package com.luoxiaohei.data;

import com.luoxiaohei.abilities.AbilityType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家数据 - 含灵力/能力/修炼/技能索引/按键绑定
 * 修炼阶数独立于能力类型, 切换能力不影响阶数
 */
public class PlayerData {

    private final UUID uuid;
    private final String name;

    // 灵力
    private int spiritual;
    private int maxSpiritual;

    // 能力
    private AbilityType abilityType;
    private boolean enabled; // 技能开关 (右ctrl切换)

    // 修炼系统 (独立于能力类型)
    private int cultivationLevel;
    private int cultivationXp;

    // 当前选中的技能索引 (按能力类型的技能列表)
    private int currentSkillIndex;

    // 按键绑定
    private String bindCycle;
    private String bindToggle;

    // 冷却/充能
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<String, Integer> charges = new HashMap<>();
    private final Map<String, Long> lastRecharge = new HashMap<>();

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.cultivationLevel = 1;
        this.cultivationXp = 0;
        this.currentSkillIndex = 0;
        this.bindCycle = "SNEAK";
        this.bindToggle = "SWAP_HANDS";
    }

    // === Getters/Setters ===
    public UUID getUuid() { return uuid; }
    public String getName() { return name; }

    public int getSpiritual() { return spiritual; }
    public void setSpiritual(int v) { this.spiritual = Math.max(0, v); }

    public int getMaxSpiritual() { return maxSpiritual; }
    public void setMaxSpiritual(int v) { this.maxSpiritual = v; }

    public AbilityType getAbilityType() { return abilityType; }
    public void setAbilityType(AbilityType v) { this.abilityType = v; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }

    public int getCultivationLevel() { return cultivationLevel; }
    public void setCultivationLevel(int v) { this.cultivationLevel = v; }

    public int getCultivationXp() { return cultivationXp; }
    public void setCultivationXp(int v) { this.cultivationXp = v; }

    public int getCurrentSkillIndex() { return currentSkillIndex; }
    public void setCurrentSkillIndex(int v) { this.currentSkillIndex = v; }

    public String getBindCycle() { return bindCycle; }
    public void setBindCycle(String v) { this.bindCycle = v; }

    public String getBindToggle() { return bindToggle; }
    public void setBindToggle(String v) { this.bindToggle = v; }

    public Map<String, Long> getCooldowns() { return cooldowns; }
    public void setCooldown(String skill, long t) { cooldowns.put(skill, t); }

    public Map<String, Integer> getCharges() { return charges; }
    public void setCharges(String skill, int c) { charges.put(skill, c); }

    public Map<String, Long> getLastRecharge() { return lastRecharge; }
}
