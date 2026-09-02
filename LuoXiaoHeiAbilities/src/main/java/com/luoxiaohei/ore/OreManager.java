package com.luoxiaohei.ore;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * 灵矿管理器 v2.0
 * - 不再提供世界灵矿直接恢复 (改用灵块系统)
 * - 保留灵矿位置缓存用于挖矿检测
 * - 灵气恢复由灵块系统(SpiritItemManager.getMultiplier)提供
 */
public class OreManager implements org.bukkit.event.Listener {

    private final LuoXiaoHeiPlugin plugin;
    private BukkitTask regenTask;

    public OreManager(LuoXiaoHeiPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 启动灵力恢复调度器
     * 恢复量 = 修炼阶数恢复速度 × 灵块倍率
     */
    public void startRegenScheduler() {
        if (regenTask != null) regenTask.cancel();
        regenTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.isOnline()) continue;
                var d = plugin.getPlayerDataManager().getData(p);
                int baseRegen = plugin.getCultivationManager().getRegenPerSecond(d.getCultivationLevel());
                // 灵块倍率 (1=无灵块, 2=1块, 最高16)
                int multiplier = plugin.getSpiritItemManager().getMultiplier(p.getLocation());
                int totalRegen = baseRegen * multiplier;
                if (totalRegen > 0) {
                    plugin.getPlayerDataManager().addSpiritual(p, totalRegen);
                }
            }
        }, 20L, 20); // 每秒
    }

    /**
     * 从缓存移除方块 (灵矿被挖时)
     */
    public void removeFromCache(Block b) {
        // 委托给SpiritItemManager处理追踪移除
        plugin.getSpiritItemManager().untrackOre(b);
    }
}
