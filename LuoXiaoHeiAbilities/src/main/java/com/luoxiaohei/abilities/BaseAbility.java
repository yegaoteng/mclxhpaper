package com.luoxiaohei.abilities;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import com.luoxiaohei.config.ConfigManager;
import com.luoxiaohei.config.MessagesManager;
import com.luoxiaohei.data.PlayerDataManager;
import com.luoxiaohei.input.KeybindManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * 能力基类 - 提供公共方法
 * v2.0: 技能伤害标记(修炼经验), 右键技能模型
 */
public abstract class BaseAbility {

    protected final LuoXiaoHeiPlugin plugin;
    protected final ConfigManager cfg;
    protected final PlayerDataManager dm;
    protected final AbilityManager am;
    protected final MessagesManager msg;
    protected final String prefix;

    public BaseAbility(LuoXiaoHeiPlugin plugin, String prefix) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
        this.dm = plugin.getPlayerDataManager();
        this.am = plugin.getAbilityManager();
        this.msg = plugin.getMessagesManager();
        this.prefix = prefix;
    }

    // ========== 配置快捷 ==========
    protected int aCost(String skill) { return cfg.getActionInt(prefix + "." + skill + ".cost", 0); }
    protected int aCd(String skill) { return cfg.getActionInt(prefix + "." + skill + ".cooldown", 0); }
    protected double aDmg(String skill) { return cfg.getActionDouble(prefix + "." + skill + ".damage", 0); }
    protected boolean aEnabled(String skill) { return cfg.getActionBool(prefix + "." + skill + ".enabled", true); }

    // ========== 技能前置检查 ==========
    protected String preCheck(Player p, String skill) {
        return am.preCheck(p, skill, aCost(skill), aCd(skill));
    }

    protected void applyCost(Player p, String skill) {
        am.applyCost(p, skill, aCost(skill), aCd(skill));
    }

    // ========== 目标获取 ==========
    protected List<LivingEntity> getLineOfSightTargets(Player p, double maxRange, double width) {
        List<LivingEntity> result = new ArrayList<>();
        Location start = p.getEyeLocation();
        Vector dir = start.getDirection().normalize();
        for (int i = 0; i < maxRange; i++) {
            Location cur = start.clone().add(dir.clone().multiply(i));
            if (cur.getBlock().getType().isSolid() && i > 1) break;
            for (org.bukkit.entity.Entity e : cur.getWorld().getNearbyEntities(cur, width, width, width)) {
                if (e instanceof LivingEntity le && e != p && !result.contains(le)) result.add(le);
            }
        }
        return result;
    }

    protected LivingEntity getTarget(Player p, double range) {
        List<LivingEntity> list = getLineOfSightTargets(p, range, 1.5);
        return list.isEmpty() ? null : list.get(0);
    }

    // ========== 伤害 (标记为技能伤害, 用于修炼经验) ==========
    protected void damage(LivingEntity target, Player source, double amount, boolean ignoreArmor) {
        if (target == null || target.isDead()) return;
        // 先标记为技能伤害 (5秒有效), 再造成伤害
        // 这样即使伤害致死, EntityDeathEvent触发时metadata已存在
        target.setMetadata("skill_damage",
            new FixedMetadataValue(plugin, source.getUniqueId() + ":" + System.currentTimeMillis()));
        if (ignoreArmor) {
            double cur = target.getHealth();
            target.setHealth(Math.max(0, cur - amount));
            target.damage(0.001, source);
        } else {
            target.damage(amount, source);
        }
    }

    // ========== 粒子 ==========
    protected void particleLine(Location from, Location to, Particle particle, int countPerBlock) {
        double dist = from.distance(to);
        if (dist < 0.01) return;
        Vector step = to.clone().subtract(from).toVector().normalize().multiply(1.0 / countPerBlock);
        Location cur = from.clone();
        for (double d = 0; d < dist; d += 1.0 / countPerBlock) {
            cur.getWorld().spawnParticle(particle, cur, 1, 0, 0, 0, 0);
            cur.add(step);
        }
    }

    protected void playSound(Player p, Sound s) { p.playSound(p.getLocation(), s, 1f, 1f); }

    // ========== 获取当前技能key ==========
    protected String getCurrentSkillKey(Player p) {
        var data = dm.getData(p);
        return KeybindManager.getSkillKey(data.getAbilityType(), data.getCurrentSkillIndex());
    }

    protected String getCurrentSkillShortKey(Player p) {
        var data = dm.getData(p);
        return KeybindManager.getSkillShortKey(data.getAbilityType(), data.getCurrentSkillIndex());
    }
}
