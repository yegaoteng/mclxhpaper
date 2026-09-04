package com.luoxiaohei.listeners;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;

/**
 * 世界事件监听器 v2.4.4
 * 不再注册 BlockPopulator (Moonrise 会在异步线程调用它, 导致方块操作崩溃)
 * 灵矿生成全部靠 ChunkLoadEvent 主线程执行
 */
public class WorldListener implements Listener {

    private final LuoXiaoHeiPlugin plugin;

    public WorldListener(LuoXiaoHeiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent e) {
        // v2.4.4: 不再向世界注册 BlockPopulator
        // Moonrise/Paper 的 chunk 系统会在异步线程调用它, 导致方块操作崩溃
        // 灵矿生成完全由 ChunkLoadEvent + runTask 主线程处理
    }
}
