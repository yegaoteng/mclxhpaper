package com.luoxiaohei.ui;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import com.luoxiaohei.abilities.AbilityType;
import com.luoxiaohei.abilities.AbilityManager;
import com.luoxiaohei.cultivation.CultivationManager;
import com.luoxiaohei.data.PlayerData;
import com.luoxiaohei.data.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scoreboard.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HUD管理器 - 右侧计分板显示 (v2.2)
 * - 不可见的entry (使用颜色代码§0-§e, 无字母)
 * - 紧凑布局确保显示全部技能
 * - 顶部1行留白避开小地图
 * - 兼容其他插件的计分板
 */
public class HUDManager {

    private final LuoXiaoHeiPlugin plugin;
    private final PlayerDataManager dm;
    private final AbilityManager am;
    private final CultivationManager cm;
    private final com.luoxiaohei.config.MessagesManager msg;
    private final int updateInterval;

    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    private final Map<UUID, Objective> objectives = new ConcurrentHashMap<>();
    private final Map<UUID, Team[]> teams = new ConcurrentHashMap<>();
    private BukkitTask task;

    // 技能名称表
    private static final Map<AbilityType, String[]> SKILL_NAMES = new java.util.EnumMap<>(AbilityType.class);
    static {
        SKILL_NAMES.put(AbilityType.METAL, new String[]{"金属操控", "金属风暴", "金刚护盾", "贯金属枪"});
        SKILL_NAMES.put(AbilityType.SPACE, new String[]{"空间吞噬", "空间瞬移", "空间领域", "虚空斩"});
        SKILL_NAMES.put(AbilityType.FIRE, new String[]{"火球术", "烈焰风暴", "火焰护盾"});
        SKILL_NAMES.put(AbilityType.THUNDER, new String[]{"雷击", "雷神之怒", "雷电冲刺"});
        SKILL_NAMES.put(AbilityType.WOOD, new String[]{"治愈之森", "荆棘缠绕", "生命绽放"});
    }

    // 技能key表
    private static final Map<AbilityType, String[]> SKILL_KEYS = new java.util.EnumMap<>(AbilityType.class);
    static {
        SKILL_KEYS.put(AbilityType.METAL, new String[]{"metal-control", "metal-storm", "metal-shield", "metal-spear"});
        SKILL_KEYS.put(AbilityType.SPACE, new String[]{"space-devour", "space-teleport", "space-domain", "space-slash"});
        SKILL_KEYS.put(AbilityType.FIRE, new String[]{"fireball", "fire-storm", "fire-shield"});
        SKILL_KEYS.put(AbilityType.THUNDER, new String[]{"lightning-bolt", "thunder-fury", "thunder-dash"});
        SKILL_KEYS.put(AbilityType.WOOD, new String[]{"heal-forest", "thorn-bind", "life-bloom"});
    }

    private static final int MAX_LINES = 15;

    public HUDManager(LuoXiaoHeiPlugin plugin) {
        this.plugin = plugin;
        this.dm = plugin.getPlayerDataManager();
        this.am = plugin.getAbilityManager();
        this.cm = plugin.getCultivationManager();
        this.msg = plugin.getMessagesManager();
        this.updateInterval = plugin.getConfig().getInt("performance.hud-update-interval", 10);
    }

    public void startScheduler() {
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try { update(p); } catch (Exception ignored) {}
            }
        }, 20L, updateInterval);
    }

    public void show(Player p) {
        Scoreboard sb = boards.get(p.getUniqueId());
        if (sb == null) {
            sb = Bukkit.getScoreboardManager().getNewScoreboard();
            boards.put(p.getUniqueId(), sb);

            Objective old = sb.getObjective("lxh_hud");
            if (old != null) old.unregister();

            Objective obj = sb.registerNewObjective("lxh_hud", "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.setDisplayName("§r");
            objectives.put(p.getUniqueId(), obj);

            Team[] ts = new Team[MAX_LINES];
            for (int i = 0; i < MAX_LINES; i++) {
                String teamName = "lxh_l" + i;
                Team oldTeam = sb.getTeam(teamName);
                if (oldTeam != null) oldTeam.unregister();
                Team t = sb.registerNewTeam(teamName);
                // 不可见entry: §0§r ~ §e§r (十六进制0-e = 15个颜色代码, 无可见字母)
                String entry = String.valueOf(ChatColor.COLOR_CHAR) + Integer.toHexString(i) + ChatColor.RESET;
                t.addEntry(entry);
                obj.getScore(entry).setScore(MAX_LINES - 1 - i);
                ts[i] = t;
            }
            teams.put(p.getUniqueId(), ts);
        }
        p.setScoreboard(sb);
    }

    public void hide(Player p) {
        Scoreboard sb = boards.remove(p.getUniqueId());
        Objective obj = objectives.remove(p.getUniqueId());
        if (obj != null) { try { obj.unregister(); } catch (Exception ignored) {} }
        teams.remove(p.getUniqueId());
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void update(Player p) {
        Objective obj = objectives.get(p.getUniqueId());
        Team[] ts = teams.get(p.getUniqueId());
        if (obj == null || ts == null) { show(p); return; }

        PlayerData d = dm.getData(p);
        int line = 0;

        // 第1行: 留白 (避开小地图)
        setLine(ts, line++, "§r");

        // 第2行: 开关状态
        if (d.getAbilityType() == AbilityType.NONE) {
            setLine(ts, line++, "§7技能: §c未觉醒");
        } else {
            setLine(ts, line++, "§7技能: " + (d.isEnabled() ? "§a● 开" : "§c○ 关"));
        }

        // 技能详情 (紧凑: 消耗+冷却合并一行, 阶数+灵力合并一行)
        if (d.getAbilityType() != AbilityType.NONE && d.isEnabled()) {
            String[] names = SKILL_NAMES.get(d.getAbilityType());
            String[] keys = SKILL_KEYS.get(d.getAbilityType());
            int idx = Math.min(d.getCurrentSkillIndex(), names.length - 1);
            String skillKey = d.getAbilityType().getConfigKey() + "." + keys[idx];

            setLine(ts, line++, "§b技能: §f" + names[idx]);
            int cost = plugin.getConfigManager().getActionInt(skillKey + ".cost", 0);
            long cdRemain = dm.getCooldownRemain(p, keys[idx]);
            String cdStr = cdRemain > 0 ? (cdRemain / 1000 + 1) + "s" : "就绪";
            setLine(ts, line++, "§b消耗:§f" + cost + " §a冷却:§f" + cdStr);
        } else if (d.getAbilityType() != AbilityType.NONE) {
            setLine(ts, line++, "§7能力: " + d.getAbilityType().getColor() + d.getAbilityType().getChinese());
        }

        // 经验 + 阶数+灵力 (合并)
        setLine(ts, line++, "§6经验: §f" + d.getCultivationXp() + "/" + cm.getXpToNext(d.getCultivationLevel()));
        setLine(ts, line++, "§7阶数:§f" + cm.getLevelChinese(d.getCultivationLevel())
                + " §b灵力:§f" + d.getSpiritual() + "/" + d.getMaxSpiritual());

        // 空行 + 作者
        setLine(ts, line++, "§r");
        setLine(ts, line++, msg.get("hud-author"));

        // 技能冷却列表
        if (d.getAbilityType() != AbilityType.NONE) {
            setLine(ts, line++, msg.get("hud-skills-header"));
            String[] names = SKILL_NAMES.get(d.getAbilityType());
            String[] keys = SKILL_KEYS.get(d.getAbilityType());
            for (int i = 0; i < names.length && line < MAX_LINES; i++) {
                long cd = dm.getCooldownRemain(p, keys[i]);
                String cdStr = cd > 0 ? msg.get("hud-skill-cooldown", "seconds", String.valueOf(cd / 1000 + 1))
                                     : msg.get("hud-skill-ready");
                String prefix = (i == d.getCurrentSkillIndex() && d.isEnabled()) ? "§e▶ " : "  ";
                setLine(ts, line++, prefix + "§f" + names[i] + " " + cdStr);
            }
        }

        // 清空剩余行
        while (line < MAX_LINES) {
            setLine(ts, line++, "");
        }
    }

    private void setLine(Team[] ts, int idx, String text) {
        if (idx < 0 || idx >= MAX_LINES) return;
        Team t = ts[idx];
        if (t == null) return;
        if (text.length() > 64) text = text.substring(0, 64);
        t.setPrefix(text);
        t.setSuffix("");
    }

    public void cleanup() {
        if (task != null) task.cancel();
        boards.clear();
        objectives.clear();
        teams.clear();
    }
}
