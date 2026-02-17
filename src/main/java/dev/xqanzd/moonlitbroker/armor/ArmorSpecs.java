package dev.xqanzd.moonlitbroker.armor;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 按 item id 查询单件装备的数值覆写表。
 * <p>
 * 覆写表只负责数据，不负责注册；注册/材质保持原逻辑。
 * 查询未命中时返回 null，调用方应回退到 ArmorConfig 默认值。
 * <p>
 * 要改任何单件装备数值，只需修改 {@link MerchantArmorConstants} 中对应常量。
 */
public final class ArmorSpecs {
    private ArmorSpecs() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("MoonlitBroker|ArmorSpecs");

    /** 覆写表：key = item path（如 "windbreaker_chestplate"），value = 覆写数据 */
    private static final Map<String, ArmorSpec> OVERRIDES = new HashMap<>();

    // =====================================================================
    //  覆写表（按套装/槽位分组）
    //  格式：new ArmorSpec(protection, toughness, knockbackResistance)
    //  null = 不覆写，回退 ArmorConfig 默认值
    // =====================================================================
    static {
        // ----- 头盔 (Helmet) -----
        OVERRIDES.put("sentinel_helmet", new ArmorSpec(
                MerchantArmorConstants.SENTINEL_HELMET_PROTECTION,
                MerchantArmorConstants.SENTINEL_HELMET_TOUGHNESS,
                MerchantArmorConstants.SENTINEL_HELMET_KNOCKBACK_RESISTANCE));
        OVERRIDES.put("silent_oath_helmet", new ArmorSpec(
                MerchantArmorConstants.SILENT_OATH_HELMET_PROTECTION,
                MerchantArmorConstants.SILENT_OATH_HELMET_TOUGHNESS,
                MerchantArmorConstants.SILENT_OATH_HELMET_KNOCKBACK_RESISTANCE));
        OVERRIDES.put("exile_mask_helmet", new ArmorSpec(
                MerchantArmorConstants.EXILE_MASK_HELMET_PROTECTION,
                MerchantArmorConstants.EXILE_MASK_HELMET_TOUGHNESS,
                MerchantArmorConstants.EXILE_MASK_HELMET_KNOCKBACK_RESISTANCE));
        OVERRIDES.put("relic_circlet_helmet", new ArmorSpec(
                MerchantArmorConstants.RELIC_CIRCLET_HELMET_PROTECTION,
                MerchantArmorConstants.RELIC_CIRCLET_HELMET_TOUGHNESS,
                MerchantArmorConstants.RELIC_CIRCLET_HELMET_KNOCKBACK_RESISTANCE));
        OVERRIDES.put("retracer_ornament_helmet", new ArmorSpec(
                MerchantArmorConstants.RETRACER_ORNAMENT_HELMET_PROTECTION,
                MerchantArmorConstants.RETRACER_ORNAMENT_HELMET_TOUGHNESS,
                MerchantArmorConstants.RETRACER_ORNAMENT_HELMET_KNOCKBACK_RESISTANCE));

        // ----- 胸甲 (Chestplate) -----
        OVERRIDES.put("old_market_chestplate", new ArmorSpec(
                MerchantArmorConstants.OLD_MARKET_CHESTPLATE_PROTECTION,
                MerchantArmorConstants.OLD_MARKET_CHESTPLATE_TOUGHNESS,
                MerchantArmorConstants.OLD_MARKET_CHESTPLATE_KNOCKBACK_RESISTANCE));
        OVERRIDES.put("blood_pact_chestplate", new ArmorSpec(
                MerchantArmorConstants.BLOOD_PACT_CHESTPLATE_PROTECTION,
                MerchantArmorConstants.BLOOD_PACT_CHESTPLATE_TOUGHNESS,
                MerchantArmorConstants.BLOOD_PACT_CHESTPLATE_KNOCKBACK_RESISTANCE));
        OVERRIDES.put("ghost_god_chestplate", new ArmorSpec(
                MerchantArmorConstants.GHOST_GOD_CHESTPLATE_PROTECTION,
                MerchantArmorConstants.GHOST_GOD_CHESTPLATE_TOUGHNESS,
                MerchantArmorConstants.GHOST_GOD_CHESTPLATE_KNOCKBACK_RESISTANCE));
        OVERRIDES.put("windbreaker_chestplate", new ArmorSpec(
                MerchantArmorConstants.WINDBREAKER_CHESTPLATE_PROTECTION,
                MerchantArmorConstants.WINDBREAKER_CHESTPLATE_TOUGHNESS,
                MerchantArmorConstants.WINDBREAKER_CHESTPLATE_KNOCKBACK_RESISTANCE));
        OVERRIDES.put("void_devourer_chestplate", new ArmorSpec(
                MerchantArmorConstants.VOID_DEVOURER_CHESTPLATE_PROTECTION,
                MerchantArmorConstants.VOID_DEVOURER_CHESTPLATE_TOUGHNESS,
                MerchantArmorConstants.VOID_DEVOURER_CHESTPLATE_KNOCKBACK_RESISTANCE));

        // ----- 护腿 (Leggings) -----
        OVERRIDES.put("smuggler_shin_leggings", new ArmorSpec(
                MerchantArmorConstants.SMUGGLER_SHIN_LEGGINGS_PROTECTION,
                MerchantArmorConstants.SMUGGLER_SHIN_LEGGINGS_TOUGHNESS,
                MerchantArmorConstants.SMUGGLER_SHIN_LEGGINGS_KNOCKBACK_RESISTANCE));
        OVERRIDES.put("smuggler_pouch_leggings", new ArmorSpec(
                MerchantArmorConstants.SMUGGLER_POUCH_LEGGINGS_PROTECTION,
                MerchantArmorConstants.SMUGGLER_POUCH_LEGGINGS_TOUGHNESS,
                MerchantArmorConstants.SMUGGLER_POUCH_LEGGINGS_KNOCKBACK_RESISTANCE));
        OVERRIDES.put("graze_guard_leggings", new ArmorSpec(
                MerchantArmorConstants.GRAZE_GUARD_LEGGINGS_PROTECTION,
                MerchantArmorConstants.GRAZE_GUARD_LEGGINGS_TOUGHNESS,
                MerchantArmorConstants.GRAZE_GUARD_LEGGINGS_KNOCKBACK_RESISTANCE));
        OVERRIDES.put("stealth_shin_leggings", new ArmorSpec(
                MerchantArmorConstants.STEALTH_SHIN_LEGGINGS_PROTECTION,
                MerchantArmorConstants.STEALTH_SHIN_LEGGINGS_TOUGHNESS,
                MerchantArmorConstants.STEALTH_SHIN_LEGGINGS_KNOCKBACK_RESISTANCE));
        OVERRIDES.put("clear_ledger_leggings", new ArmorSpec(
                MerchantArmorConstants.CLEAR_LEDGER_LEGGINGS_PROTECTION,
                MerchantArmorConstants.CLEAR_LEDGER_LEGGINGS_TOUGHNESS,
                MerchantArmorConstants.CLEAR_LEDGER_LEGGINGS_KNOCKBACK_RESISTANCE));

        // ----- 靴子 (Boots) -----
        OVERRIDES.put("untraceable_treads_boots", new ArmorSpec(
                MerchantArmorConstants.UNTRACEABLE_TREADS_BOOTS_PROTECTION,
                MerchantArmorConstants.UNTRACEABLE_TREADS_BOOTS_TOUGHNESS,
                MerchantArmorConstants.UNTRACEABLE_TREADS_BOOTS_KNOCKBACK_RESISTANCE));
        OVERRIDES.put("boundary_walker_boots", new ArmorSpec(
                MerchantArmorConstants.BOUNDARY_WALKER_BOOTS_PROTECTION,
                MerchantArmorConstants.BOUNDARY_WALKER_BOOTS_TOUGHNESS,
                MerchantArmorConstants.BOUNDARY_WALKER_BOOTS_KNOCKBACK_RESISTANCE));
        OVERRIDES.put("ghost_step_boots", new ArmorSpec(
                MerchantArmorConstants.GHOST_STEP_BOOTS_PROTECTION,
                MerchantArmorConstants.GHOST_STEP_BOOTS_TOUGHNESS,
                MerchantArmorConstants.GHOST_STEP_BOOTS_KNOCKBACK_RESISTANCE));
        OVERRIDES.put("marching_boots", new ArmorSpec(
                MerchantArmorConstants.MARCHING_BOOTS_PROTECTION,
                MerchantArmorConstants.MARCHING_BOOTS_TOUGHNESS,
                MerchantArmorConstants.MARCHING_BOOTS_KNOCKBACK_RESISTANCE));
        OVERRIDES.put("gossamer_boots", new ArmorSpec(
                MerchantArmorConstants.GOSSAMER_BOOTS_PROTECTION,
                MerchantArmorConstants.GOSSAMER_BOOTS_TOUGHNESS,
                MerchantArmorConstants.GOSSAMER_BOOTS_KNOCKBACK_RESISTANCE));
    }

    /**
     * 按 item path 查询覆写（如 "windbreaker_chestplate"）
     * @return 覆写记录，未命中返回 null
     */
    @Nullable
    public static ArmorSpec forItemPath(String itemPath) {
        return OVERRIDES.get(itemPath);
    }

    /**
     * 按 Item 实例查询覆写
     * @return 覆写记录，未命中返回 null
     */
    @Nullable
    public static ArmorSpec forItem(Item item) {
        Identifier id = Registries.ITEM.getId(item);
        if (!ArmorConfig.MOD_ID.equals(id.getNamespace())) {
            return null;
        }
        return OVERRIDES.get(id.getPath());
    }

    /**
     * 按 item path 查询覆写，未命中返回空 spec（所有字段 null，全部回退默认值）
     */
    public static ArmorSpec lookup(String itemPath) {
        ArmorSpec spec = OVERRIDES.get(itemPath);
        return spec != null ? spec : new ArmorSpec(null, null, null);
    }

    /** 覆写表条目数（用于日志） */
    public static int overrideCount() {
        return OVERRIDES.size();
    }

    /**
     * 覆盖率自检：遍历 OVERRIDES 的每个 key，校验对应 item 是否已注册。
     * 拼错 key 时会立即在日志中暴露，不会悄悄回退默认值。
     * <p>
     * 应在所有 item 注册完成后调用（如 Mymodtest.onInitialize 末尾）。
     */
    public static void validateOverrideKeys() {
        int checked = 0;
        int missing = 0;

        for (String path : OVERRIDES.keySet()) {
            checked++;
            Identifier id = Identifier.of(ArmorConfig.MOD_ID, path);
            Item item = Registries.ITEM.get(id);
            // Registries.ITEM.get() 对未注册 id 返回 Items.AIR
            if (item == net.minecraft.item.Items.AIR) {
                missing++;
                LOGGER.warn("[ArmorSpecs] Override key not registered: {} — typo or item removed?", id);
            }
        }

        if (missing > 0) {
            LOGGER.warn("[ArmorSpecs] validateOverrideKeys: checked={} missing={} — fix OVERRIDES keys!",
                    checked, missing);
        } else {
            LOGGER.info("[ArmorSpecs] validateOverrideKeys: checked={} all OK, overrides={}",
                    checked, checked);
        }
    }
}
