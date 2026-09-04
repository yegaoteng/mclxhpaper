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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 灵矿生成器 v2.4.3
 *
 * 核心机制:
 * 1. BlockPopulator: 只在原版 chunk 生成流程中被调用(新chunk第一次创建时)
 *    Paper 1.21 中服务端自带的 chunk generator 会管理 populators
 *    但 register() 里手动 add 的 populator 未必会被调用 → 我们在 ChunkLoadEvent 兜底
 * 2. ChunkLoadEvent: isNewChunk() 时手动调用 populate 保证覆盖
 * 3. 已存在 chunk 回填: 玩家进入时扫描已加载 chunk, 找空洞附近的石头补灵矿
 *
 * 生成规则:
 * - 铁矿材质 (IRON_ORE / DEEPSLATE_IRON_ORE)
 * - 地下 y=-58 ~ 32 (真正的矿洞深度, 避开地表)
 * - 只在地下石头/深板岩中生成 (不在泥土/草方块上)
 * - 每个区块 ~20 次尝试 (铁矿级别)
 */
public class OreGenerator extends BlockPopulator implements Listener {

    private final LuoXiaoHeiPlugin plugin;
    private final Material oreMaterial;
    private final Material deepOreMaterial;
    private final int minHeight;
    private final int maxHeight;
    private final int maxVeinSize;
    private final int attemptsPerChunk;
    // 防止同一 chunk 重复 populate (tracked: 已追踪过该 chunk 的灵矿位置)
    private final ConcurrentHashMap<String, Boolean> populatedChunks = new ConcurrentHashMap<>();

    public OreGenerator(LuoXiaoHeiPlugin plugin) {
        this.plugin = plugin;
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("spirit-ore");
        this.oreMaterial = Material.IRON_ORE;
        this.deepOreMaterial = Material.DEEPSLATE_IRON_ORE;
        // 合理深度: y=-58 ~ 32, 真正的矿洞/深板岩层, 避开地表
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
        for (World w : Bukkit.getWorlds()) {
            try { w.getPopulators().add(this); } catch (Throwable ignored) {}
            plugin.getLogger().info("已向世界 " + w.getName() + " 注册灵矿 populator");
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ===== BlockPopulator 入口 =====
    @Override
    public void populate(World world, Random random, org.bukkit.Chunk source) {
        if (world.getEnvironment() != World.Environment.NORMAL) return;
        String key = world.getUID() + ":" + source.getX() + ":" + source.getZ();
        if (populatedChunks.putIfAbsent(key, Boolean.TRUE) != null) return;

        int baseX = source.getX() << 4;
        int baseZ = source.getZ() << 4;
        final Random rnd = random != null ? random : ThreadLocalRandom.current();

        int generated = 0;
        for (int i = 0; i < attemptsPerChunk; i++) {
            int x = baseX + rnd.nextInt(16);
            int z = baseZ + rnd.nextInt(16);
            int y = minHeight + rnd.nextInt(Math.max(1, maxHeight - minHeight));
            int veinSize = 1 + rnd.nextInt(maxVeinSize);
            generated += generateVein(world, x, y, z, veinSize, rnd);
        }
        if (generated > 0) plugin.getLogger().info("Chunk(" + source.getX() + "," + source.getZ() + ") 生成 " + generated + " 个灵矿");
        else plugin.getLogger().fine("Chunk(" + source.getX() + "," + source.getZ() + ") 未生成灵矿");
    }

    private int generateVein(World world, int x, int y, int z, int size, Random rnd) {
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

    // ===== ChunkLoadEvent 兜底: Paper 的原版 generator 可能不调用自定义 populator =====
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent e) {
        if (e.getWorld().getEnvironment() != World.Environment.NORMAL) return;
        String key = e.getWorld().getUID() + ":" + e.getChunk().getX() + ":" + e.getChunk().getZ();
        if (populatedChunks.containsKey(key)) return;
        if (!e.isNewChunk()) return; // 只对新生成区块触发, 已有区块跳过(避免破坏玩家建筑)

        // Paper 要求 chunk 已 fully generated 才能修改方块
        if (!e.getChunk().isGenerated()) return;
        // 在主线程中生成
        Bukkit.getScheduler().runTask(plugin, () -> populate(e.getWorld(), ThreadLocalRandom.current(), e.getChunk()));
    }

    // ===== 手动对已加载 chunk 回填灵矿 (供管理员命令调用) =====
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
            generated += generateVein(world, x, y, z, veinSize, rnd);
        }
        return generated;
    }
}
