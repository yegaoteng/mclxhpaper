package com.luoxiaohei.abilities;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import com.luoxiaohei.data.PlayerData;
import com.luoxiaohei.util.Compat;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 空间系能力 v2.0
 * - 右键触发当前技能 (左键=正常攻击)
 * - 技能0: 空间吞噬 (仅石头, 30秒复原, 不破坏地形)
 * - 技能1: 空间瞬移 (充能)
 * - 技能2: 空间领域 (黑色半圆领域视觉)
 * - 技能3: 虚空斩 (真实伤害)
 */
public class SpaceAbility extends BaseAbility implements Listener {

    private static class StoredBlock {
        long restoreAt;
        World world;
        int x, y, z;
        BlockData data;
    }
    private final Queue<StoredBlock> devourQueue = new ConcurrentLinkedDeque<>();
    private final Map<UUID, Double> tempShield = new ConcurrentHashMap<>();

    private static class Domain {
        Player owner;
        Location center;
        int radius;
        long endTime;
    }
    private final List<Domain> activeDomains = Collections.synchronizedList(new ArrayList<>());
    private BukkitTask restoreTask;
    private BukkitTask domainTask;

    public SpaceAbility(LuoXiaoHeiPlugin plugin) {
        super(plugin, "space");
    }

    // ===== 吞噬方块复原调度器 =====
    public void startRestoreScheduler() {
        if (restoreTask != null) restoreTask.cancel();
        int maxQueue = cfg.getConfig().getInt("performance.devour-queue-max", 500);
        restoreTask = new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                int restored = 0;
                while (restored < 10 && !devourQueue.isEmpty()) {
                    StoredBlock sb = devourQueue.peek();
                    if (sb.restoreAt > now) break;
                    devourQueue.poll();
                    if (sb.world != null && sb.world.isChunkLoaded(sb.x >> 4, sb.z >> 4)) {
                        Block b = sb.world.getBlockAt(sb.x, sb.y, sb.z);
                        if (!b.getWorld().getNearbyEntities(new Location(b.getWorld(), sb.x+0.5, sb.y+0.5, sb.z+0.5),
                                0.6, 1.8, 0.6, e -> e instanceof Player).isEmpty()) {
                            sb.restoreAt = now + 2000;
                            if (devourQueue.size() < maxQueue) devourQueue.offer(sb);
                            continue;
                        }
                        b.setBlockData(sb.data, false);
                        sb.world.spawnParticle(Particle.PORTAL, sb.x+0.5, sb.y+0.5, sb.z+0.5, 5, 0.3,0.3,0.3,0.02);
                        restored++;
                    } else {
                        sb.restoreAt = now + 5000;
                        if (devourQueue.size() < maxQueue) devourQueue.offer(sb);
                    }
                }
                while (devourQueue.size() > maxQueue) {
                    StoredBlock sb = devourQueue.poll();
                    if (sb != null && sb.world.isChunkLoaded(sb.x>>4, sb.z>>4))
                        sb.world.getBlockAt(sb.x, sb.y, sb.z).setBlockData(sb.data, false);
                }
            }
        }.runTaskTimer(plugin, 20, 5);
    }

    public void forceRestoreAll() {
        for (StoredBlock sb : devourQueue) {
            try { if (sb.world != null) sb.world.getBlockAt(sb.x, sb.y, sb.z).setBlockData(sb.data, false); }
            catch (Exception ignored) {}
        }
        devourQueue.clear();
    }

    // ===== 领域调度器 (黑色半圆视觉) =====
    public void startDomainScheduler() {
        if (domainTask != null) domainTask.cancel();
        domainTask = new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                // 结束的领域: 清除所有者metadata
                activeDomains.removeIf(d -> {
                    if (now > d.endTime) {
                        if (d.owner != null && d.owner.isOnline()) {
                            d.owner.removeMetadata("domain-owner", plugin);
                            d.owner.removeMetadata("domain-immune", plugin);
                        }
                        return true;
                    }
                    return false;
                });
                for (Domain d : activeDomains) {
                    try { applyDomainEffects(d); spawnBlackDomainVisual(d); } catch (Exception ignored) {}
                }
            }
        }.runTaskTimer(plugin, 20, 4); // 每4tick刷新视觉
    }

    private void applyDomainEffects(Domain d) {
        ConfigurationSection eff = cfg.getActionSection("space.space-domain.effects");
        int speed = eff == null ? 2 : eff.getInt("speed-boost", 2);
        int slow = eff == null ? 2 : eff.getInt("slowness-enemy", 2);
        double dmgBoost = eff == null ? 0.3 : eff.getDouble("damage-boost", 0.3);
        boolean blind = eff != null && eff.getBoolean("blindness", false);

        if (d.owner.isOnline() && isInside(d, d.owner.getLocation())) {
            // 速度2 (去除跳跃提升)
            d.owner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, speed-1, false, false, false));
            if (blind) d.owner.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 60, 0, false, false, false));
            // 标记领域加成
            d.owner.setMetadata("domain-owner", new FixedMetadataValue(plugin, String.valueOf(dmgBoost)));
            // 标记领域免伤 (95%减伤)
            d.owner.setMetadata("domain-immune", new FixedMetadataValue(plugin, "0.95"));
        } else {
            d.owner.removeMetadata("domain-owner", plugin);
            d.owner.removeMetadata("domain-immune", plugin);
        }
        for (Entity e : d.center.getWorld().getNearbyEntities(d.center, d.radius, d.radius, d.radius)) {
            if (e == d.owner || !(e instanceof LivingEntity le)) continue;
            if (isInside(d, le.getLocation())) {
                le.addPotionEffect(new PotionEffect(Compat.EFFECT_SLOWNESS, 30, slow-1, false, false, false));
                if (blind) le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false, false));
            }
        }
    }

    private boolean isInside(Domain d, Location loc) {
        if (!loc.getWorld().equals(d.center.getWorld())) return false;
        return loc.distance(d.center) <= d.radius;
    }

    /**
     * 黑色半圆形领域视觉 - 黑色粒子球体 (上半球)
     */
    private void spawnBlackDomainVisual(Domain d) {
        World w = d.center.getWorld();
        double r = d.radius;
        // 球壳 (上半球) - 黑色粒子
        Particle.DustOptions blackDust = new Particle.DustOptions(Color.BLACK, (float)(r * 0.15));
        int layers = 8;
        for (int layer = 0; layer <= layers; layer++) {
            double phi = (Math.PI / 2) * layer / layers; // 0=赤道, PI/2=北极
            double ringR = r * Math.cos(phi);
            double ringY = r * Math.sin(phi);
            int steps = Math.max(8, (int)(ringR * 1.5));
            for (int i = 0; i < steps; i++) {
                double theta = (Math.PI * 2 / steps) * i;
                double x = d.center.getX() + ringR * Math.cos(theta);
                double y = d.center.getY() + ringY;
                double z = d.center.getZ() + ringR * Math.sin(theta);
                w.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, blackDust);
            }
        }
        // 地面圈
        Particle.DustOptions darkDust = new Particle.DustOptions(Color.fromRGB(20,0,30), 1.5f);
        int groundSteps = Math.max(16, (int)(r * 2));
        for (int i = 0; i < groundSteps; i++) {
            double theta = (Math.PI * 2 / groundSteps) * i;
            double x = d.center.getX() + r * Math.cos(theta);
            double z = d.center.getZ() + r * Math.sin(theta);
            w.spawnParticle(Particle.DUST, x, d.center.getY() + 0.1, z, 2, 0, 0, 0, 0, darkDust);
        }
        // 内部稀疏粒子 (黑暗感)
        for (int i = 0; i < 10; i++) {
            double rr = Math.random() * r * 0.9;
            double a = Math.random() * Math.PI * 2;
            double h = Math.random() * r * 0.8;
            w.spawnParticle(Particle.SQUID_INK,
                d.center.getX() + Math.cos(a) * rr,
                d.center.getY() + h,
                d.center.getZ() + Math.sin(a) * rr,
                1, 0.3, 0.3, 0.3, 0);
        }
    }

    // ===== 技能2: 空间领域 =====
    public void castDomain(Player p) {
        if (!aEnabled("space-domain")) return;
        String err = preCheck(p, "space-domain");
        if (err != null) { p.sendMessage(err); return; }
        int duration = cfg.getActionInt("space.space-domain.duration", 60);
        int radius = cfg.getActionInt("space.space-domain.radius", 20);
        applyCost(p, "space-domain");
        Domain d = new Domain();
        d.owner = p; d.center = p.getLocation(); d.radius = radius;
        d.endTime = System.currentTimeMillis() + duration * 1000L;
        activeDomains.add(d);
        p.sendMessage("§5[空间领域] §f展开! 半径" + radius + " 持续" + duration + "s");
        playSound(p, Sound.BLOCK_BEACON_POWER_SELECT);
        p.getWorld().spawnParticle(Compat.PARTICLE_EXPLOSION_HUGE, d.center, 1, 0,0,0,0);
    }

    // ===== 技能0: 空间吞噬 (仅石头, 30秒复原) =====
    public void devourBlocks(Player p) {
        if (!aEnabled("space-devour")) return;
        String err = preCheck(p, "space-devour");
        if (err != null) { p.sendMessage(err); return; }
        int maxBlocks = cfg.getActionInt("space.space-devour.max-blocks", 64);
        int range = cfg.getActionInt("space.space-devour.range", 12);
        int restoreSec = 30; // 固定30秒

        Location start = p.getEyeLocation();
        Vector dir = start.getDirection().normalize();
        int eaten = 0;
        long restoreTime = System.currentTimeMillis() + restoreSec * 1000L;
        double shieldGained = 0;
        double shieldPerBlock = cfg.getActionDouble("space.space-devour.temp-shield-per-block", 0.5);
        double maxShield = cfg.getActionDouble("space.space-devour.max-temp-shield", 30);

        for (int i = 1; i <= range && eaten < maxBlocks; i++) {
            Location c1 = start.clone().add(dir.clone().multiply(i));
            for (int dx = -1; dx <= 1 && eaten < maxBlocks; dx++)
            for (int dy = -1; dy <= 1 && eaten < maxBlocks; dy++)
            for (int dz = -1; dz <= 1 && eaten < maxBlocks; dz++) {
                int bx = c1.getBlockX()+dx, by = c1.getBlockY()+dy, bz = c1.getBlockZ()+dz;
                Block b = p.getWorld().getBlockAt(bx, by, bz);
                Material t = b.getType();
                if (t == Material.AIR || t.getHardness() < 0) continue;
                // 仅吞噬石头类 (不含矿石)
                if (!isDevourableStone(t)) continue;
                StoredBlock sb = new StoredBlock();
                sb.world = b.getWorld(); sb.x = bx; sb.y = by; sb.z = bz;
                sb.data = b.getBlockData().clone();
                sb.restoreAt = restoreTime;
                devourQueue.offer(sb);
                b.setType(Material.AIR, false);
                eaten++;
                shieldGained += shieldPerBlock;
                b.getWorld().spawnParticle(Particle.SQUID_INK, bx+0.5, by+0.5, bz+0.5, 5, 0.2,0.2,0.2,0.05);
            }
        }
        if (eaten > 0) {
            applyCost(p, "space-devour");
            double add = Math.min(maxShield - tempShield.getOrDefault(p.getUniqueId(), 0d), shieldGained);
            if (add > 0) tempShield.merge(p.getUniqueId(), add, Double::sum);
            p.sendMessage("§5[空间吞噬] §f吞噬" + eaten + "方块 " + restoreSec + "秒后复原!");
            playSound(p, Sound.ENTITY_ENDERMAN_TELEPORT);
        } else {
            p.sendMessage("§5[空间吞噬] §c前方没有可吞噬的石头 (矿石不可吞噬)");
        }
    }

    /**
     * 可吞噬的石头类型 (不含矿石)
     */
    private boolean isDevourableStone(Material t) {
        if (t.name().contains("ORE")) return false; // 不吞噬任何矿石
        return t == Material.STONE || t == Material.DEEPSLATE || t == Material.COBBLESTONE
            || t == Material.DIRT || t == Material.GRASS_BLOCK || t == Material.SAND
            || t == Material.GRAVEL || t == Material.NETHERRACK || t == Material.BLACKSTONE
            || t == Material.BASALT || t == Material.TUFF || t == Material.CALCITE
            || t == Material.DRIPSTONE_BLOCK || t == Material.POINTED_DRIPSTONE
            || t == Material.SANDSTONE || t == Material.RED_SANDSTONE
            || t == Material.TERRACOTTA || t == Material.NETHERRACK
            || t == Material.COARSE_DIRT || t == Material.PODZOL
            || t == Material.MYCELIUM || t == Material.ROOTED_DIRT
            || t == Material.MOSS_BLOCK || t == Material.DEEPSLATE
            || t == Material.COBBLED_DEEPSLATE || t == Material.STONE_BRICKS
            || t == Material.DEEPSLATE_BRICKS || t == Material.SMOOTH_STONE
            || t == Material.ANDESITE || t == Material.DIORITE || t == Material.GRANITE
            || t == Material.NETHERRACK || t == Material.END_STONE
            || t == Material.SNOW_BLOCK || t == Material.PACKED_ICE
            || t == Material.BLUE_ICE || t == Material.CLAY;
    }

    // ===== 技能1: 空间瞬移 =====
    public void teleport(Player p) {
        if (!aEnabled("space-teleport")) return;
        int maxCharges = cfg.getActionInt("space.space-teleport.max-charges", 3);
        int chargeTime = cfg.getActionInt("space.space-teleport.charge-time", 20);
        int charges = dm.getCharges(p, "space-teleport", maxCharges);
        if (charges <= 0) {
            p.sendMessage("§c[空间瞬移] 充能不足 (" + charges + "/" + maxCharges + ")");
            return;
        }
        if (!dm.consumeSpiritual(p, aCost("space-teleport"))) {
            p.sendMessage("§c灵力不足!");
            return;
        }
        int range = cfg.getActionInt("space.space-teleport.range", 20);
        var ray = p.rayTraceBlocks(range, FluidCollisionMode.NEVER);
        Location dest;
        if (ray != null && ray.getHitBlock() != null) {
            // 命中墙: 瞬移到命中点前1格 (不穿墙, 避免卡墙里)
            Vector hit = ray.getHitPosition();
            // 向后退1格, 落脚在墙前
            Vector back = p.getEyeLocation().getDirection().normalize().multiply(-1.0);
            dest = new Location(p.getWorld(), hit.getX() + back.getX(),
                hit.getY() + back.getY(), hit.getZ() + back.getZ());
            // 如果退1格仍是实心 (贴脸墙), 提示不能对着墙
            if (dest.getBlock().getType().isSolid()) {
                p.sendMessage("§c[空间瞬移] 不能对着墙使用! 请对着空气或空地");
                dm.addSpiritual(p, aCost("space-teleport")); // 退还灵力
                return;
            }
        } else {
            // 对着空气: 瞬移到视线尽头
            dest = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(range));
        }
        // 落脚点向上找安全位置 (避免卡在方块里)
        while (dest.getBlock().getType().isSolid() && dest.getY() < 320) dest.add(0, 1, 0);
        while (dest.clone().add(0, -1, 0).getBlock().getType().isAir() && dest.getY() > -64) dest.add(0, -1, 0);
        dest.setX(dest.getBlockX() + 0.5);
        dest.setZ(dest.getBlockZ() + 0.5);
        // 保留玩家朝向 (修复瞬移后视角反向)
        dest.setYaw(p.getLocation().getYaw());
        dest.setPitch(p.getLocation().getPitch());
        Location old = p.getLocation();
        p.teleport(dest);
        p.getWorld().spawnParticle(Particle.PORTAL, old, 30, 0.5,1,0.5,0.1);
        p.getWorld().spawnParticle(Particle.PORTAL, dest, 30, 0.5,1,0.5,0.1);
        playSound(p, Sound.ENTITY_ENDERMAN_TELEPORT);
        dm.useCharge(p, "space-teleport", maxCharges);
        dm.rechargeTick(p, "space-teleport", maxCharges, chargeTime);
    }

    // ===== 技能3: 虚空斩 =====
    public void spaceSlash(Player p) {
        if (!aEnabled("space-slash")) return;
        String err = preCheck(p, "space-slash");
        if (err != null) { p.sendMessage(err); return; }
        double dmg = aDmg("space-slash");
        int range = cfg.getActionInt("space.space-slash.range", 25);
        int width = cfg.getActionInt("space.space-slash.width", 3);
        boolean ignoreArmor = cfg.getActionBool("space.space-slash.ignore-armor", true);
        applyCost(p, "space-slash");
        p.sendMessage("§5[虚空斩] §f空间撕裂!");
        playSound(p, Sound.ENTITY_WARDEN_SONIC_BOOM);
        Vector dir = p.getEyeLocation().getDirection().normalize();
        Vector side = dir.clone().crossProduct(new Vector(0,1,0)).normalize();
        Set<Integer> hitSet = new HashSet<>();
        for (int i = 1; i <= range; i++) {
            Location center = p.getEyeLocation().clone().add(dir.clone().multiply(i));
            for (int w = -width; w <= width; w++) {
                Location l = center.clone().add(side.clone().multiply(w * 0.5));
                l.getWorld().spawnParticle(Particle.SONIC_BOOM, l, 1, 0,0,0,0);
                for (Entity e : l.getWorld().getNearbyEntities(l, 0.8, 2, 0.8)) {
                    if (e instanceof LivingEntity le && e != p && !hitSet.contains(e.getEntityId())) {
                        hitSet.add(e.getEntityId());
                        damage(le, p, dmg, ignoreArmor);
                    }
                }
            }
        }
    }

    // ===== 事件: 右键触发 =====
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        PlayerData d = dm.getData(p);
        if (d.getAbilityType() != AbilityType.SPACE) return;
        if (!d.isEnabled()) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // 手持灵瓜时不触发技能 (灵瓜监听器已cancel, 这里跳过)
        if (plugin.getSpiritItemManager().isSpiritMelon(p.getInventory().getItemInMainHand())) return;
        e.setCancelled(true);
        switch (d.getCurrentSkillIndex()) {
            case 0: devourBlocks(p); break;
            case 1: teleport(p); break;
            case 2: castDomain(p); break;
            case 3: spaceSlash(p); break;
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        double dmg = e.getFinalDamage();
        if (dmg <= 0) return;
        boolean reduced = false;

        // 领域免伤 (95%减伤) - 适用于所有伤害源 (近战/远程/魔法)
        for (MetadataValue mv : p.getMetadata("domain-immune")) {
            if (mv.getOwningPlugin() == plugin) {
                try {
                    double immune = Double.parseDouble(mv.asString());
                    double reduced2 = dmg * (1 - immune);
                    e.setDamage(reduced2);
                    dmg = reduced2;
                    reduced = true;
                    p.spawnParticle(Particle.PORTAL, p.getLocation().add(0,1,0), 5, 0.3,0.5,0.3,0.05);
                } catch (Exception ignored) {}
                break;
            }
        }

        // 临时护盾 (吞噬获得)
        Double shield = tempShield.get(p.getUniqueId());
        if (shield != null && shield > 0) {
            double absorb = Math.min(shield, dmg);
            shield -= absorb;
            tempShield.put(p.getUniqueId(), shield);
            e.setDamage(dmg - absorb);
            if (!reduced) p.spawnParticle(Particle.PORTAL, p.getLocation().add(0,1,0), 10, 0.3,0.5,0.3,0.05);
            if (shield <= 0) tempShield.remove(p.getUniqueId());
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (dm.getData(p).getAbilityType() != AbilityType.SPACE) return;
        int maxCharges = cfg.getActionInt("space.space-teleport.max-charges", 3);
        int chargeTime = cfg.getActionInt("space.space-teleport.charge-time", 20);
        dm.rechargeTick(p, "space-teleport", maxCharges, chargeTime);
    }
}
