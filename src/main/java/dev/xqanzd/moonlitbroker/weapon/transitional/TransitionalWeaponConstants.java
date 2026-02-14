package dev.xqanzd.moonlitbroker.weapon.transitional;

import org.apache.commons.codec.StringDecoder;

/**
 * 过渡武器常量定义
 * 定位：铁剑 → Katana 之间的过渡层
 */
public final class TransitionalWeaponConstants {

    private TransitionalWeaponConstants() {}

    // === 基础常量 ===
    public static final int IRON_SWORD_DURABILITY = 250;

    // === Acer - 暴击强化剑 ===
    public static final int ACER_BASE_DAMAGE = 8;
    public static final float ACER_ATTACK_SPEED = -2.2f;  // 1.8 effective (4.0 - 2.2)
    public static final int ACER_DURABILITY = (int) (IRON_SWORD_DURABILITY * 1.15);  // 287
    public static final float ACER_CRIT_MULTIPLIER = 1.7f;  // 原版 1.5，Acer 提升至 1.7

    // === Velox - 快攻剑 ===
    public static final int VELOX_BASE_DAMAGE = 7;
    public static final float VELOX_ATTACK_SPEED = -1.6f;  // 2.4 effective (4.0 - 1.6)
    public static final int VELOX_DURABILITY = (int) (IRON_SWORD_DURABILITY * 1.05);  // 262

    // === Fatalis - 重击剑 ===
    public static final int FATALIS_BASE_DAMAGE = 12;
    public static final float FATALIS_ATTACK_SPEED = -2.2f;  // 1.8 effective
    public static final int FATALIS_DURABILITY = (int) (IRON_SWORD_DURABILITY * 1.15);  // 287

    // === 附魔系数 ===
    public static final int KATANA_ENCHANTABILITY = 15;  // 与 Katana 系统一致
    public static final int SWORD_ENCHANTABILITY = 14;   // 原版铁剑附魔系数

    // === Debug ===
    public static final boolean DEBUG = true;
}
