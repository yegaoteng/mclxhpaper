package com.luoxiaohei.ore;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.generator.BlockPopulator;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 灵矿生成器 v2.3 - 铁矿材质 + 附魔光效粒子 + 只在矿洞(地下)生成
 * 生成几率 = 铁矿级别
 * 灵矿方块用 IRON_ORE/DEEPSLATE_IRON_ORE, 通过SpiritItemManager追踪
 * 附魔光效: 由SpiritItemManager的粒子调度器生成 END_ROD 闪光
 */
public class OreGenerator extends BlockPopulator implements Listener {

    private final LuoXiaoHeiPlugin plugin;
    private final Material oreMaterial;        // 浅层 (y>=0) 铁矿
    private final Material deepOreMaterial;    // 深层 (y<0) 深板岩铁矿
    private final int minHeight;
    private final int maxHeight;
    private final int maxVeinSize;
    private final int attemptsPerChunk;

    public OreGenerator(LuoXiaoHeiPlugin plugin) {
        this.plugin = plugin;
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("spirit-ore");
        // 灵矿用铁矿材质 (白色发光感由粒子模拟)
        this.oreMaterial = Material.IRON_ORE;
        this.deepOreMaterial = Material.DEEPSLATE_IRON_ORE;
        // 只在地下生成 (y < 56, 不在地表)
        this.minHeight = cfg == null ? -24 : cfg.getInt("min-height", -24);
        this.maxHeight = cfg == null ? 56 : cfg.getInt("max-height", 56);
        this.maxVeinSize = cfg == null ? 9 : cfg.getInt("max-vein-size", 9);
        // 铁矿级别: ~20次/区块 (铁矿默认~20)
        this.attemptsPerChunk = cfg == null ? 20 : cfg.getInt("attempts-per-chunk", 20);
    }

    public Material getOreMaterial() { return oreMaterial; }
    public Material getDeepOreMaterial() { return deepOreMaterial; }

    /** 选择对应深度的灵矿材质 */
    private Material getOreForY(int y) {
        return y < 0 ? deepOreMaterial : oreMaterial;
    }

    public void register() {
        for (World w : Bukkit.getWorlds()) {
            try { w.getPopulators().add(this); } catch (Exception ignored) {}
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void populate(World world, Random random, org.bukkit.Chunk source) {
        if (world.getEnvironment() != World.Environment.NORMAL) return;
        int baseX = source.getX() << 4;
        int baseZ = source.getZ() << 4;
        Random rnd = ThreadLocalRandom.current();

        for (int i = 0; i < attemptsPerChunk; i++) {
            int x = baseX + rnd.nextInt(16);
            int z = baseZ + rnd.nextInt(16);
            int y = minHeight + rnd.nextInt(Math.max(1, maxHeight - minHeight));
            int veinSize = 1 + rnd.nextInt(maxVeinSize);
            generateVein(world, x, y, z, veinSize, rnd);
        }
    }

    private void generateVein(World world, int x, int y, int z, int size, Random rnd) {
        int placed = 0;
        int r = (int) Math.ceil(Math.cbrt(size));
        for (int dx = -r; dx <= r && placed < size; dx++)
        for (int dy = -r; dy <= r && placed < size; dy++)
        for (int dz = -r; dz <= r && placed < size; dz++) {
            if (dx*dx + dy*dy + dz*dz > r*r) continue;
            if (rnd.nextInt(3) == 0 && placed > 0) continue;
            int bx = x+dx, by = y+dy, bz = z+dz;
            if (by < world.getMinHeight() || by >= world.getMaxHeight()) continue;
            Block b = world.getBlockAt(bx, by, bz);
            // 只在地下石头/深板岩中生成 (不在地表泥土/草方块生成)
            if (isUndergroundStone(b.getType())) {
                Material ore = getOreForY(by);
                b.setType(ore, false);
                // 追踪为灵矿
                plugin.getSpiritItemManager().trackOreBlock(b);
                placed++;
            }
        }
    }

    /** 仅地下石头类 (不含地表泥土/草方块/沙子) */
    private static boolean isUndergroundStone(Material t) {
        return t == Material.STONE || t == Material.DEEPSLATE || t == Material.TUFF
            || t == Material.ANDESITE || t == Material.DIORITE || t == Material.GRANITE;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent e) {
        if (e.isNewChunk() && !Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () ->
                populate(e.getWorld(), ThreadLocalRandom.current(), e.getChunk()));
        }
    }
}
