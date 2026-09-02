package com.luoxiaohei.spirititems;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
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
    private final Material oreBlockType;

    // 放置的灵块位置缓存 (worldUID -> Set<encodedPos>)
    private final Map<UUID, Set<Long>> placedBlocks = new ConcurrentHashMap<>();

    // 被插件追踪的灵矿世界方块 (区分自然海晶灯和灵矿)
    private final Set<Long> trackedOreBlocks = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public SpiritItemManager(LuoXiaoHeiPlugin plugin) {
        this.plugin = plugin;
        this.keyType = new NamespacedKey(plugin, "spirit_type");
        Material mat = Material.matchMaterial(
            plugin.getConfig().getString("spirit-ore.block-type", "SEA_LANTERN"));
        this.oreBlockType = (mat != null) ? mat : Material.SEA_LANTERN;
    }

    // ========== 物品创建 ==========
    public ItemStack createRawOre(int amount) {
        ItemStack item = new ItemStack(Material.RAW_IRON, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§f灵矿石");
        meta.setLore(Arrays.asList("§7蕴含灵气的原矿石", "§7烧炼后可获得灵粒"));
        meta.getPersistentDataContainer().set(keyType, PersistentDataType.STRING, "raw_ore");
        meta.setCustomModelData(10001);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createParticle(int amount) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b灵粒");
        meta.setLore(Arrays.asList("§7提纯后的灵气结晶", "§79个可合成灵块", "§7与西瓜合成灵瓜"));
        meta.getPersistentDataContainer().set(keyType, PersistentDataType.STRING, "particle");
        meta.setCustomModelData(10002);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createSpiritBlock(int amount) {
        ItemStack item = new ItemStack(Material.SEA_LANTERN, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b灵块");
        meta.setLore(Arrays.asList("§7凝聚灵气的发光方块", "§7放置后5格内倍率叠加", "§7最多16层(16倍)"));
        meta.getPersistentDataContainer().set(keyType, PersistentDataType.STRING, "spirit_block");
        meta.setCustomModelData(10003);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createSpiritMelon(int amount) {
        ItemStack item = new ItemStack(Material.GLISTERING_MELON_SLICE, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§d灵瓜");
        meta.setLore(Arrays.asList("§7灵粒与西瓜的融合", "§7食用恢复750灵力"));
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
        if (b.getType() != oreBlockType) return;
        if (!isTrackedOre(b)) return;

        // 灵矿掉落
        e.setDropItems(false);
        untrackOre(b);
        b.getWorld().dropItemNaturally(b.getLocation(), createRawOre(1 + (int)(Math.random() * 2)));
        b.getWorld().spawnParticle(Particle.END_ROD, b.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0.05);
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

    // ========== 事件: 吃灵瓜 ==========
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
}
