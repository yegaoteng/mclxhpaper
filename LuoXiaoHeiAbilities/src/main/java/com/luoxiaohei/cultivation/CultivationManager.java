package com.luoxiaohei.cultivation;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import com.luoxiaohei.config.MessagesManager;
import com.luoxiaohei.data.PlayerData;
import com.luoxiaohei.data.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * 修炼系统管理器 - 经验/升阶/属性
 * 阶数独立于能力类型, 切换能力不影响阶数
 */
public class CultivationManager {

    private final LuoXiaoHeiPlugin plugin;
    private final PlayerDataManager dm;
    private final MessagesManager msg;

    public CultivationManager(LuoXiaoHeiPlugin plugin) {
        this.plugin = plugin;
        this.dm = plugin.getPlayerDataManager();
        this.msg = plugin.getMessagesManager();
    }

    /**
     * 击杀获取经验
     */
    public void grantKillXp(Player killer) {
        if (killer == null) return;
        PlayerData d = dm.getData(killer);
        if (d.getAbilityType() == com.luoxiaohei.abilities.AbilityType.NONE) return;
        int xp = plugin.getConfig().getInt("cultivation.xp-per-kill", 20);
        d.setCultivationXp(d.getCultivationXp() + xp);
        int need = getXpToNext(d.getCultivationLevel());
        if (need > 0) {
            killer.sendMessage(msg.getPrefixed("cultivation-xp-gain", "xp", String.valueOf(xp),
                    "need", String.valueOf(need)));
        }
        checkLevelUp(killer);
    }

    /**
     * 检查升阶 (需灵力满 + 经验足)
     */
    public void checkLevelUp(Player p) {
        PlayerData d = dm.getData(p);
        int level = d.getCultivationLevel();
        if (level >= 5) return;
        int need = getXpToNext(level);
        if (need <= 0) return;
        if (d.getCultivationXp() < need) return;
        // 灵力需满
        if (d.getSpiritual() < d.getMaxSpiritual()) {
            p.sendMessage(msg.getPrefixed("cultivation-need-full-spirit",
                    "spiritual", String.valueOf(d.getSpiritual()),
                    "max", String.valueOf(d.getMaxSpiritual())));
            return;
        }
        // 升阶
        int newLevel = level + 1;
        d.setCultivationLevel(newLevel);
        d.setCultivationXp(0);
        applyLevelStats(p, newLevel);
        p.sendMessage(msg.getPrefixed("cultivation-levelup",
                "level", String.valueOf(newLevel),
                "max_spiritual", String.valueOf(d.getMaxSpiritual())));
        // 特效
        p.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, p.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1);
    }

    /**
     * 应用阶数属性 (灵力上限/恢复/额外血量)
     */
    public void applyLevelStats(Player p, int level) {
        ConfigurationSection lvl = plugin.getConfig().getConfigurationSection("cultivation.levels." + level);
        if (lvl == null) return;
        PlayerData d = dm.getData(p);
        d.setMaxSpiritual(lvl.getInt("max-spiritual", 1000));
        d.setSpiritual(d.getMaxSpiritual());

        // 额外血量 (仅五阶)
        int bonusHealth = lvl.getInt("bonus-health", 0);
        try {
            var attr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (attr != null) {
                // 先移除之前的修饰符 (拷贝到新列表避免 ConcurrentModificationException)
                for (org.bukkit.attribute.AttributeModifier m : new java.util.ArrayList<>(attr.getModifiers())) {
                    if ("cultivation_bonus".equals(m.getName())) attr.removeModifier(m);
                }
                if (bonusHealth > 0) {
                    attr.addModifier(new org.bukkit.attribute.AttributeModifier(
                        "cultivation_bonus", bonusHealth,
                        org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER));
                }
                // 血量不超过新上限
                if (p.getHealth() > attr.getValue()) p.setHealth(attr.getValue());
            }
        } catch (Exception ignored) {}
    }

    public int getXpToNext(int level) {
        ConfigurationSection lvl = plugin.getConfig().getConfigurationSection("cultivation.levels." + level);
        if (lvl == null) return 0;
        return lvl.getInt("xp-to-next", 0);
    }

    public int getRegenPerSecond(int level) {
        ConfigurationSection lvl = plugin.getConfig().getConfigurationSection("cultivation.levels." + level);
        if (lvl == null) return 2;
        return lvl.getInt("regen-per-second", 2);
    }

    public int getMaxSpiritual(int level) {
        ConfigurationSection lvl = plugin.getConfig().getConfigurationSection("cultivation.levels." + level);
        if (lvl == null) return 1000;
        return lvl.getInt("max-spiritual", 1000);
    }

    public String getLevelChinese(int level) {
        switch (level) {
            case 1: return "一阶";
            case 2: return "二阶";
            case 3: return "三阶";
            case 4: return "四阶";
            case 5: return "五阶";
            default: return "未知";
        }
    }
}
