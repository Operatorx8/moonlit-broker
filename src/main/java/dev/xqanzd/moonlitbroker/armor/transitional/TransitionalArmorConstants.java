package dev.xqanzd.moonlitbroker.armor.transitional;

import net.minecraft.util.Rarity;

/**
 * 过渡护甲常量定义
 * 共同约束：fireproof=true, anvilRepair=false, knockbackResistance=0
 */
public final class TransitionalArmorConstants {

    private TransitionalArmorConstants() {}

    // === 通用 ===
    public static final float KNOCKBACK_RESISTANCE = 0.0f;
    public static final boolean DEBUG = false;

    // === 1. Scavenger's Goggles - 拾荒者的风镜 ===
    public static final int SCAVENGER_GOGGLES_DURABILITY = 180;
    public static final int SCAVENGER_GOGGLES_DEFENSE = 3;
    public static final float SCAVENGER_GOGGLES_TOUGHNESS = 3.0f;
    public static final Rarity SCAVENGER_GOGGLES_RARITY = Rarity.UNCOMMON;

    // === 2. Cast Iron Sallet - 生铁护面盔 ===
    public static final int CAST_IRON_SALLET_DURABILITY = 200;
    public static final int CAST_IRON_SALLET_DEFENSE = 3;
    public static final float CAST_IRON_SALLET_TOUGHNESS = 3.0f;
    public static final Rarity CAST_IRON_SALLET_RARITY = Rarity.UNCOMMON;

    // === 3. Sanctified Hood - 祝圣兜帽 ===
    public static final int SANCTIFIED_HOOD_DURABILITY = 165;
    public static final int SANCTIFIED_HOOD_DEFENSE = 3;
    public static final float SANCTIFIED_HOOD_TOUGHNESS = 3.0f;
    public static final Rarity SANCTIFIED_HOOD_RARITY = Rarity.RARE;
    public static final float SANCTIFIED_MAGIC_REDUCTION_MULT = 0.85f;  // 魔法伤害 ×0.85
    public static final String SANCTIFIED_HOOD_EFFECT_ID = "sanctified_hood_magic_resist";

    // === 4. Reactive Bug Plate - 反应Bug装甲板 ===
    public static final int REACTIVE_BUG_PLATE_DURABILITY = 260;
    public static final int REACTIVE_BUG_PLATE_DEFENSE = 8;
    public static final float REACTIVE_BUG_PLATE_TOUGHNESS = 3.0f;
    public static final Rarity REACTIVE_BUG_PLATE_RARITY = Rarity.RARE;
    public static final float REACTIVE_BUG_FLAT_REDUCTION = 1.0f;  // 固定减伤 1.0
    public static final float REACTIVE_BUG_CLAMP_MIN = 0.0f;       // 最终伤害下限
    public static final String REACTIVE_BUG_PLATE_EFFECT_ID = "reactive_bug_plate_arthropod_resist";

    // === 5. Patchwork Coat - 补丁皮大衣 ===
    public static final int PATCHWORK_COAT_DURABILITY = 240;
    public static final int PATCHWORK_COAT_DEFENSE = 7;
    public static final float PATCHWORK_COAT_TOUGHNESS = 3.0f;
    public static final Rarity PATCHWORK_COAT_RARITY = Rarity.UNCOMMON;

    // === 6. Ritual Robe - 仪式罩袍 ===
    public static final int RITUAL_ROBE_DURABILITY = 220;
    public static final int RITUAL_ROBE_DEFENSE = 7;
    public static final float RITUAL_ROBE_TOUGHNESS = 3.0f;
    public static final Rarity RITUAL_ROBE_RARITY = Rarity.UNCOMMON;

    // ==================== LEGS ====================

    // === 7. Wrapped Leggings - 缠布护腿 ===
    public static final int WRAPPED_LEGGINGS_DURABILITY = 200;
    public static final int WRAPPED_LEGGINGS_DEFENSE = 6;
    public static final float WRAPPED_LEGGINGS_TOUGHNESS = 3.0f;
    public static final Rarity WRAPPED_LEGGINGS_RARITY = Rarity.UNCOMMON;

    // === 8. Reinforced Greaves - 加固护膝 ===
    public static final int REINFORCED_GREAVES_DURABILITY = 280;
    public static final int REINFORCED_GREAVES_DEFENSE = 6;
    public static final float REINFORCED_GREAVES_TOUGHNESS = 3.0f;
    public static final Rarity REINFORCED_GREAVES_RARITY = Rarity.UNCOMMON;

    // === 9. Cargo Pants - 多袋工装裤 ===
    public static final int CARGO_PANTS_DURABILITY = 225;
    public static final int CARGO_PANTS_DEFENSE = 6;
    public static final float CARGO_PANTS_TOUGHNESS = 3.0f;
    public static final Rarity CARGO_PANTS_RARITY = Rarity.RARE;
    public static final float CARGO_TORCH_SAVE_CHANCE = 0.15f;  // 15% 返还概率
    public static final int CARGO_TORCH_CD_TICKS = 200;          // 10s CD
    public static final String CARGO_PANTS_EFFECT_ID = "cargo_pants_torch_return";

    // ==================== FEET ====================

    // === 10. Penitent Boots - 苦行者之靴 ===
    public static final int PENITENT_BOOTS_DURABILITY = 200;
    public static final int PENITENT_BOOTS_DEFENSE = 3;
    public static final float PENITENT_BOOTS_TOUGHNESS = 3.0f;
    public static final Rarity PENITENT_BOOTS_RARITY = Rarity.UNCOMMON;

    // === 11. Standard Iron Boots - 制式铁靴 ===
    public static final int STANDARD_IRON_BOOTS_DURABILITY = 210;
    public static final int STANDARD_IRON_BOOTS_DEFENSE = 3;
    public static final float STANDARD_IRON_BOOTS_TOUGHNESS = 3.0f;
    public static final Rarity STANDARD_IRON_BOOTS_RARITY = Rarity.UNCOMMON;

    // === 12. Cushion Hiking Boots - 缓冲登山靴 ===
    public static final int CUSHION_HIKING_BOOTS_DURABILITY = 260;
    public static final int CUSHION_HIKING_BOOTS_DEFENSE = 3;
    public static final float CUSHION_HIKING_BOOTS_TOUGHNESS = 3.0f;
    public static final Rarity CUSHION_HIKING_BOOTS_RARITY = Rarity.RARE;
    public static final float CUSHION_FALL_FLAT_REDUCTION = 2.0f;  // 摔落减伤 flat -2.0
    public static final float CUSHION_FALL_CLAMP_MIN = 0.0f;       // 最终下限
    public static final String CUSHION_HIKING_BOOTS_EFFECT_ID = "cushion_hiking_boots_fall_resist";
}
