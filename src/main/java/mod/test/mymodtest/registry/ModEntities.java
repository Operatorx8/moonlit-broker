package mod.test.mymodtest.registry;

import mod.test.mymodtest.entity.MysteriousMerchantEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModEntities {

    public static final String MOD_ID = "mymodtest";

    public static final RegistryKey<EntityType<?>> MYSTERIOUS_MERCHANT_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(MOD_ID, "mysterious_merchant"));

    public static final EntityType<MysteriousMerchantEntity> MYSTERIOUS_MERCHANT =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    MYSTERIOUS_MERCHANT_KEY.getValue(),
                    EntityType.Builder
                            .create(MysteriousMerchantEntity::new, SpawnGroup.CREATURE)
                            .dimensions(0.6f, 1.95f)
                            .build(MYSTERIOUS_MERCHANT_KEY)
            );

    // Phase 1: 先给个基础属性，能活着走两步就行
    private static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes();
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(
                MYSTERIOUS_MERCHANT,
                MysteriousMerchantEntity.createMerchantAttributes()
        );
    }
}
