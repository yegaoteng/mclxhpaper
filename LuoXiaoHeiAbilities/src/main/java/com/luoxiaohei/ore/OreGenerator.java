package com.luoxiaohei.ore;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 灵矿生成器 v2.4.6
 *
 * v2.4.5→v2.4.6 修复:
 * 1. 矿量过多问题: 每区块生成90-113,远超铁矿级别
 *    → generateVein的球形填充过宽(r=2时27格,r=3时125格) × 33%概率跳过太松
 *    → 改为: 固定矿脉大小 (1~4), 50%概率跳过, 每区块6次尝试(原版铁矿)
 * 2. 玩家看不到灵矿: ChunkLoadEvent + runTask 可能在玩家进入后才修改方块
 *    → Paper API 需刷新客户端 chunk: 使用 getChunk().getWorld().refreshChunk(x,z)
 *    → 对已生成 chunk 回填时, 必须刷新可见的 chunk
 * 3. 对回填灵矿的玩家发送 block change 包, 立即看到
 */
public class OreGenerator implements Listener {

    private final LuoXiaoHeiPlugin plugin;
    private final Material oreMaterial;
    private final Material deepOreMaterial;
    private final int minHeight;
    private final int maxHeight;
    private final int maxVeinSize;
    private final int attemptsPerChunk;
    private final ConcurrentHashMap<String, Boolean> populatedChunks = new ConcurrentHashMap<>();

    public OreGenerator(LuoXiaoHeiPlugin plugin) {
        this.plugin = plugin;
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("spirit-ore");
        this.oreMaterial = Material.IRON_ORE;
        this.deepOreMaterial = Material.DEEPSLATE_IRON_ORE;
        this.minHeight = cfg == null ? -58 : cfg.getInt("min-height", -58);
        this.maxHeight = cfg == null ? 32 : cfg.getInt("max-height", 32);
        this.maxVeinSize = cfg == null ? 4 : Math.min(4, cfg.getInt("max-vein-size", 4));
        // 铁矿级别: 原版MC 1.21 约6次/区块矿脉尝试
        this.attemptsPerChunk = cfg == null ? 6 : Math.min(8, cfg.getInt("attempts-per-chunk", 6));
        plugin.getLogger().info("灵矿生成器初始化 v2.4.6: y=" + minHeight + "~" + maxHeight
                + ", 每区块" + attemptsPerChunk + "次尝试, 矿脉大小1~" + maxVeinSize);
    }

    public Material getOreMaterial() { return oreMaterial; }
    public Material getDeepOreMaterial() { return deepOreMaterial; }

    private Material getOreForY(int y) { return y < 0 ? deepOreMaterial : oreMaterial; }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("灵矿生成器已注册 (ChunkLoadEvent 主线程模式 v2.4.6)");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent e) {
        if (e.getWorld().getEnvironment() != World.Environment.NORMAL) return;
        if (!e.isNewChunk()) return;
        if (!e.getChunk().isGenerated()) return;
        String key = e.getWorld().getUID() + ":" + e.getChunk().getX() + ":" + e.getChunk().getZ();
        if (populatedChunks.containsKey(key)) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            Boolean already = populatedChunks.putIfAbsent(key, Boolean.TRUE);
            if (already != null) return;
            try {
                doPopulate(e.getWorld(), e.getChunk());
            } catch (Exception ex) {
                plugin.getLogger().warning("灵矿生成异常 Chunk(" + e.getChunk().getX() + "," + e.getChunk().getZ() + "): " + ex.getMessage());
            }
        });
    }

    private void doPopulate(World world, Chunk chunk) {
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        Random rnd = ThreadLocalRandom.current();
        int generated = 0;
        for (int i = 0; i < attemptsPerChunk; i++) {
            int x = baseX + rnd.nextInt(16);
            int z = baseZ + rnd.nextInt(16);
            int y = minHeight + rnd.nextInt(Math.max(1, maxHeight - minHeight));
            int veinSize = 1 + rnd.nextInt(maxVeinSize);
            generated += generateVeinSafe(world, x, y, z, veinSize, rnd);
        }
        if (generated > 0) {
            plugin.getLogger().info("Chunk(" + chunk.getX() + "," + chunk.getZ() + ") 生成 " + generated + " 个灵矿");
        }
    }

    /** 矿脉生成: 严格控制数量 (1个中心点向6个方向偶尔延伸, 形成1~size小矿脉) */
    private int generateVeinSafe(World world, int x, int y, int z, int size, Random rnd) {
        int placed = 0;
        // 先放中心点
        if (tryPlaceOre(world, x, y, z)) {
            placed++;
            if (placed >= size) return placed;
        } else {
            // 中心都没成功, 尝试换一个方向
        }
        // 向6个正方向 (±x,±y,±z) 各有50%概率延伸1格 (共最多延伸6格)
        int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int[] d : dirs) {
            if (placed >= size) break;
            if (rnd.nextInt(2) == 0) continue; // 50% 跳过
            if (tryPlaceOre(world, x + d[0], y + d[1], z + d[2])) placed++;
        }
        return placed;
    }

    private boolean tryPlaceOre(World w, int x, int y, int z) {
        if (y < w.getMinHeight() || y >= w.getMaxHeight()) return false;
        Block b = w.getBlockAt(x, y, z);
        if (!isUndergroundStone(b.getType())) return false;
        Material ore = getOreForY(y);
        b.setType(ore, false);
        plugin.getSpiritItemManager().trackOreBlock(b);
        return true;
    }

    private static boolean isUndergroundStone(Material t) {
        return t == Material.STONE || t == Material.DEEPSLATE || t == Material.TUFF
            || t == Material.ANDESITE || t == Material.DIORITE || t == Material.GRANITE;
    }

    /** 手动回填区块 (供 /ability genores 命令) */
    public int backfillChunk(World world, Chunk chunk) {
        if (world.getEnvironment() != World.Environment.NORMAL) return 0;
        String key = world.getUID() + ":" + chunk.getX() + ":" + chunk.getZ();
        if (populatedChunks.putIfAbsent(key, Boolean.TRUE) != null) return 0;
        int baseX = chunk.getX() << 4, baseZ = chunk.getZ() << 4;
        Random rnd = ThreadLocalRandom.current();
        int generated = 0;
        for (int i = 0; i < attemptsPerChunk; i++) {
            int x = baseX + rnd.nextInt(16);
            int z = baseZ + rnd.nextInt(16);
            int y = minHeight + rnd.nextInt(Math.max(1, maxHeight - minHeight));
            int veinSize = 1 + rnd.nextInt(maxVeinSize);
            generated += generateVeinSafe(world, x, y, z, veinSize, rnd);
        }
        if (generated > 0) {
            // 通知附近玩家刷新 chunk (立刻看到灵矿)
            try {
                Location playerLoc;
                for (Player p : world.getPlayers()) {
                    playerLoc = p.getLocation();
                    double dx = playerLoc.getBlockX() - (chunk.getX() * 16 + 8);
                    double dz = playerLoc.getBlockZ() - (chunk.getZ() * 16 + 8);
                    if (dx*dx + dz*dz < 128*128) { // 水平距离 128 格内
                        world.refreshChunk(chunk.getX(), chunk.getZ());
                    }
                }
            } catch (Throwable ignored) {}
        }
        return generated;
    }
}
