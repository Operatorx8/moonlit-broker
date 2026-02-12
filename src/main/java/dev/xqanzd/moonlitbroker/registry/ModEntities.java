package dev.xqanzd.moonlitbroker.registry;

import dev.xqanzd.moonlitbroker.entity.MysteriousMerchantEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {

    public static final String MOD_ID = "xqanzd_moonlit_broker";

    // ========== 5 Merchant Variant EntityTypes ==========

    public static final Identifier MYSTERIOUS_MERCHANT_ID =
            Identifier.of(MOD_ID, "mysterious_merchant");

    /** Standard / Brown - PLAINS + TAIGA default */
    public static final EntityType<MysteriousMerchantEntity> MYSTERIOUS_MERCHANT =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    MYSTERIOUS_MERCHANT_ID,
                    EntityType.Builder
                            .create(MysteriousMerchantEntity::new, SpawnGroup.CREATURE)
                            .dimensions(0.6f, 1.95f)
                            .build()
            );

    /** Arid / RedYellow - DESERT + SAVANNA default */
    public static final EntityType<MysteriousMerchantEntity> MYSTERIOUS_MERCHANT_ARID =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    Identifier.of(MOD_ID, "mysterious_merchant_arid"),
                    EntityType.Builder
                            .create(MysteriousMerchantEntity::new, SpawnGroup.CREATURE)
                            .dimensions(0.6f, 1.95f)
                            .build()
            );

    /** Cold / BlueWhite - SNOW only */
    public static final EntityType<MysteriousMerchantEntity> MYSTERIOUS_MERCHANT_COLD =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    Identifier.of(MOD_ID, "mysterious_merchant_cold"),
                    EntityType.Builder
                            .create(MysteriousMerchantEntity::new, SpawnGroup.CREATURE)
                            .dimensions(0.6f, 1.95f)
                            .build()
            );

    /** Wet / GreenPurple - SWAMP only */
    public static final EntityType<MysteriousMerchantEntity> MYSTERIOUS_MERCHANT_WET =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    Identifier.of(MOD_ID, "mysterious_merchant_wet"),
                    EntityType.Builder
                            .create(MysteriousMerchantEntity::new, SpawnGroup.CREATURE)
                            .dimensions(0.6f, 1.95f)
                            .build()
            );

    /** Exotic / Wild - JUNGLE only */
    public static final EntityType<MysteriousMerchantEntity> MYSTERIOUS_MERCHANT_EXOTIC =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    Identifier.of(MOD_ID, "mysterious_merchant_exotic"),
                    EntityType.Builder
                            .create(MysteriousMerchantEntity::new, SpawnGroup.CREATURE)
                            .dimensions(0.6f, 1.95f)
                            .build()
            );

    /** All 5 merchant entity types for iteration */
    @SuppressWarnings("unchecked")
    public static final EntityType<MysteriousMerchantEntity>[] ALL_MERCHANT_TYPES = new EntityType[] {
            MYSTERIOUS_MERCHANT,
            MYSTERIOUS_MERCHANT_ARID,
            MYSTERIOUS_MERCHANT_COLD,
            MYSTERIOUS_MERCHANT_WET,
            MYSTERIOUS_MERCHANT_EXOTIC
    };

    public static void register() {
        for (EntityType<MysteriousMerchantEntity> type : ALL_MERCHANT_TYPES) {
            FabricDefaultAttributeRegistry.register(
                    type,
                    MysteriousMerchantEntity.createMerchantAttributes()
            );
        }
    }
}
