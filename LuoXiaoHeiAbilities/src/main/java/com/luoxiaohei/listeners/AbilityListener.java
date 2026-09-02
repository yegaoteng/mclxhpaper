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
     * 击杀生物获取修炼经验 (无论技能开关与否, 只要玩家有系能力即获经验)
     */
    @EventHandler
    public void onEntityKill(org.bukkit.event.entity.EntityDeathEvent e) {
        LivingEntity entity = e.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;
        com.luoxiaohei.data.PlayerData d = plugin.getPlayerDataManager().getData(killer);
        // 有系能力即可获得经验 (不检查技能开关)
        if (d.getAbilityType() == AbilityType.NONE) return;
        plugin.getCultivationManager().grantKillXp(killer);
    }
}
