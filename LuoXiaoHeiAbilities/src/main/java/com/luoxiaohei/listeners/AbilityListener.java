package com.luoxiaohei.listeners;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import com.luoxiaohei.abilities.AbilityType;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.metadata.MetadataValue;

import java.util.UUID;

/**
 * 能力相关全局监听器 v2.0
 * - 技能击杀获取修炼经验
 * - 领域内伤害加成
 */
public class AbilityListener implements Listener {

    private final LuoXiaoHeiPlugin plugin;

    public AbilityListener(LuoXiaoHeiPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 领域内伤害加成
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDomainDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (plugin.getPlayerDataManager().getData(p).getAbilityType() != AbilityType.SPACE) return;
        for (MetadataValue mv : p.getMetadata("domain-owner")) {
            if (mv.getOwningPlugin() == plugin) {
                double boost = Double.parseDouble(mv.asString());
                if (boost > 0) e.setDamage(e.getDamage() * (1 + boost));
            }
        }
    }

    /**
     * 技能击杀获取修炼经验
     */
    @EventHandler
    public void onEntityKill(org.bukkit.event.entity.EntityDeathEvent e) {
        LivingEntity entity = e.getEntity();
        // 检查技能伤害标记
        for (MetadataValue mv : entity.getMetadata("skill_damage")) {
            if (mv.getOwningPlugin() != plugin) continue;
            String data = mv.asString();
            String[] parts = data.split(":");
            if (parts.length < 2) continue;
            try {
                UUID killerId = UUID.fromString(parts[0]);
                long time = Long.parseLong(parts[1]);
                if (System.currentTimeMillis() - time < 5000) { // 5秒内
                    Player killer = Bukkit.getPlayer(killerId);
                    if (killer != null && killer.isOnline()) {
                        plugin.getCultivationManager().grantKillXp(killer);
                    }
                }
            } catch (Exception ignored) {}
            break;
        }
    }
}
