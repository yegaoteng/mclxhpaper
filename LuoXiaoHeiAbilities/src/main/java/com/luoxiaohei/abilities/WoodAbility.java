package com.luoxiaohei.abilities;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import com.luoxiaohei.data.PlayerData;
import com.luoxiaohei.util.Compat;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * 木系能力 v2.0
 * - 右键当前技能触发 (左键=正常攻击/破坏)
 * - 技能0: 治愈之森 (heal-forest)
 * - 技能1: 荆棘缠绕 (thorn-bind)
 * - 技能2: 生命绽放 (life-bloom)
 */
public class WoodAbility extends BaseAbility implements Listener {

    public WoodAbility(LuoXiaoHeiPlugin plugin) {
        super(plugin, "wood");
    }

    // ===== 技能0: 治愈之森 =====
    public void healForest(Player p) {
        if (!aEnabled("heal-forest")) return;
        String err = preCheck(p, "heal-forest");
        if (err != null) { p.sendMessage(err); return; }
        int radius = cfg.getActionInt("wood.heal-forest.radius", 8);
        int duration = cfg.getActionInt("wood.heal-forest.duration", 5);
        int amp = cfg.getActionInt("wood.heal-forest.regen-amp", 1);
        applyCost(p, "heal-forest");
        p.sendMessage("§a[治愈之森] §f生机盎然!");
        playSound(p, Sound.BLOCK_GRASS_BREAK);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t++ >= duration) { cancel(); return; }
                Location loc = p.getLocation();
                for (int i = 0; i < 20; i++) {
                    double a = Math.random() * Math.PI * 2;
                    double r = Math.random() * radius;
                    Location pp = loc.clone().add(Math.cos(a) * r, Math.random() * 2, Math.sin(a) * r);
                    loc.getWorld().spawnParticle(Compat.PARTICLE_HAPPY_VILLAGER, pp, 1, 0, 0, 0, 0);
                }
                // 自身 + 附近友军恢复
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, amp, false, false, false));
                for (Entity e : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
                    if (e instanceof Player ally && !ally.getUniqueId().equals(p.getUniqueId())) {
                        ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, amp, false, false, false));
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    // ===== 技能1: 荆棘缠绕 =====
    public void thornBind(Player p) {
        if (!aEnabled("thorn-bind")) return;
        String err = preCheck(p, "thorn-bind");
        if (err != null) { p.sendMessage(err); return; }
        double dmg = aDmg("thorn-bind");
        int radius = cfg.getActionInt("wood.thorn-bind.radius", 8);
        int slowAmp = cfg.getActionInt("wood.thorn-bind.slow-amp", 1);
        int duration = cfg.getActionInt("wood.thorn-bind.duration", 3);
        applyCost(p, "thorn-bind");
        p.sendMessage("§a[荆棘缠绕] §f缠绕!");
        playSound(p, Sound.BLOCK_VINE_STEP);
        Set<Integer> hit = new HashSet<>();
        Location loc = p.getLocation();
        for (Entity e : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
            if (e instanceof LivingEntity le && e != p && !(e instanceof Player) && !hit.contains(e.getEntityId())) {
                hit.add(e.getEntityId());
                damage(le, p, dmg, false);
                le.addPotionEffect(new PotionEffect(Compat.EFFECT_SLOWNESS, duration * 20, slowAmp, false, false, false));
                le.getWorld().spawnParticle(Particle.BLOCK, le.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.05,
                        Material.OAK_LEAVES.createBlockData());
            }
        }
        // 缠绕特效
        for (int i = 0; i < 24; i++) {
            double a = (Math.PI * 2 / 24) * i;
            Location pp = loc.clone().add(Math.cos(a) * radius, 0.5, Math.sin(a) * radius);
            loc.getWorld().spawnParticle(Particle.BLOCK, pp, 2, 0, 0, 0, 0, Material.OAK_LEAVES.createBlockData());
        }
    }

    // ===== 技能2: 生命绽放 =====
    public void lifeBloom(Player p) {
        if (!aEnabled("life-bloom")) return;
        String err = preCheck(p, "life-bloom");
        if (err != null) { p.sendMessage(err); return; }
        double dmg = aDmg("life-bloom");
        int radius = cfg.getActionInt("wood.life-bloom.radius", 6);
        int duration = cfg.getActionInt("wood.life-bloom.duration", 6);
        int regenAmp = cfg.getActionInt("wood.life-bloom.regen-amp", 2);
        int jumpAmp = cfg.getActionInt("wood.life-bloom.jump-amp", 1);
        applyCost(p, "life-bloom");
        p.sendMessage("§a[生命绽放] §f绽放!");
        playSound(p, Sound.BLOCK_ENCHANTMENT_TABLE_USE);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t++ >= duration) { cancel(); return; }
                Location loc = p.getLocation();
                // 绽放粒子
                for (int i = 0; i < 24; i++) {
                    double a = (Math.PI * 2 / 24) * i + t * 0.15;
                    double r = radius * (0.5 + 0.5 * Math.sin(t * 0.3));
                    Location pp = loc.clone().add(Math.cos(a) * r, Math.sin(t * 0.2) * 0.5, Math.sin(a) * r);
                    loc.getWorld().spawnParticle(Compat.PARTICLE_HAPPY_VILLAGER, pp, 1, 0, 0, 0, 0.02);
                }
                // 自身强化: 恢复 + 跳跃提升
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, regenAmp, false, false, false));
                p.addPotionEffect(new PotionEffect(Compat.EFFECT_JUMP_BOOST, 40, jumpAmp, false, false, false));
                // 附近敌人持续伤害
                for (Entity e : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
                    if (e instanceof LivingEntity le && e != p && !(e instanceof Player)) {
                        damage(le, p, dmg, false);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    // ===== 事件: 右键触发技能 (左键=正常攻击) =====
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        PlayerData d = dm.getData(p);
        if (d.getAbilityType() != AbilityType.WOOD) return;
        if (!d.isEnabled()) return; // 技能关闭时不拦截
        // 只拦截右键 (左键正常)
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // 手持灵瓜时不触发技能 (灵瓜监听器已cancel, 这里跳过)
        if (plugin.getSpiritItemManager().isSpiritMelon(p.getInventory().getItemInMainHand())) return;
        e.setCancelled(true);
        switch (d.getCurrentSkillIndex()) {
            case 0: healForest(p); break;
            case 1: thornBind(p); break;
            case 2: lifeBloom(p); break;
        }
    }
}
