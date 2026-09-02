package com.luoxiaohei.config;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 配置管理器 - config.yml + action.yml (带缓存)
 */
public class ConfigManager {

    private final LuoXiaoHeiPlugin plugin;
    private FileConfiguration actionConfig;
    private File actionFile;
    private final Map<String, Object> actionCache = new HashMap<>();
    private long actionCacheTime = 0;
    private static final long CACHE_TTL_MS = 5000;

    public ConfigManager(LuoXiaoHeiPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        plugin.reloadConfig();
        actionFile = new File(plugin.getDataFolder(), "action.yml");
        if (!actionFile.exists()) plugin.saveResource("action.yml", true);
        reloadActionConfig();
    }

    public void reloadActionConfig() {
        actionConfig = YamlConfiguration.loadConfiguration(actionFile);
        actionCache.clear();
        actionCacheTime = System.currentTimeMillis();
    }

    public FileConfiguration getConfig() { return plugin.getConfig(); }

    public FileConfiguration getActionConfig() {
        if (System.currentTimeMillis() - actionCacheTime > CACHE_TTL_MS) reloadActionConfig();
        return actionConfig;
    }

    public Object getActionValue(String path, Object def) {
        if (actionCache.containsKey(path)) return actionCache.get(path);
        Object val = getActionConfig().get(path, def);
        actionCache.put(path, val);
        return val;
    }

    public int getActionInt(String path, int def) { return ((Number) getActionValue(path, def)).intValue(); }
    public double getActionDouble(String path, double def) { return ((Number) getActionValue(path, def)).doubleValue(); }
    public boolean getActionBool(String path, boolean def) { return (Boolean) getActionValue(path, def); }
    public String getActionString(String path, String def) { return (String) getActionValue(path, def); }
    public ConfigurationSection getActionSection(String section) { return getActionConfig().getConfigurationSection(section); }

    public void saveActionConfig() {
        try { actionConfig.save(actionFile); } catch (IOException e) {
            plugin.getLogger().warning("保存 action.yml 失败: " + e.getMessage());
        }
    }
}
