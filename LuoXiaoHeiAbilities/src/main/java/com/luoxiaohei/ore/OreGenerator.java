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
 * 灵矿生成器 v2.0 - 生成概率=铁矿级别
 * 生成白色发光方块 (SEA_LANTERN), 被SpiritItemManager追踪
 */
public class OreGenerator extends BlockPopulator implements Listener {

    private final LuoXiaoHeiPlugin plugin;
    private final Material oreMaterial;
    private final int minHeight;
    private final int maxHeight;
    private final int maxVeinSize;
    private final int attemptsPerChunk;

    public OreGenerator(LuoXiaoHeiPlugin plugin) {
        this.plugin = plugin;
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("spirit-ore");
        String type = cfg == null ? "SEA_LANTERN" : cfg.getString("block-type", "SEA_LANTERN");
        Material m = Material.SEA_LANTERN;
        try { m = Material.valueOf(type); } catch (Exception ignored) {}
        this.oreMaterial = m;
        this.minHeight = cfg == null ? -24 : cfg.getInt("min-height", -24);
        this.maxHeight = cfg == null ? 56 : cfg.getInt("max-height", 56);
        this.maxVeinSize = cfg == null ? 9 : cfg.getInt("max-vein-size", 9);
        // 铁矿级别: ~20次/区块 (铁矿默认~20)
        this.attemptsPerChunk = cfg == null ? 20 : cfg.getInt("attempts-per-chunk", 20);
    }

    public Material getOreMaterial() { return oreMaterial; }

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
            if (isReplaceableStone(b.getType())) {
                b.setType(oreMaterial, false);
                // 追踪为灵矿
                plugin.getSpiritItemManager().trackOreBlock(b);
                placed++;
            }
        }
    }

    private static boolean isReplaceableStone(Material t) {
        return t == Material.STONE || t == Material.DEEPSLATE || t == Material.TUFF
            || t == Material.ANDESITE || t == Material.DIORITE || t == Material.GRANITE
            || t == Material.GRAVEL || t == Material.DIRT;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent e) {
        if (e.isNewChunk() && !Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () ->
                populate(e.getWorld(), ThreadLocalRandom.current(), e.getChunk()));
        }
    }
}
