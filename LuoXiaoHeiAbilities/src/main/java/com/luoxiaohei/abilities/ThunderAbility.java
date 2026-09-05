package com.luoxiaohei.abilities;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import com.luoxiaohei.data.PlayerData;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 雷系能力 v2.0
 * - 右键当前技能触发 (左键=正常攻击/破坏)
 * - 技能0: 雷击 (lightning-bolt)
 * - 技能1: 雷神之怒 (thunder-fury)
 * - 技能2: 雷电冲刺 (thunder-dash)
 */
public class ThunderAbility extends BaseAbility implements Listener {

    public ThunderAbility(LuoXiaoHeiPlugin plugin) {
        super(plugin, "thunder");
    }

    // ===== 技能0: 雷击 =====
    public void bolt(Player p) {
        if (!aEnabled("lightning-bolt")) return;
        String err = preCheck(p, "lightning-bolt");
        if (err != null) { p.sendMessage(err); return; }
        double dmg = aDmg("lightning-bolt");
        int range = cfg.getActionInt("thunder.lightning-bolt.range", 30);
        applyCost(p, "lightning-bolt");
        p.sendMessage("§e[雷击] §f轰!");
        playSound(p, Sound.ENTITY_LIGHTNING_BOLT_THUNDER);

        LivingEntity target = getTarget(p, range);
        Location strikeLoc = target != null ? target.getLocation()
                : p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(range));
        p.getWorld().strikeLightning(strikeLoc);
        if (target != null) {
            damage(target, p, dmg, false);
        }
        p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, strikeLoc, 30, 0.5, 1, 0.5, 0.1);
    }

    // ===== 技能1: 雷神之怒 =====
    public void fury(Player p) {
        if (!aEnabled("thunder-fury")) return;
        String err = preCheck(p, "thunder-fury");
        if (err != null) { p.sendMessage(err); return; }
        double dmg = aDmg("thunder-fury");
        int radius = cfg.getActionInt("thunder.thunder-fury.radius", 10);
        int strikes = cfg.getActionInt("thunder.thunder-fury.strikes", 5);
        applyCost(p, "thunder-fury");
        p.sendMessage("§e[雷神之怒] §f万雷齐发!");
        playSound(p, Sound.ENTITY_LIGHTNING_BOLT_THUNDER);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (n++ >= strikes) { cancel(); return; }
                Location loc = p.getLocation();
                double a = Math.random() * Math.PI * 2;
                double r = Math.random() * radius;
                Location s = loc.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                p.getWorld().strikeLightning(s);
                p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, s, 15, 0.5, 1, 0.5, 0.1);
                for (Entity e : s.getWorld().getNearbyEntities(s, 2, 2, 2)) {
                    if (e instanceof LivingEntity le && e != p) {
                        damage(le, p, dmg, false);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 10);
    }

    // ===== 技能2: 雷电冲刺 =====
    public void dash(Player p) {
        if (!aEnabled("thunder-dash")) return;
        String err = preCheck(p, "thunder-dash");
        if (err != null) { p.sendMessage(err); return; }
        double dmg = aDmg("thunder-dash");
        int distance = cfg.getActionInt("thunder.thunder-dash.distance", 15);
        applyCost(p, "thunder-dash");
        p.sendMessage("§e[雷电冲刺] §f瞬移!");
        playSound(p, Sound.ENTITY_LIGHTNING_BOLT_THUNDER);

        Location start = p.getLocation();
        Vector dir = p.getEyeLocation().getDirection().normalize();
        Set<Integer> hit = new HashSet<>();
        Location lastValid = start.clone();
        for (int i = 1; i <= distance; i++) {
            Location cur = start.clone().add(dir.clone().multiply(i));
            // 脚部或头部被实心方块阻挡 → 停止
            if (cur.getBlock().getType().isSolid()
                    || cur.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                break;
            }
            lastValid = cur;
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, cur, 5, 0.3, 0.3, 0.3, 0.1);
            for (Entity e : cur.getWorld().getNearbyEntities(cur, 1.2, 1.2, 1.2)) {
                if (e instanceof LivingEntity le && e != p && !hit.contains(e.getEntityId())) {
                    hit.add(e.getEntityId());
                    damage(le, p, dmg, false);
                    le.setVelocity(dir.clone().multiply(0.8).setY(0.3));
                }
            }
        }
        Location end = lastValid;
        end.setYaw(start.getYaw());
        end.setPitch(start.getPitch());
        p.teleport(end);
        p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, start, 20, 0.5, 0.5, 0.5, 0.1);
    }

    // 防止对空气+对物体同时触发两次 (去抖)
    private final Map<UUID, Long> interactDebounce = new ConcurrentHashMap<>();
    private static final long INTERACT_DEBOUNCE_MS = 80;

    // ===== 事件: 左键触发技能 (攻击敌人走EntityDamage, 不受影响) =====
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        PlayerData d = dm.getData(p);
        if (d.getAbilityType() != AbilityType.THUNDER) return;
        if (!d.isEnabled()) return;
        if (!p.hasPermission("luoxiaohei.keys")) return;
        Action act = e.getAction();
        if (act != Action.LEFT_CLICK_AIR && act != Action.LEFT_CLICK_BLOCK) return;
        if (plugin.getSpiritItemManager().isSpiritMelon(p.getInventory().getItemInMainHand())) return;
        EquipmentSlot h = e.getHand();
        if (h != null && h != EquipmentSlot.HAND) return;
        long now = System.currentTimeMillis();
        Long last = interactDebounce.get(p.getUniqueId());
        if (last != null && now - last < INTERACT_DEBOUNCE_MS) return;
        interactDebounce.put(p.getUniqueId(), now);
        // 取消: 阻止破坏方块
        e.setCancelled(true);
        switch (d.getCurrentSkillIndex()) {
            case 0: bolt(p); break;
            case 1: fury(p); break;
            case 2: dash(p); break;
        }
    }
}
