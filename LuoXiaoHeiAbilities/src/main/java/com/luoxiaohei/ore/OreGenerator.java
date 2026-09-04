package com.luoxiaohei.ore;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 灵矿生成器 v2.4.4
 *
 * 核心修复 v2.4.3→v2.4.4: 不再依赖 BlockPopulator 接口
 * - Moonrise/Paper 的 chunk 系统会在异步线程调用 BlockPopulator.populate()
 * - 但 Bukkit 要求所有方块操作必须在主线程
 * - 结果: 从 BlockPopulator.populate() 中移除所有方块操作
 * - 改为: 全部靠 ChunkLoadEvent + isNewChunk() + runTask 主线程执行
 *
 * 生成规则:
 * - 铁矿材质 (IRON_ORE / DEEPSLATE_IRON_ORE)
 * - 地下 y=-58 ~ 32 (真正的矿洞深度, 避开地表)
 * - 只在地下石头/深板岩中生成
 * - 每个区块 ~20 次尝试 (铁矿级别)
 */
public class OreGenerator implements Listener {

    private final LuoXiaoHeiPlugin plugin;
    private final Material oreMaterial;
    private final Material deepOreMaterial;
    private final int minHeight;
    private final int maxHeight;
    private final int maxVeinSize;
    private final int attemptsPerChunk;
    // 防止同一 chunk 重复生成
    private final ConcurrentHashMap<String, Boolean> populatedChunks = new ConcurrentHashMap<>();

    public OreGenerator(LuoXiaoHeiPlugin plugin) {
        this.plugin = plugin;
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("spirit-ore");
        this.oreMaterial = Material.IRON_ORE;
        this.deepOreMaterial = Material.DEEPSLATE_IRON_ORE;
        this.minHeight = cfg == null ? -58 : cfg.getInt("min-height", -58);
        this.maxHeight = cfg == null ? 32 : cfg.getInt("max-height", 32);
        this.maxVeinSize = cfg == null ? 9 : cfg.getInt("max-vein-size", 9);
        this.attemptsPerChunk = cfg == null ? 20 : cfg.getInt("attempts-per-chunk", 20);
        plugin.getLogger().info("灵矿生成器初始化: y=" + minHeight + "~" + maxHeight
                + ", 每区块" + attemptsPerChunk + "次尝试, 矿脉大小1~" + maxVeinSize);
    }

    public Material getOreMaterial() { return oreMaterial; }
    public Material getDeepOreMaterial() { return deepOreMaterial; }

    private Material getOreForY(int y) { return y < 0 ? deepOreMaterial : oreMaterial; }

    public void register() {
        // ⚠️ 不再注册 BlockPopulator! Moonrise 会在异步线程调用它, 导致方块操作崩溃
        // 全部靠 ChunkLoadEvent 主线程执行
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("灵矿生成器已注册 (ChunkLoadEvent 主线程模式)");
    }

    // ===== ChunkLoadEvent: 新区块加载时在主线程生成灵矿 =====
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent e) {
        if (e.getWorld().getEnvironment() != World.Environment.NORMAL) return;
        String key = e.getWorld().getUID() + ":" + e.getChunk().getX() + ":" + e.getChunk().getZ();
        if (populatedChunks.containsKey(key)) return; // 已处理过
        if (!e.isNewChunk()) return; // 只对新生成区块触发

        if (!e.getChunk().isGenerated()) return;

        // 回到主线程执行 (绝对安全)
        Bukkit.getScheduler().runTask(plugin, () -> {
            // putIfAbsent 返回null = 之前不存在, 成功抢占, 允许生成
            Boolean already = populatedChunks.putIfAbsent(key, Boolean.TRUE);
            if (already != null) return; // 已被其他线程处理
            try {
                doPopulate(e.getWorld(), e.getChunk());
            } catch (Exception ex) {
                plugin.getLogger().warning("灵矿生成异常 Chunk(" + e.getChunk().getX() + "," + e.getChunk().getZ() + "): " + ex.getMessage());
            }
        });
    }

    /** 主线程安全的灵矿生成 */
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
        if (generated > 0) plugin.getLogger().info("Chunk(" + chunk.getX() + "," + chunk.getZ() + ") 生成 " + generated + " 个灵矿");
    }

    /** 已在主线程, 安全操作方块 */
    private int generateVeinSafe(World world, int x, int y, int z, int size, Random rnd) {
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
            if (isUndergroundStone(b.getType())) {
                Material ore = getOreForY(by);
                b.setType(ore, false);
                plugin.getSpiritItemManager().trackOreBlock(b);
                placed++;
            }
        }
        return placed;
    }

    private static boolean isUndergroundStone(Material t) {
        return t == Material.STONE || t == Material.DEEPSLATE || t == Material.TUFF
            || t == Material.ANDESITE || t == Material.DIORITE || t == Material.GRANITE;
    }

    // ===== 手动对已加载 chunk 回填灵矿 (供 /ability genores 命令调用) =====
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
        return generated;
    }
}
