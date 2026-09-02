package com.luoxiaohei.abilities;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import com.luoxiaohei.data.PlayerData;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 金系能力 v2.0
 * - 右键当前技能触发 (左键=正常攻击/破坏)
 * - 技能0: 金属操控 (收集金属→发射, 背包铁块/铁锭也可用)
 * - 技能1: 金属风暴 (连招壹式)
 * - 技能2: 金刚护盾 (连招贰式, 显示下移到1/4)
 * - 技能3: 贯金属枪 (连招叁式)
 */
public class MetalAbility extends BaseAbility implements Listener {

    private static final List<Material> METALS = Arrays.asList(
            Material.IRON_BLOCK, Material.GOLD_BLOCK, Material.COPPER_BLOCK,
            Material.IRON_INGOT, Material.GOLD_INGOT, Material.COPPER_INGOT,
            Material.NETHERITE_BLOCK, Material.IRON_BARS, Material.ANVIL,
            Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL, Material.HEAVY_WEIGHTED_PRESSURE_PLATE,
            Material.LIGHT_WEIGHTED_PRESSURE_PLATE, Material.HOPPER, Material.BELL
    );

    private final Map<UUID, List<FallingBlock>> floatingMetals = new ConcurrentHashMap<>();
    private final Map<UUID, Long> shieldPlayers = new ConcurrentHashMap<>();
    // 追踪每个悬浮金属的轨道任务, 发射时取消 (否则轨道会覆盖发射速度)
    private final Map<Integer, org.bukkit.scheduler.BukkitTask> orbitTasks = new ConcurrentHashMap<>();

    public MetalAbility(LuoXiaoHeiPlugin plugin) {
        super(plugin, "metal");
    }

    public boolean isMetal(Material m) { return METALS.contains(m); }

    // ===== 技能0: 金属操控 =====
    public void metalControl(Player p) {
        if (!aEnabled("metal-control")) return;
        PlayerData d = dm.getData(p);

        List<FallingBlock> current = floatingMetals.computeIfAbsent(p.getUniqueId(),
                k -> Collections.synchronizedList(new ArrayList<>()));
        current.removeIf(fb -> fb == null || fb.isDead());

        // 有悬浮金属 → 发射
        if (!current.isEmpty()) {
            fireMetal(p, current);
            return;
        }

        // 无悬浮金属 → 收集
        String err = preCheck(p, "metal-control");
        if (err != null) { p.sendMessage(err); return; }
        int maxBlocks = cfg.getActionInt("metal.metal-control.max-blocks", 8);
        int range = cfg.getActionInt("metal.metal-control.range", 30);

        int collected = collectFromWorld(p, current, maxBlocks, range);
        // 从背包收集 (铁块/铁锭)
        if (current.size() < maxBlocks) {
            collected += collectFromInventory(p, current, maxBlocks - current.size());
        }

        if (collected > 0) {
            applyCost(p, "metal-control");
            p.sendMessage("§e[金属操控] §f操控 §6" + current.size() + " §f块金属, 右键再次发射!");
            playSound(p, Sound.BLOCK_ANVIL_LAND);
        } else {
            p.sendMessage("§e[金属操控] §c附近/背包没有金属! 需要铁块或铁锭");
        }
    }

    private int collectFromWorld(Player p, List<FallingBlock> current, int max, int range) {
        int collected = 0;
        World w = p.getWorld();
        Location pLoc = p.getLocation();
        // 掉落物
        for (Entity ent : w.getNearbyEntities(pLoc, range, range, range)) {
            if (collected + current.size() >= max) break;
            if (ent instanceof Item item && isMetal(item.getItemStack().getType())) {
                Material mat = item.getItemStack().getType();
                Material blockMat = itemToBlock(mat);
                if (blockMat != null) {
                    item.remove();
                    spawnFloatingMetal(p, blockMat, current);
                    collected++;
                }
            }
        }
        // 放置的方块 (5格范围)
        if (collected + current.size() < max) {
            int r = 5;
            for (int x = -r; x <= r && collected + current.size() < max; x++)
            for (int y = -r; y <= r && collected + current.size() < max; y++)
            for (int z = -r; z <= r && collected + current.size() < max; z++) {
                Block b = w.getBlockAt(pLoc.getBlockX()+x, pLoc.getBlockY()+y, pLoc.getBlockZ()+z);
                if (isMetal(b.getType())) {
                    Material t = b.getType();
                    b.setType(Material.AIR, false);
                    spawnFloatingMetal(p, t, current);
                    collected++;
                }
            }
        }
        return collected;
    }

    private int collectFromInventory(Player p, List<FallingBlock> current, int need) {
        int collected = 0;
        var inv = p.getInventory();
        for (int slot = 0; slot < inv.getSize() && collected < need; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null) continue;
            Material mat = item.getType();
            if (mat == Material.IRON_INGOT || mat == Material.IRON_BLOCK) {
                Material blockMat = itemToBlock(mat);
                if (blockMat != null) {
                    int take = Math.min(item.getAmount(), need - collected);
                    for (int i = 0; i < take; i++) {
                        spawnFloatingMetal(p, blockMat, current);
                        collected++;
                    }
                    item.setAmount(item.getAmount() - take);
                    if (item.getAmount() <= 0) inv.setItem(slot, null);
                }
            }
        }
        return collected;
    }

    private Material itemToBlock(Material m) {
        if (m.isBlock()) return m;
        switch (m) {
            case IRON_INGOT: return Material.IRON_BLOCK;
            case GOLD_INGOT: return Material.GOLD_BLOCK;
            case COPPER_INGOT: return Material.COPPER_BLOCK;
            case NETHERITE_INGOT: return Material.NETHERITE_BLOCK;
            default: return null;
        }
    }

    private void spawnFloatingMetal(Player p, Material mat, List<FallingBlock> list) {
        Location spawn = p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.5));
        FallingBlock fb = p.getWorld().spawnFallingBlock(spawn, mat.createBlockData());
        fb.setGravity(false);
        fb.setInvulnerable(true);
        fb.setDropItem(true);
        fb.setMetadata("metal-owner", new FixedMetadataValue(plugin, p.getUniqueId().toString()));
        list.add(fb);
        startFloatingOrbit(p, fb);
    }

    private void startFloatingOrbit(Player p, FallingBlock fb) {
        final float[] angle = {(float)(Math.random() * Math.PI * 2)};
        BukkitTask task = new BukkitRunnable() {
            @Override public void run() {
                if (fb.isDead() || !p.isOnline()) { cancel(); orbitTasks.remove(fb.getEntityId()); return; }
                angle[0] += 0.15;
                double r = 2.2;
                Location cur = p.getLocation();
                Location dest = cur.clone().add(Math.cos(angle[0])*r, 1.2, Math.sin(angle[0])*r);
                fb.teleport(dest);
                p.getWorld().spawnParticle(Particle.END_ROD, dest, 1, 0, 0, 0, 0);
            }
        }.runTaskTimer(plugin, 1, 1);
        orbitTasks.put(fb.getEntityId(), task);
    }

    // ===== 发射金属 (修复伤害) =====
    private void fireMetal(Player p, List<FallingBlock> list) {
        if (list.isEmpty()) return;
        FallingBlock fb = list.remove(0);
        // 取消轨道任务! 否则轨道每tick传送方块, 覆盖发射速度, 导致无法飞行
        BukkitTask orbit = orbitTasks.remove(fb.getEntityId());
        if (orbit != null) orbit.cancel();

        double speed = cfg.getActionDouble("metal.metal-control.projectile-speed", 1.5);
        double dmg = aDmg("metal-control");

        Vector dir = p.getEyeLocation().getDirection().normalize().multiply(speed);
        fb.setGravity(true);
        fb.setVelocity(dir);
        fb.setDropItem(true);

        playSound(p, Sound.ENTITY_ARROW_SHOOT);

        // 修复: 使用定时器检测碰撞造成伤害
        new BukkitRunnable() {
            int life = 0;
            final Set<Integer> hit = new HashSet<>();
            @Override public void run() {
                if (fb.isDead() || life++ > 200) { cancel(); return; }
                Location loc = fb.getLocation();
                // 检测命中实体
                for (Entity e : fb.getWorld().getNearbyEntities(loc, 1.2, 1.2, 1.2)) {
                    if (e instanceof LivingEntity le && e != p && !hit.contains(e.getEntityId())) {
                        hit.add(e.getEntityId());
                        damage(le, p, dmg, false); // 使用BaseAbility.damage (标记技能伤害)
                        le.setVelocity(dir.clone().normalize().multiply(0.6).setY(0.3));
                        fb.getWorld().spawnParticle(Particle.BLOCK, loc, 20, 0.5, 0.5, 0.5, 0.1, fb.getBlockData());
                        playSound(p, Sound.BLOCK_ANVIL_USE);
                        fb.remove();
                        cancel();
                        return;
                    }
                }
                if (fb.isOnGround() || loc.getBlock().getType().isSolid()) {
                    fb.getWorld().spawnParticle(Particle.BLOCK, loc, 10, 0.3, 0.3, 0.3, 0.05, fb.getBlockData());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 2, 1);
    }

    // ===== 技能1: 金属风暴 =====
    public void metalStorm(Player p) {
        if (!aEnabled("metal-storm")) return;
        String err = preCheck(p, "metal-storm");
        if (err != null) { p.sendMessage(err); return; }
        if (cfg.getActionBool("metal.metal-storm.need-metal", true) && !hasMetal(p)) {
            p.sendMessage("§c[金属风暴] 附近/背包没有金属!"); return;
        }
        double dmg = aDmg("metal-storm");
        int hits = cfg.getActionInt("metal.metal-storm.hits", 5);
        int radius = cfg.getActionInt("metal.metal-storm.radius", 8);
        applyCost(p, "metal-storm");
        p.sendMessage("§e[金属风暴] §f金属乱舞!");
        playSound(p, Sound.BLOCK_BEACON_ACTIVATE);
        final int[] hc = {0};
        new BukkitRunnable() {
            @Override public void run() {
                if (hc[0]++ >= hits) { cancel(); return; }
                Location loc = p.getLocation();
                for (int i = 0; i < 24; i++) {
                    double a = (Math.PI*2/24)*i + hc[0]*0.3;
                    Location pp = loc.clone().add(Math.cos(a)*radius, 0.5, Math.sin(a)*radius);
                    loc.getWorld().spawnParticle(Particle.BLOCK, pp, 2, 0,0,0,0, Material.IRON_BLOCK.createBlockData());
                }
                for (Entity e : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
                    if (e instanceof LivingEntity le && e != p) {
                        damage(le, p, dmg, false);
                        le.setVelocity(new Vector((Math.random()-0.5)*0.5, 0.3, (Math.random()-0.5)*0.5));
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 10);
    }

    // ===== 技能2: 金刚护盾 (显示下移到1/4) =====
    public void metalShield(Player p) {
        if (!aEnabled("metal-shield")) return;
        String err = preCheck(p, "metal-shield");
        if (err != null) { p.sendMessage(err); return; }
        int duration = cfg.getActionInt("metal.metal-shield.duration", 15);
        boolean kb = cfg.getActionBool("metal.metal-shield.knockback", true);
        applyCost(p, "metal-shield");
        shieldPlayers.put(p.getUniqueId(), System.currentTimeMillis() + duration * 1000L);
        p.sendMessage("§e[金刚护盾] §f护体!" + duration + "s");
        playSound(p, Sound.ITEM_SHIELD_BLOCK);
        // 护盾特效: Y偏移降低到0.5 (约屏幕1/4处)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                long end = shieldPlayers.getOrDefault(p.getUniqueId(), 0L);
                if (System.currentTimeMillis() > end || !p.isOnline()) { cancel(); return; }
                Location loc = p.getLocation();
                for (int i = 0; i < 12; i++) {
                    double a = (Math.PI*2/12)*i + t*0.08;
                    // Y=0.5 → 屏幕约1/4处
                    Location pp = loc.clone().add(Math.cos(a)*1.3, 0.5 + Math.sin(t*0.2)*0.2, Math.sin(a)*1.3);
                    loc.getWorld().spawnParticle(Particle.BLOCK_MARKER, pp, 1, Material.IRON_BLOCK.createBlockData());
                }
                t++;
            }
        }.runTaskTimer(plugin, 0, 5);
    }

    // ===== 技能3: 贯金属枪 =====
    public void metalSpear(Player p) {
        if (!aEnabled("metal-spear")) return;
        String err = preCheck(p, "metal-spear");
        if (err != null) { p.sendMessage(err); return; }
        double dmg = aDmg("metal-spear");
        int range = cfg.getActionInt("metal.metal-spear.range", 40);
        int pierce = cfg.getActionInt("metal.metal-spear.pierce", 3);
        applyCost(p, "metal-spear");
        p.sendMessage("§6[贯金属枪] §f贯穿!");
        playSound(p, Sound.ENTITY_WITHER_SHOOT);
        Vector dir = p.getEyeLocation().getDirection().normalize();
        Set<Integer> hitIds = new HashSet<>();
        int pierced = 0;
        for (int i = 1; i <= range; i++) {
            Location cur = p.getEyeLocation().clone().add(dir.clone().multiply(i));
            if (cur.getBlock().getType().isSolid() && cur.getBlock().getType() != Material.IRON_BARS) break;
            if (i % 2 == 0)
                cur.getWorld().spawnParticle(Particle.BLOCK, cur, 3, 0.2,0.2,0.2,0, Material.NETHERITE_BLOCK.createBlockData());
            if (i % 3 == 0) {
                for (Entity e : cur.getWorld().getNearbyEntities(cur, 1.2, 1.2, 1.2)) {
                    if (e instanceof LivingEntity le && e != p && !hitIds.contains(e.getEntityId())) {
                        hitIds.add(e.getEntityId());
                        damage(le, p, dmg, false);
                        le.setVelocity(dir.clone().multiply(1.2).setY(0.5));
                        if (++pierced >= pierce) return;
                    }
                }
            }
        }
    }

    private boolean hasMetal(Player p) {
        List<FallingBlock> list = floatingMetals.get(p.getUniqueId());
        if (list != null && !list.isEmpty()) return true;
        // 背包检查
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && isMetal(item.getType())) return true;
        }
        // 附近方块
        Location loc = p.getLocation();
        for (int x = -5; x <= 5; x++) for (int y = -5; y <= 5; y++) for (int z = -5; z <= 5; z++) {
            if (isMetal(p.getWorld().getBlockAt(loc.getBlockX()+x, loc.getBlockY()+y, loc.getBlockZ()+z).getType()))
                return true;
        }
        return false;
    }

    // ===== 事件: 右键触发技能 (左键=正常攻击/破坏) =====
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        PlayerData d = dm.getData(p);
        if (d.getAbilityType() != AbilityType.METAL) return;
        if (!d.isEnabled()) return; // 技能关闭时不拦截
        // 只拦截右键 (左键正常)
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // 手持灵瓜时不触发技能 (灵瓜监听器已cancel, 这里跳过)
        if (plugin.getSpiritItemManager().isSpiritMelon(p.getInventory().getItemInMainHand())) return;
        e.setCancelled(true);
        int idx = d.getCurrentSkillIndex();
        switch (idx) {
            case 0: metalControl(p); break;
            case 1: metalStorm(p); break;
            case 2: metalShield(p); break;
            case 3: metalSpear(p); break;
        }
    }

    // ===== 金刚护盾减伤 =====
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (dm.getData(p).getAbilityType() != AbilityType.METAL) return;
        long end = shieldPlayers.getOrDefault(p.getUniqueId(), 0L);
        if (System.currentTimeMillis() < end) {
            double def = cfg.getActionDouble("metal.metal-shield.defense", 0.5);
            e.setDamage(e.getDamage() * (1 - def));
            p.getWorld().spawnParticle(Particle.BLOCK, p.getLocation().add(0,0.5,0), 10, 0.5,0.5,0.5,0.05,
                    Material.IRON_BLOCK.createBlockData());
            if (cfg.getActionBool("metal.metal-shield.knockback", true) && e.getDamager() instanceof LivingEntity att) {
                att.damage(e.getDamage() * 0.3, p);
                att.setVelocity(att.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1).setY(0.5));
            }
        }
    }
}
