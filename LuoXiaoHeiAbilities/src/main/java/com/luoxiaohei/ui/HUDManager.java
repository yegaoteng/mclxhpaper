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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HUD管理器 - 右侧计分板显示 (v2.1)
 * - 去掉标题文字 (用空格代替, 兼容小地图)
 * - 顶部留空行让HUD下移 (避开右上角小地图区域)
 * - 显示当前技能开关状态
 * - 兼容其他插件的计分板 (复用已有scoreboard)
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
    private static final java.util.Map<AbilityType, String[]> SKILL_NAMES = new java.util.EnumMap<>(AbilityType.class);
    static {
        SKILL_NAMES.put(AbilityType.METAL, new String[]{"金属操控", "金属风暴", "金刚护盾", "贯金属枪"});
        SKILL_NAMES.put(AbilityType.SPACE, new String[]{"空间吞噬", "空间瞬移", "空间领域", "虚空斩"});
        SKILL_NAMES.put(AbilityType.FIRE, new String[]{"火球术", "烈焰风暴", "火焰护盾"});
        SKILL_NAMES.put(AbilityType.THUNDER, new String[]{"雷击", "雷神之怒", "雷电冲刺"});
        SKILL_NAMES.put(AbilityType.WOOD, new String[]{"治愈之森", "荆棘缠绕", "生命绽放"});
    }

    // 技能key表
    private static final java.util.Map<AbilityType, String[]> SKILL_KEYS = new java.util.EnumMap<>(AbilityType.class);
    static {
        SKILL_KEYS.put(AbilityType.METAL, new String[]{"metal-control", "metal-storm", "metal-shield", "metal-spear"});
        SKILL_KEYS.put(AbilityType.SPACE, new String[]{"space-devour", "space-teleport", "space-domain", "space-slash"});
        SKILL_KEYS.put(AbilityType.FIRE, new String[]{"fireball", "fire-storm", "fire-shield"});
        SKILL_KEYS.put(AbilityType.THUNDER, new String[]{"lightning-bolt", "thunder-fury", "thunder-dash"});
        SKILL_KEYS.put(AbilityType.WOOD, new String[]{"heal-forest", "thorn-bind", "life-bloom"});
    }

    // 顶部留3行空白让HUD下移 (避开小地图), 最多15行
    private static final int TOP_PADDING = 3;
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
            // 兼容小地图: 复用玩家已有scoreboard, 不创建新的
            sb = p.getScoreboard();
            if (sb == null || sb == Bukkit.getScoreboardManager().getMainScoreboard()) {
                sb = Bukkit.getScoreboardManager().getNewScoreboard();
            }
            boards.put(p.getUniqueId(), sb);

            // 先清除可能存在的旧objective
            Objective old = sb.getObjective("lxh_hud");
            if (old != null) old.unregister();

            Objective obj = sb.registerNewObjective("lxh_hud", "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            // 去掉"罗小黑能力"标题, 用空格代替
            obj.setDisplayName("§r");
            objectives.put(p.getUniqueId(), obj);

            Team[] ts = new Team[MAX_LINES];
            for (int i = 0; i < MAX_LINES; i++) {
                String teamName = "lxh_l" + i;
                Team oldTeam = sb.getTeam(teamName);
                if (oldTeam != null) oldTeam.unregister();
                Team t = sb.registerNewTeam(teamName);
                // 使用唯一entry避免与其他插件冲突
                String entry = ChatColor.COLOR_CHAR + "" + (char)('a' + (i / 26)) + (char)('a' + (i % 26)) + ChatColor.RESET;
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
        objectives.remove(p.getUniqueId());
        teams.remove(p.getUniqueId());
        // 重置为主计分板 (不强制清除, 让其他插件恢复)
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void update(Player p) {
        Objective obj = objectives.get(p.getUniqueId());
        Team[] ts = teams.get(p.getUniqueId());
        if (obj == null || ts == null) { show(p); return; }

        PlayerData d = dm.getData(p);
        int line = 0;

        // === 顶部留白 (让HUD下移, 避开小地图) ===
        for (int i = 0; i < TOP_PADDING; i++) {
            setLine(ts, line++, "§r " + ChatColor.COLOR_CHAR + (char)('a' + i));
        }

        // === 开关状态 ===
        String toggleStr = d.isEnabled() ? "§a● 开" : "§c○ 关";
        if (d.getAbilityType() == AbilityType.NONE) {
            setLine(ts, line++, "§7技能: §c未觉醒");
        } else {
            setLine(ts, line++, "§7技能: " + toggleStr);
        }

        // === 上半部分 ===
        if (d.getAbilityType() == AbilityType.NONE) {
            setLine(ts, line++, "§7阶数: §f" + cm.getLevelChinese(d.getCultivationLevel()));
            setLine(ts, line++, "§6经验: §f" + d.getCultivationXp() + "/" + cm.getXpToNext(d.getCultivationLevel()));
            setLine(ts, line++, "§b灵力: §f" + d.getSpiritual() + "/" + d.getMaxSpiritual());
        } else if (!d.isEnabled()) {
            setLine(ts, line++, "§7能力: " + d.getAbilityType().getColor() + d.getAbilityType().getChinese());
            setLine(ts, line++, "§7阶数: §f" + cm.getLevelChinese(d.getCultivationLevel()));
            setLine(ts, line++, "§6经验: §f" + d.getCultivationXp() + "/" + cm.getXpToNext(d.getCultivationLevel()));
            setLine(ts, line++, "§b灵力: §f" + d.getSpiritual() + "/" + d.getMaxSpiritual());
        } else {
            // 当前技能信息
            String[] names = SKILL_NAMES.get(d.getAbilityType());
            String[] keys = SKILL_KEYS.get(d.getAbilityType());
            int idx = Math.min(d.getCurrentSkillIndex(), names.length - 1);
            String skillName = names[idx];
            String skillKey = d.getAbilityType().getConfigKey() + "." + keys[idx];

            setLine(ts, line++, "§b技能: §f" + skillName);
            int cost = plugin.getConfigManager().getActionInt(skillKey + ".cost", 0);
            setLine(ts, line++, "§b灵力消耗: §f" + cost);
            // 冷却
            long cdRemain = dm.getCooldownRemain(p, keys[idx]);
            if (cdRemain > 0) {
                setLine(ts, line++, "§a冷却: §f" + (cdRemain / 1000 + 1) + "s");
            } else {
                setLine(ts, line++, "§a冷却: §f就绪");
            }
            // 经验
            setLine(ts, line++, "§6经验: §f" + d.getCultivationXp() + "/" + cm.getXpToNext(d.getCultivationLevel()));
            // 阶数+灵力
            setLine(ts, line++, "§7阶数: §f" + cm.getLevelChinese(d.getCultivationLevel()));
            setLine(ts, line++, "§b灵力: §f" + d.getSpiritual() + "/" + d.getMaxSpiritual());
        }

        // 空行
        setLine(ts, line++, "§r");

        // 作者
        setLine(ts, line++, msg.get("hud-author"));

        // 空行
        if (line < MAX_LINES) setLine(ts, line++, "§r  ");

        // === 技能冷却列表 ===
        if (line < MAX_LINES) setLine(ts, line++, msg.get("hud-skills-header"));
        if (d.getAbilityType() != AbilityType.NONE) {
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
        // 截取到限制长度
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
