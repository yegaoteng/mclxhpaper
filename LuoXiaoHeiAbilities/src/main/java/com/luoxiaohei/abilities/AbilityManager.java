package com.luoxiaohei.abilities;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import com.luoxiaohei.config.ConfigManager;
import com.luoxiaohei.config.MessagesManager;
import com.luoxiaohei.data.PlayerData;
import com.luoxiaohei.data.PlayerDataManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;

/**
 * 能力管理器 v2.0
 */
public class AbilityManager {

    private final LuoXiaoHeiPlugin plugin;
    private final ConfigManager cfg;
    private final PlayerDataManager dm;
    private final MessagesManager msg;
    private final Map<AbilityType, BaseAbility> abilities = new EnumMap<>(AbilityType.class);

    public AbilityManager(LuoXiaoHeiPlugin plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
        this.dm = plugin.getPlayerDataManager();
        this.msg = plugin.getMessagesManager();
    }

    public void registerAbility(AbilityType type, BaseAbility ability) { abilities.put(type, ability); }
    public BaseAbility getAbility(AbilityType type) { return abilities.get(type); }

    public boolean setPlayerAbility(Player player, AbilityType type) {
        if (!isAbilityEnabled(type)) return false;
        PlayerData data = dm.getData(player);
        data.setAbilityType(type);
        data.setEnabled(true);
        // 初始化充能
        if (type == AbilityType.SPACE) {
            int max = cfg.getActionInt("space.space-teleport.max-charges", 3);
            dm.getData(player).setCharges("space-teleport", max);
        }
        player.sendMessage(msg.getPrefixed("ability-set",
            "ability", type.getChinese(), "ability_color", type.getColor()));
        return true;
    }

    public void toggleAbility(Player player, boolean enable) {
        PlayerData data = dm.getData(player);
        data.setEnabled(enable);
        player.sendMessage(enable ? msg.getPrefixed("ability-toggle-on") : msg.getPrefixed("ability-toggle-off"));
    }

    public boolean isAbilityEnabled(AbilityType type) {
        if (type == AbilityType.NONE) return true;
        return cfg.getConfig().getBoolean("abilities." + type.getConfigKey() + "-enabled", true);
    }

    public String preCheck(Player player, String skill, int cost, int cooldownSec) {
        PlayerData data = dm.getData(player);
        if (!data.isEnabled()) return msg.get("ability-toggle-off");
        if (dm.getCooldownRemain(player, skill) > 0)
            return msg.getPrefixed("skill-cooldown", "seconds",
                String.valueOf(dm.getCooldownRemain(player, skill) / 1000 + 1));
        if (data.getSpiritual() < cost)
            return msg.getPrefixed("skill-no-spirit", "cost", String.valueOf(cost));
        return null;
    }

    public void applyCost(Player player, String skill, int cost, int cooldownSec) {
        dm.consumeSpiritual(player, cost);
        if (cooldownSec > 0) dm.setCooldown(player, skill, cooldownSec);
    }
}
