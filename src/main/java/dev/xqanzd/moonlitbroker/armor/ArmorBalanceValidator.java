package dev.xqanzd.moonlitbroker.armor;

import dev.xqanzd.moonlitbroker.Mymodtest;
import dev.xqanzd.moonlitbroker.util.ModLog;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 盔甲数值硬阀门校验（仅告警，不改变行为）。
 * 对于 ArmorSpecs 中有显式覆写的装备，跳过校验（覆写视为有意为之）。
 */
public final class ArmorBalanceValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModLog.MOD_TAG);
    private static final float HELMET_TOUGHNESS_CAP = 1.0f;
    private static final float LEGGINGS_TOUGHNESS_CAP = 1.5f;
    private static final float BOOTS_TOUGHNESS_CAP = 0.8f;
    private static final float EPSILON = 1.0e-4f;

    private ArmorBalanceValidator() {}

    public static void validateNonChestplateToughnessCaps() {
        int checked = 0;
        int warned = 0;
        int skippedOverride = 0;

        for (Item item : Registries.ITEM) {
            if (!(item instanceof ArmorItem armorItem)) {
                continue;
            }
            Identifier itemId = Registries.ITEM.getId(item);
            if (!Mymodtest.MOD_ID.equals(itemId.getNamespace())) {
                continue;
            }

            ArmorItem.Type slot = armorItem.getType();
            if (slot != ArmorItem.Type.HELMET
                    && slot != ArmorItem.Type.LEGGINGS
                    && slot != ArmorItem.Type.BOOTS) {
                continue;
            }

            // ArmorSpecs 覆写的装备跳过校验
            ArmorSpec spec = ArmorSpecs.forItemPath(itemId.getPath());
            if (spec != null && spec.toughness() != null) {
                skippedOverride++;
                continue;
            }

            float toughness = armorItem.getMaterial().value().toughness();
            float cap = toughnessCap(slot);
            int armor = armorItem.getMaterial().value().defense().getOrDefault(slot, 0);
            checked++;

            if (toughness > cap + EPSILON) {
                warned++;
                LOGGER.warn(
                        "{} action=toughness_cap_violation itemId={} slot={} armor={} toughness={} cap={}",
                        ModLog.armorBootPrefix(),
                        itemId,
                        slot.name(),
                        armor,
                        toughness,
                        cap
                );
            }
        }

        LOGGER.info("{} action=toughness_cap_check result=OK checked={} warned={} skipped_override={}",
                ModLog.armorBootPrefix(), checked, warned, skippedOverride);
    }

    private static float toughnessCap(ArmorItem.Type slot) {
        return switch (slot) {
            case HELMET -> HELMET_TOUGHNESS_CAP;
            case LEGGINGS -> LEGGINGS_TOUGHNESS_CAP;
            case BOOTS -> BOOTS_TOUGHNESS_CAP;
            default -> Float.MAX_VALUE;
        };
    }
}
