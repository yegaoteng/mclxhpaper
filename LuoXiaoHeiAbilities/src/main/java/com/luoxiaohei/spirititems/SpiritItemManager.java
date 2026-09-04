package com.luoxiaohei.spirititems;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 灵矿/灵粒/灵块/灵瓜 物品系统
 *
 * 流程:
 * 1. 挖灵矿(海晶灯方块, 被插件追踪) → 灵矿石(raw)
 * 2. 烧炼灵矿石 → 2x 灵粒(particle)
 * 3. 9x灵粒 → 1x灵块(block) (工作台)
 * 4. 灵粒 + 西瓜片 → 灵瓜(melon) (工作台, 闪烁西瓜片改名)
 * 5. 吃灵瓜 → +750灵力
 * 6. 放置灵块 → 5格内倍率叠加(最多16层=16倍)
 */
public class SpiritItemManager implements Listener {

    private final LuoXiaoHeiPlugin plugin;
    private final NamespacedKey keyType;
    // 灵矿方块类型 (铁矿材质 + 深板岩铁矿)
    private final Material oreBlockType;
    private final Material deepOreBlockType;
    // 灵块方块类型 (玩家放置的灵块, 用于倍率叠加)
    private final Material spiritBlockType;

    // 放置的灵块位置缓存 (worldUID -> Set<encodedPos>)
    private final Map<UUID, Set<Long>> placedBlocks = new ConcurrentHashMap<>();

    // 被插件追踪的灵矿世界方块 (区分普通铁矿和灵矿)
    private final Set<Long> trackedOreBlocks = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public SpiritItemManager(LuoXiaoHeiPlugin plugin) {
        this.plugin = plugin;
        this.keyType = new NamespacedKey(plugin, "spirit_type");
        this.oreBlockType = Material.SHROOMLIGHT;
        this.deepOreBlockType = Material.SHROOMLIGHT;
        this.spiritBlockType = Material.SEA_LANTERN;
    }

    // ========== 物品创建 ==========
    /** 灵矿 (挖灵矿掉落) — 载体:生铁块 RAW_IRON, CustomModelData=10001,
     *  资源包需覆盖 assets/minecraft/models/item/raw_iron.json 添加 predicate custom_model_data=10001 */
    public ItemStack createRawOre(int amount) {
        ItemStack item = new ItemStack(Material.RAW_IRON, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§f灵矿");
        meta.setLore(Arrays.asList("§7蕴含灵气的矿石", "§7烧炼后可获得灵粒", "§8[需要资源包激活自定义贴图]"));
        meta.getPersistentDataContainer().set(keyType, PersistentDataType.STRING, "raw_ore");
        meta.setCustomModelData(10001);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    /** 灵粒 — 载体:紫水晶碎片 AMETHYST_SHARD, CustomModelData=10002
     *  资源包覆盖 assets/minecraft/models/item/amethyst_shard.json 添加 predicate */
    public ItemStack createParticle(int amount) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b灵粒");
        meta.setLore(Arrays.asList("§7提纯后的灵气结晶", "§79个可合成灵块", "§7与西瓜合成灵瓜", "§8[需要资源包激活自定义贴图]"));
        meta.getPersistentDataContainer().set(keyType, PersistentDataType.STRING, "particle");
        meta.setCustomModelData(10002);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    /** 灵块物品 (放置后 = 灵块方块倍率来源) — 载体:海晶灯 SEA_LANTERN, CustomModelData=10003
     *  资源包覆盖 assets/minecraft/models/item/sea_lantern.json 添加 predicate */
    public ItemStack createSpiritBlock(int amount) {
        ItemStack item = new ItemStack(Material.SEA_LANTERN, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b灵块");
        meta.setLore(Arrays.asList("§7凝聚灵气的发光方块", "§7放置后5格内倍率叠加", "§7最多16层(16倍)", "§8[需要资源包激活自定义贴图]"));
        meta.getPersistentDataContainer().set(keyType, PersistentDataType.STRING, "spirit_block");
        meta.setCustomModelData(10003);
        item.setItemMeta(meta);
        return item;
    }

    /** 灵瓜 — 载体:闪烁西瓜片 GLISTERING_MELON_SLICE, CustomModelData=10004
     *  资源包覆盖 assets/minecraft/models/item/glistering_melon_slice.json 添加 predicate */
    public ItemStack createSpiritMelon(int amount) {
        ItemStack item = new ItemStack(Material.GLISTERING_MELON_SLICE, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§d灵瓜");
        meta.setLore(Arrays.asList("§7灵粒与西瓜的融合", "§7食用恢复750灵力", "§8[需要资源包激活自定义贴图]"));
        meta.getPersistentDataContainer().set(keyType, PersistentDataType.STRING, "spirit_melon");
        meta.setCustomModelData(10004);
        item.setItemMeta(meta);
        return item;
    }

    // ========== 物品判断 ==========
    public String getSpiritType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keyType, PersistentDataType.STRING);
    }

    public boolean isRawOre(ItemStack item) { return "raw_ore".equals(getSpiritType(item)); }
    public boolean isParticle(ItemStack item) { return "particle".equals(getSpiritType(item)); }
    public boolean isSpiritBlock(ItemStack item) { return "spirit_block".equals(getSpiritType(item)); }
    public boolean isSpiritMelon(ItemStack item) { return "spirit_melon".equals(getSpiritType(item)); }

    // ========== 注册配方 ==========
    public void registerRecipes() {
        // 9x灵粒 → 1x灵块
        ShapedRecipe blockRecipe = new ShapedRecipe(new NamespacedKey(plugin, "spirit_block"), createSpiritBlock(1));
        blockRecipe.shape("AAA", "AAA", "AAA");
        blockRecipe.setIngredient('A', Material.AMETHYST_SHARD);
        // Note: Bukkit shaped recipe doesn't check PDC, so we need custom handling
        // We'll use a custom approach: listen to CraftItemEvent
        try { Bukkit.addRecipe(blockRecipe); } catch (Exception ignored) {}

        // 灵粒 + 西瓜 → 灵瓜 (无序配方)
        var melonRecipe = new org.bukkit.inventory.ShapelessRecipe(
            new NamespacedKey(plugin, "spirit_melon"), createSpiritMelon(1));
        melonRecipe.addIngredient(1, Material.AMETHYST_SHARD);
        melonRecipe.addIngredient(1, Material.MELON_SLICE);
        try { Bukkit.addRecipe(melonRecipe); } catch (Exception ignored) {}
    }

    // ========== 灵矿追踪 ==========
    public void trackOreBlock(Block b) {
        trackedOreBlocks.add(encodePos(b.getX(), b.getY(), b.getZ()));
    }

    public boolean isTrackedOre(Block b) {
        return trackedOreBlocks.contains(encodePos(b.getX(), b.getY(), b.getZ()));
    }

    public void untrackOre(Block b) {
        trackedOreBlocks.remove(encodePos(b.getX(), b.getY(), b.getZ()));
    }

    // ========== 事件: 挖矿 ==========
    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        // 检查是否为灵矿 (铁矿材质 + 被追踪)
        if (b.getType() != oreBlockType && b.getType() != deepOreBlockType) return;
        if (!isTrackedOre(b)) return;

        // 灵矿掉落
        e.setDropItems(false);
        untrackOre(b);
        b.getWorld().dropItemNaturally(b.getLocation(), createRawOre(1 + (int)(Math.random() * 2)));
        b.getWorld().spawnParticle(Particle.END_ROD, b.getLocation().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0.1);
        e.getPlayer().playSound(b.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1, 1.5f);

        // 从缓存移除
        plugin.getOreManager().removeFromCache(b);
    }

    // ========== 事件: 放置灵块 ==========
    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        ItemStack hand = e.getItemInHand();
        if (!isSpiritBlock(hand)) return;
        Block b = e.getBlockPlaced();
        UUID wid = b.getWorld().getUID();
        placedBlocks.computeIfAbsent(wid, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
            .add(encodePos(b.getX(), b.getY(), b.getZ()));
        int mult = getMultiplier(b.getLocation());
        e.getPlayer().sendMessage(plugin.getMessagesManager().getPrefixed("spirit-block-place",
            "multiplier", String.valueOf(mult)));
    }

    // ========== 事件: 破坏灵块 ==========
    @EventHandler
    public void onSpiritBlockBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.SEA_LANTERN) return;
        UUID wid = b.getWorld().getUID();
        Set<Long> set = placedBlocks.get(wid);
        if (set == null) return;
        long key = encodePos(b.getX(), b.getY(), b.getZ());
        if (!set.contains(key)) return;
        set.remove(key);
        e.setDropItems(false);
        b.getWorld().dropItemNaturally(b.getLocation(), createSpiritBlock(1));
        e.getPlayer().sendMessage(plugin.getMessagesManager().getPrefixed("spirit-block-break"));
    }

    // ========== 事件: 烧炼 ==========
    @EventHandler
    public void onSmelt(FurnaceSmeltEvent e) {
        ItemStack source = e.getSource();
        if (!isRawOre(source)) return;
        // 灵矿石烧炼 → 2灵粒
        e.setResult(createParticle(2));
    }

    // ========== 事件: 右键吃灵瓜 ==========
    // 闪烁西瓜片无FoodComponent, 原版不可食用, 这里手动处理右键食用
    @EventHandler(priority = EventPriority.LOWEST)
    public void onRightClickMelon(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isSpiritMelon(hand)) return;
        // 优先级LOWEST: 先cancel, 防止能力监听器触发技能
        e.setCancelled(true);
        // 消耗1个灵瓜
        if (hand.getAmount() > 1) hand.setAmount(hand.getAmount() - 1);
        else p.getInventory().setItemInMainHand(null);
        // 恢复灵力
        int restore = plugin.getConfig().getInt("spiritual.spirit-melon-restore", 750);
        plugin.getPlayerDataManager().addSpiritual(p, restore);
        p.sendMessage(plugin.getMessagesManager().getPrefixed("spirit-melon-eat"));
        // 食用音效+粒子
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BURP, 1, 1);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getEyeLocation(), 15, 0.3, 0.3, 0.3, 0.05);
    }

    // ========== 事件: 原版食用(兼容其他食物机制) ==========
    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        if (!isSpiritMelon(e.getItem())) return;
        Player p = e.getPlayer();
        int restore = plugin.getConfig().getInt("spiritual.spirit-melon-restore", 750);
        plugin.getPlayerDataManager().addSpiritual(p, restore);
        p.sendMessage(plugin.getMessagesManager().getPrefixed("spirit-melon-eat"));
    }

    // ========== 灵块倍率计算 ==========
    public int getMultiplier(org.bukkit.Location loc) {
        UUID wid = loc.getWorld().getUID();
        Set<Long> set = placedBlocks.get(wid);
        if (set == null || set.isEmpty()) return 1;
        int radius = plugin.getConfig().getInt("spiritual.spirit-block-radius", 5);
        int maxLayers = plugin.getConfig().getInt("spiritual.spirit-block-max-layers", 16);
        int px = loc.getBlockX(), py = loc.getBlockY(), pz = loc.getBlockZ();
        int count = 0;
        for (Long key : set) {
            int bx = decodeX(key), by = decodeY(key), bz = decodeZ(key);
            long dx = bx - px, dy = by - py, dz = bz - pz;
            if (dx*dx + dy*dy + dz*dz <= (long)radius*radius) count++;
        }
        // 倍率 = min(1 + count, maxLayers) → 1块=2x, 15块=16x
        return Math.min(1 + count, maxLayers);
    }

    // ========== 位置编码 ==========
    private static long encodePos(int x, int y, int z) {
        return (((long)(x & 0x3FFFFFF) << 38) | ((long)(y & 0xFFF) << 26) | (long)(z & 0x3FFFFFF));
    }
    private static int decodeX(long key) { return (int)(key >> 38); }
    private static int decodeY(long key) { return (int)((key >> 26) & 0xFFF); }
    private static int decodeZ(long key) { return (int)(key & 0x3FFFFFF); }

    // ========== 灵矿发光粒子调度器 ==========
    // 模拟附魔光效: 在被追踪的灵矿上方生成 END_ROD 闪光
    private org.bukkit.scheduler.BukkitTask glowTask;

    public void startGlowScheduler() {
        if (glowTask != null) glowTask.cancel();
        int interval = plugin.getConfig().getInt("performance.ore-glow-interval", 30);
        glowTask = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (trackedOreBlocks.isEmpty()) return;
            // 遍历在线玩家附近的灵矿, 生成发光粒子
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                org.bukkit.Location pLoc = p.getLocation();
                int pchX = pLoc.getBlockX() >> 4;
                int pchZ = pLoc.getBlockZ() >> 4;
                // 仅检查玩家周围3区块内的灵矿
                int checked = 0;
                for (Long key : trackedOreBlocks) {
                    if (checked++ > 200) break; // 限制每次检查数量
                    int bx = decodeX(key), by = decodeY(key), bz = decodeZ(key);
                    int chX = bx >> 4, chZ = bz >> 4;
                    if (Math.abs(chX - pchX) > 3 || Math.abs(chZ - pchZ) > 3) continue;
                    // 距离检查 (32格内才显示粒子)
                    long dx = bx - pLoc.getBlockX();
                    long dy = by - pLoc.getBlockY();
                    long dz = bz - pLoc.getBlockZ();
                    if (dx*dx + dy*dy + dz*dz > 1024) continue;
                    org.bukkit.World w = pLoc.getWorld();
                    if (w == null) continue;
                    // 不验证方块类型(性能), 只显示粒子
                    w.spawnParticle(Particle.END_ROD,
                        bx + 0.5, by + 0.5, bz + 0.5, 1, 0.15, 0.15, 0.15, 0.02);
                }
            }
        }, 40L, interval);
    }

    public void stopGlowScheduler() {
        if (glowTask != null) { glowTask.cancel(); glowTask = null; }
    }
}
