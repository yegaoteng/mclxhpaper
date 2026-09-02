package com.luoxiaohei.config;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 消息管理器 - 加载 messages.yml
 */
public class MessagesManager {

    private final LuoXiaoHeiPlugin plugin;
    private FileConfiguration msgConfig;
    private final Map<String, String> cache = new HashMap<>();

    public MessagesManager(LuoXiaoHeiPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", true);
        msgConfig = YamlConfiguration.loadConfiguration(file);
        cache.clear();
    }

    /**
     * 获取消息并替换占位符
     */
    public String get(String key, String... pairs) {
        String msg = cache.computeIfAbsent(key, k -> msgConfig.getString(k, k));
        if (pairs.length >= 2) {
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                msg = msg.replace("{" + pairs[i] + "}", pairs[i + 1]);
            }
        }
        return msg;
    }

    /**
     * 获取带前缀的消息
     */
    public String getPrefixed(String key, String... pairs) {
        return get("prefix") + get(key, pairs);
    }
}
