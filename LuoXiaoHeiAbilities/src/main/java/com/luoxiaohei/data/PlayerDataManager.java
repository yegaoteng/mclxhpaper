package com.luoxiaohei.data;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import com.luoxiaohei.abilities.AbilityType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家数据管理器 - 线程安全缓存 + YAML持久化
 */
public class PlayerDataManager {

    private final LuoXiaoHeiPlugin plugin;
    private final File dataFolder;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(LuoXiaoHeiPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) dataFolder.mkdirs();
    }

    public PlayerData getData(Player player) {
        return getData(player.getUniqueId(), player.getName());
    }

    public PlayerData getData(UUID uuid) {
        return getData(uuid, "");
    }

    public PlayerData getData(UUID uuid, String name) {
        PlayerData data = cache.get(uuid);
        if (data == null) {
            data = load(uuid);
            if (data == null) {
                data = new PlayerData(uuid, name);
                // 初始化修炼1阶属性
                initCultivation(data, 1);
            }
            cache.put(uuid, data);
        }
        return data;
    }

    private void initCultivation(PlayerData data, int level) {
        ConfigurationSection lvl = plugin.getConfig().getConfigurationSection("cultivation.levels." + level);
        if (lvl != null) {
            data.setCultivationLevel(level);
            data.setMaxSpiritual(lvl.getInt("max-spiritual", 1000));
            data.setSpiritual(data.getMaxSpiritual());
        }
    }

    private PlayerData load(UUID uuid) {
        File file = new File(dataFolder, uuid + ".yml");
        if (!file.exists()) return null;
        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            PlayerData data = new PlayerData(uuid, cfg.getString("name", ""));
            data.setSpiritual(cfg.getInt("spiritual", 0));
            data.setMaxSpiritual(cfg.getInt("max-spiritual", 1000));
            data.setAbilityType(AbilityType.fromString(cfg.getString("ability-type", "NONE")));
            data.setEnabled(cfg.getBoolean("enabled", false));
            data.setCultivationLevel(cfg.getInt("cultivation.level", 1));
            data.setCultivationXp(cfg.getInt("cultivation.xp", 0));
            data.setCurrentSkillIndex(cfg.getInt("current-skill", 0));
            data.setBindCycle(cfg.getString("binds.cycle", "SNEAK"));
            data.setBindToggle(cfg.getString("binds.toggle", "SWAP_HANDS"));

            ConfigurationSection cd = cfg.getConfigurationSection("cooldowns");
            if (cd != null) for (String k : cd.getKeys(false)) data.setCooldown(k, cd.getLong(k));
            ConfigurationSection ch = cfg.getConfigurationSection("charges");
            if (ch != null) for (String k : ch.getKeys(false)) data.setCharges(k, ch.getInt(k));
            return data;
        } catch (Exception e) {
            plugin.getLogger().warning("加载玩家数据失败 " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    public void save(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) return;
        File file = new File(dataFolder, uuid + ".yml");
        FileConfiguration cfg = new YamlConfiguration();
        cfg.set("name", data.getName());
        cfg.set("spiritual", data.getSpiritual());
        cfg.set("max-spiritual", data.getMaxSpiritual());
        cfg.set("ability-type", data.getAbilityType().name());
        cfg.set("enabled", data.isEnabled());
        cfg.set("cultivation.level", data.getCultivationLevel());
        cfg.set("cultivation.xp", data.getCultivationXp());
        cfg.set("current-skill", data.getCurrentSkillIndex());
        cfg.set("binds.cycle", data.getBindCycle());
        cfg.set("binds.toggle", data.getBindToggle());
        for (Map.Entry<String, Long> e : data.getCooldowns().entrySet())
            cfg.set("cooldowns." + e.getKey(), e.getValue());
        for (Map.Entry<String, Integer> e : data.getCharges().entrySet())
            cfg.set("charges." + e.getKey(), e.getValue());
        try { cfg.save(file); } catch (IOException e) {
            plugin.getLogger().warning("保存玩家数据失败 " + uuid + ": " + e.getMessage());
        }
    }

    public void saveAll() { cache.keySet().forEach(this::save); }
    public void loadAllOnline() { Bukkit.getOnlinePlayers().forEach(this::getData); }

    // === 灵力操作 ===
    public boolean consumeSpiritual(Player p, int amount) {
        PlayerData d = getData(p);
        if (d.getSpiritual() < amount) return false;
        d.setSpiritual(d.getSpiritual() - amount);
        return true;
    }

    public void addSpiritual(Player p, int amount) {
        PlayerData d = getData(p);
        d.setSpiritual(Math.min(d.getMaxSpiritual(), d.getSpiritual() + amount));
    }

    public void addSpiritual(UUID uuid, int amount) {
        PlayerData d = getData(uuid);
        if (d != null) d.setSpiritual(Math.min(d.getMaxSpiritual(), d.getSpiritual() + amount));
    }

    // === 冷却 ===
    public long getCooldownRemain(Player p, String skill) {
        long end = getData(p).getCooldowns().getOrDefault(skill, 0L);
        long r = end - System.currentTimeMillis();
        return r > 0 ? r : 0;
    }

    public boolean isOnCooldown(Player p, String skill) { return getCooldownRemain(p, skill) > 0; }

    public void setCooldown(Player p, String skill, int seconds) {
        getData(p).setCooldown(skill, System.currentTimeMillis() + seconds * 1000L);
    }

    // === 充能 ===
    public int getCharges(Player p, String skill, int max) {
        Integer c = getData(p).getCharges().get(skill);
        return c == null ? max : c;
    }

    public void useCharge(Player p, String skill, int max) {
        getData(p).setCharges(skill, Math.max(0, getCharges(p, skill, max) - 1));
    }

    public void rechargeTick(Player p, String skill, int max, int chargeTimeSec) {
        PlayerData d = getData(p);
        String lk = skill + "_lastRecharge";
        long last = d.getLastRecharge().getOrDefault(lk, System.currentTimeMillis());
        int c = getCharges(p, skill, max);
        if (c < max && System.currentTimeMillis() - last >= chargeTimeSec * 1000L) {
            d.setCharges(skill, c + 1);
            d.getLastRecharge().put(lk, System.currentTimeMillis());
        } else if (c >= max) {
            d.getLastRecharge().put(lk, System.currentTimeMillis());
        }
    }

    // === 修炼经验 ===
    public void addXp(Player p, int amount) {
        PlayerData d = getData(p);
        d.setCultivationXp(d.getCultivationXp() + amount);
    }

    public void addXp(UUID uuid, int amount) {
        PlayerData d = getData(uuid);
        if (d != null) d.setCultivationXp(d.getCultivationXp() + amount);
    }
}
