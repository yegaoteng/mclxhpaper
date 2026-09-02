package com.luoxiaohei.listeners;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;

/**
 * 世界事件监听器 v2.0
 */
public class WorldListener implements Listener {

    private final LuoXiaoHeiPlugin plugin;

    public WorldListener(LuoXiaoHeiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent e) {
        if (!plugin.getConfig().getBoolean("spirit-ore.enabled")) return;
        World w = e.getWorld();
        try {
            boolean registered = w.getPopulators().stream()
                    .anyMatch(p -> p instanceof com.luoxiaohei.ore.OreGenerator);
            if (!registered) w.getPopulators().add(plugin.getOreGenerator());
        } catch (Exception ignored) {}
    }
}
