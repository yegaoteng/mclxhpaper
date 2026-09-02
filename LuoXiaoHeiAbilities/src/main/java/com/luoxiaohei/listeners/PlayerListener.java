package com.luoxiaohei.listeners;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import com.luoxiaohei.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * 玩家事件监听器 v2.0
 */
public class PlayerListener implements Listener {

    private final LuoXiaoHeiPlugin plugin;

    public PlayerListener(LuoXiaoHeiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.getPlayerDataManager().getData(p);
        // 应用修炼阶数属性
        plugin.getCultivationManager().applyLevelStats(p, d.getCultivationLevel());
        // 显示HUD
        plugin.getHudManager().show(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.getPlayerDataManager().save(p.getUniqueId());
        plugin.getHudManager().hide(p);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.getPlayerDataManager().getData(p);
        int keep = plugin.getConfig().getInt("spiritual.death-keep-percent", 50);
        d.setSpiritual((int)(d.getMaxSpiritual() * keep / 100.0));
        // 重生后重新应用属性
        plugin.getCultivationManager().applyLevelStats(p, d.getCultivationLevel());
    }
}
