package com.luoxiaohei.abilities;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import com.luoxiaohei.data.PlayerData;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 火系能力 v2.0
 * - 右键当前技能触发 (左键=正常攻击/破坏)
 * - 技能0: 火球术 (fireball)
 * - 技能1: 烈焰风暴 (fire-storm)
 * - 技能2: 火焰护盾 (fire-shield)
 */
public class FireAbility extends BaseAbility implements Listener {

    private final Map<UUID, Long> shieldPlayers = new ConcurrentHashMap<>();

    public FireAbility(LuoXiaoHeiPlugin plugin) {
        super(plugin, "fire");
    }

    // ===== 技能0: 火球术 =====
    public void fireball(Player p) {
        if (!aEnabled("fireball")) return;
        String err = preCheck(p, "fireball");
        if (err != null) { p.sendMessage(err); return; }
        double dmg = aDmg("fireball");
        int range = cfg.getActionInt("fire.fireball.range", 30);
        double speed = cfg.getActionDouble("fire.fireball.speed", 1.5);
        double radius = cfg.getActionDouble("fire.fireball.radius", 2.0);
        applyCost(p, "fireball");
        p.sendMessage("§6[火球术] §f发射!");
        playSound(p, Sound.ENTITY_BLAZE_SHOOT);

        final Location cur = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.5));
        final Vector dir = p.getEyeLocation().getDirection().normalize().multiply(speed);
        new BukkitRunnable() {
            int life = 0;
            final Set<Integer> hit = new HashSet<>();
            @Override public void run() {
                if (life++ > range || !p.isOnline()) { cancel(); return; }
                cur.add(dir);
                // 击中实体方块 → 爆裂
                if (cur.getBlock().getType().isSolid()) {
                    cur.getWorld().spawnParticle(Particle.FLAME, cur, 30, 0.5, 0.5, 0.5, 0.1);
                    cur.getWorld().spawnParticle(Particle.LAVA, cur, 10, 0.3, 0.3, 0.3, 0.05);
                    playSound(p, Sound.ENTITY_GENERIC_EXPLODE);
                    for (Entity e : cur.getWorld().getNearbyEntities(cur, radius, radius, radius)) {
                        if (e instanceof LivingEntity le && e != p) {
                            damage(le, p, dmg, false);
                            le.setFireTicks(60);
                        }
                    }
                    cancel(); return;
                }
                // 飞行轨迹 + 碰撞伤害
                p.getWorld().spawnParticle(Particle.FLAME, cur, 3, 0.1, 0.1, 0.1, 0.02);
                p.getWorld().spawnParticle(Particle.LAVA, cur, 1, 0.2, 0.2, 0.2, 0);
                for (Entity e : cur.getWorld().getNearbyEntities(cur, radius, radius, radius)) {
                    if (e instanceof LivingEntity le && e != p && !hit.contains(e.getEntityId())) {
                        hit.add(e.getEntityId());
                        damage(le, p, dmg, false);
                        le.setFireTicks(60);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    // ===== 技能1: 烈焰风暴 =====
    public void firestorm(Player p) {
        if (!aEnabled("fire-storm")) return;
        String err = preCheck(p, "fire-storm");
        if (err != null) { p.sendMessage(err); return; }
        double dmg = aDmg("fire-storm");
        int radius = cfg.getActionInt("fire.fire-storm.radius", 8);
        int duration = cfg.getActionInt("fire.fire-storm.duration", 5);
        applyCost(p, "fire-storm");
        p.sendMessage("§6[烈焰风暴] §f烈焰肆虐!");
        playSound(p, Sound.ENTITY_BLAZE_SHOOT);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t++ >= duration) { cancel(); return; }
                Location loc = p.getLocation();
                for (int i = 0; i < 30; i++) {
                    double a = Math.random() * Math.PI * 2;
                    double r = Math.random() * radius;
                    Location pp = loc.clone().add(Math.cos(a) * r, Math.random() * 2, Math.sin(a) * r);
                    loc.getWorld().spawnParticle(Particle.FLAME, pp, 1, 0, 0, 0, 0.05);
                }
                for (Entity e : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
                    if (e instanceof LivingEntity le && e != p) {
                        damage(le, p, dmg, false);
                        le.setFireTicks(80);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    // ===== 技能2: 火焰护盾 =====
    public void fireshield(Player p) {
        if (!aEnabled("fire-shield")) return;
        String err = preCheck(p, "fire-shield");
        if (err != null) { p.sendMessage(err); return; }
        int duration = cfg.getActionInt("fire.fire-shield.duration", 15);
        applyCost(p, "fire-shield");
        shieldPlayers.put(p.getUniqueId(), System.currentTimeMillis() + duration * 1000L);
        p.sendMessage("§6[火焰护盾] §f护体! " + duration + "s");
        playSound(p, Sound.ITEM_SHIELD_BLOCK);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                long end = shieldPlayers.getOrDefault(p.getUniqueId(), 0L);
                if (System.currentTimeMillis() > end || !p.isOnline()) { cancel(); return; }
                Location loc = p.getLocation();
                for (int i = 0; i < 12; i++) {
                    double a = (Math.PI * 2 / 12) * i + t * 0.1;
                    Location pp = loc.clone().add(Math.cos(a) * 1.3, 1.0 + Math.sin(t * 0.2) * 0.2, Math.sin(a) * 1.3);
                    loc.getWorld().spawnParticle(Particle.FLAME, pp, 1, 0, 0, 0, 0.01);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0, 5);
    }

    // 防止对空气+对物体同时触发两次 (去抖)
    private final Map<UUID, Long> interactDebounce = new ConcurrentHashMap<>();
    private static final long INTERACT_DEBOUNCE_MS = 80;

    // ===== 事件: 右键触发技能 (左键=正常攻击, 可对着空气释放) =====
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        PlayerData d = dm.getData(p);
        if (d.getAbilityType() != AbilityType.FIRE) return;
        if (!d.isEnabled()) return;
        Action act = e.getAction();
        if (act != Action.RIGHT_CLICK_AIR && act != Action.RIGHT_CLICK_BLOCK) return;
        if (plugin.getSpiritItemManager().isSpiritMelon(p.getInventory().getItemInMainHand())) return;
        EquipmentSlot h = e.getHand();
        if (h != null && h != EquipmentSlot.HAND) return;
        long now = System.currentTimeMillis();
        Long last = interactDebounce.get(p.getUniqueId());
        if (last != null && now - last < INTERACT_DEBOUNCE_MS) return;
        interactDebounce.put(p.getUniqueId(), now);
        e.setCancelled(true);
        switch (d.getCurrentSkillIndex()) {
            case 0: fireball(p); break;
            case 1: firestorm(p); break;
            case 2: fireshield(p); break;
        }
    }

    // ===== 火焰护盾减伤 + 反伤 =====
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (dm.getData(p).getAbilityType() != AbilityType.FIRE) return;
        long end = shieldPlayers.getOrDefault(p.getUniqueId(), 0L);
        if (System.currentTimeMillis() < end) {
            double def = cfg.getActionDouble("fire.fire-shield.defense", 0.5);
            double reflect = cfg.getActionDouble("fire.fire-shield.reflect", 0.3);
            e.setDamage(e.getDamage() * (1 - def));
            p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.05);
            if (e.getDamager() instanceof LivingEntity att) {
                damage(att, p, e.getDamage() * reflect, false);
                att.setFireTicks(60);
            }
        }
    }
}
