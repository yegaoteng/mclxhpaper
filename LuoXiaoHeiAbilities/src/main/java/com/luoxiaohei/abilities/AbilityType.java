package com.luoxiaohei.abilities;

/**
 * 能力类型枚举
 */
public enum AbilityType {
    NONE("无", "§7", ""),
    METAL("金系", "§e", "metal"),
    SPACE("空间", "§5", "space"),
    FIRE("火系", "§c", "fire"),
    THUNDER("雷系", "§b", "thunder"),
    WOOD("木系", "§a", "wood");

    private final String chinese;
    private final String color;
    private final String configKey;

    AbilityType(String chinese, String color, String configKey) {
        this.chinese = chinese;
        this.color = color;
        this.configKey = configKey;
    }

    public String getChinese() { return chinese; }
    public String getColor() { return color; }
    public String getConfigKey() { return configKey; }

    public static AbilityType fromString(String name) {
        for (AbilityType type : values()) {
            if (type.name().equalsIgnoreCase(name)
                || type.chinese.equals(name)
                || type.configKey.equalsIgnoreCase(name)) {
                return type;
            }
        }
        return NONE;
    }
}
