package com.luoxiaohei.input;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import com.luoxiaohei.abilities.AbilityType;
import com.luoxiaohei.config.MessagesManager;
import com.luoxiaohei.data.PlayerData;
import com.luoxiaohei.data.PlayerDataManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.Arrays;
import java.util.List;

/**
 * 按键绑定管理器 v2.1
 *
 * Bukkit无法直接检测Alt/Ctrl/M等键, 使用以下替代:
 * - SWAP_HANDS: F键 (交换主副手)
 * - SHIFT_SWAP: Shift+F
 * - DROP: Q键 (丢弃物品)
 * - SHIFT_DROP: Shift+Q
 * - SNEAK: Shift键 (单按, 仅按下时触发)
 *
 * 默认: SNEAK(Shift)=切换技能, SWAP_HANDS(F)=开关技能
 * 玩家可用 /ability bind 自定义, 或 /ability cycle 手动切换
 */
public class KeybindManager implements Listener {

    private final LuoXiaoHeiPlugin plugin;
    private final PlayerDataManager dm;
    private final MessagesManager msg;

    public static final List<String> VALID_BINDS = Arrays.asList(
            "SWAP_HANDS", "SHIFT_SWAP", "DROP", "SHIFT_DROP", "SNEAK");

    // 防止sneak快速重复触发 (冷却500ms)
    private final java.util.Map<java.util.UUID, Long> sneakLastTrigger = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long SNEAK_COOLDOWN_MS = 500;

    public KeybindManager(LuoXiaoHeiPlugin plugin) {
        this.plugin = plugin;
        this.dm = plugin.getPlayerDataManager();
        this.msg = plugin.getMessagesManager();
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        PlayerData d = dm.getData(p);
        if (d.getAbilityType() == AbilityType.NONE) return;

        boolean sneaking = p.isSneaking();
        String trigger = sneaking ? "SHIFT_SWAP" : "SWAP_HANDS";

        if (trigger.equals(d.getBindCycle())) {
            e.setCancelled(true);
            cycleSkill(p);
        } else if (trigger.equals(d.getBindToggle())) {
            e.setCancelled(true);
            toggleSkills(p);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        PlayerData d = dm.getData(p);
        if (d.getAbilityType() == AbilityType.NONE) return;

        boolean sneaking = p.isSneaking();
        String trigger = sneaking ? "SHIFT_DROP" : "DROP";

        if (trigger.equals(d.getBindCycle())) {
            e.setCancelled(true);
            cycleSkill(p);
        } else if (trigger.equals(d.getBindToggle())) {
            e.setCancelled(true);
            toggleSkills(p);
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        PlayerData d = dm.getData(p);
        if (d.getAbilityType() == AbilityType.NONE) return;
        // 只在按下shift时触发 (不是松开)
        if (!e.isSneaking()) return;

        String trigger = "SNEAK";
        boolean matched = false;
        if (trigger.equals(d.getBindCycle())) {
            matched = true;
            // 冷却检查
            Long last = sneakLastTrigger.get(p.getUniqueId());
            if (last != null && System.currentTimeMillis() - last < SNEAK_COOLDOWN_MS) return;
            sneakLastTrigger.put(p.getUniqueId(), System.currentTimeMillis());
            cycleSkill(p);
        } else if (trigger.equals(d.getBindToggle())) {
            matched = true;
            Long last = sneakLastTrigger.get(p.getUniqueId());
            if (last != null && System.currentTimeMillis() - last < SNEAK_COOLDOWN_MS) return;
            sneakLastTrigger.put(p.getUniqueId(), System.currentTimeMillis());
            toggleSkills(p);
        }
        // 不取消sneak事件, 让玩家正常蹲下
    }

    /**
     * 切换到下一个技能 (可从外部调用)
     */
    public void cycleSkill(Player p) {
        PlayerData d = dm.getData(p);
        int max = getSkillCount(d.getAbilityType());
        if (max == 0) return;
        d.setCurrentSkillIndex((d.getCurrentSkillIndex() + 1) % max);
        String name = getSkillName(d.getAbilityType(), d.getCurrentSkillIndex());
        p.sendMessage(msg.getPrefixed("skill-cycle", "skill", name));
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    /**
     * 开关技能 (可从外部调用)
     */
    public void toggleSkills(Player p) {
        PlayerData d = dm.getData(p);
        boolean newState = !d.isEnabled();
        d.setEnabled(newState);
        if (newState) {
            p.sendMessage(msg.getPrefixed("ability-toggle-on"));
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1f);
        } else {
            p.sendMessage(msg.getPrefixed("ability-toggle-off"));
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1f);
        }
    }

    // === 技能名称表 ===
    private static final java.util.Map<AbilityType, String[]> SKILL_NAMES = new java.util.EnumMap<>(AbilityType.class);
    static {
        SKILL_NAMES.put(AbilityType.METAL, new String[]{"金属操控", "金属风暴", "金刚护盾", "贯金属枪"});
        SKILL_NAMES.put(AbilityType.SPACE, new String[]{"空间吞噬", "空间瞬移", "空间领域", "虚空斩"});
        SKILL_NAMES.put(AbilityType.FIRE, new String[]{"火球术", "烈焰风暴", "火焰护盾"});
        SKILL_NAMES.put(AbilityType.THUNDER, new String[]{"雷击", "雷神之怒", "雷电冲刺"});
        SKILL_NAMES.put(AbilityType.WOOD, new String[]{"治愈之森", "荆棘缠绕", "生命绽放"});
    }

    public static int getSkillCount(AbilityType type) {
        String[] n = SKILL_NAMES.get(type);
        return n == null ? 0 : n.length;
    }

    public static String getSkillName(AbilityType type, int idx) {
        String[] n = SKILL_NAMES.get(type);
        if (n == null || idx < 0 || idx >= n.length) return "?";
        return n[idx];
    }

    // 技能key表 (用于配置文件查找)
    private static final java.util.Map<AbilityType, String[]> SKILL_KEYS = new java.util.EnumMap<>(AbilityType.class);
    static {
        SKILL_KEYS.put(AbilityType.METAL, new String[]{"metal-control", "metal-storm", "metal-shield", "metal-spear"});
        SKILL_KEYS.put(AbilityType.SPACE, new String[]{"space-devour", "space-teleport", "space-domain", "space-slash"});
        SKILL_KEYS.put(AbilityType.FIRE, new String[]{"fireball", "fire-storm", "fire-shield"});
        SKILL_KEYS.put(AbilityType.THUNDER, new String[]{"lightning-bolt", "thunder-fury", "thunder-dash"});
        SKILL_KEYS.put(AbilityType.WOOD, new String[]{"heal-forest", "thorn-bind", "life-bloom"});
    }

    public static String getSkillKey(AbilityType type, int idx) {
        String[] k = SKILL_KEYS.get(type);
        if (k == null || idx < 0 || idx >= k.length) return "";
        return type.getConfigKey() + "." + k[idx];
    }

    public static String getSkillShortKey(AbilityType type, int idx) {
        String[] k = SKILL_KEYS.get(type);
        if (k == null || idx < 0 || idx >= k.length) return "";
        return k[idx];
    }
}
