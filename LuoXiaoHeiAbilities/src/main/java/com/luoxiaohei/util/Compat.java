package com.luoxiaohei.util;

import org.bukkit.Particle;
import org.bukkit.potion.PotionEffectType;

/**
 * API兼容工具 - 粒子/Potion效果名称 (兼容Paper 1.20~1.21+)
 */
public final class Compat {
    private Compat() {}

    public static final PotionEffectType EFFECT_SLOWNESS = getPotion("SLOWNESS", "SLOW");
    public static final PotionEffectType EFFECT_JUMP_BOOST = getPotion("JUMP_BOOST", "JUMP");

    public static final Particle PARTICLE_EXPLOSION_HUGE = getParticle("EXPLOSION", "EXPLOSION_HUGE", "HUGE_EXPLOSION");
    public static final Particle PARTICLE_HAPPY_VILLAGER = getParticle("HAPPY_VILLAGER", "VILLAGER_HAPPY");

    private static PotionEffectType getPotion(String... names) {
        for (String n : names) {
            try {
                var f = PotionEffectType.class.getField(n);
                return (PotionEffectType) f.get(null);
            } catch (Exception ignored) {}
        }
        return PotionEffectType.SPEED;
    }

    private static Particle getParticle(String... names) {
        for (String n : names) {
            try { return Particle.valueOf(n); } catch (Exception ignored) {}
        }
        return Particle.EXPLOSION;
    }
}
