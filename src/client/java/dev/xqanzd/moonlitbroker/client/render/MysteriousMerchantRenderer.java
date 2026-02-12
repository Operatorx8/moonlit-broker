package dev.xqanzd.moonlitbroker.client.render;

import dev.xqanzd.moonlitbroker.registry.ModEntities;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.WanderingTraderEntityRenderer;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.util.Identifier;

import java.util.Map;

/**
 * Phase 8: 自定义商人渲染器 - 按 EntityType 返回对应材质。
 *
 * 材质映射：
 * - MYSTERIOUS_MERCHANT        -> 1_mysterious_merchant_final.png (Standard/Brown)
 * - MYSTERIOUS_MERCHANT_ARID   -> 2_mysterious_merchant_final.png (Arid/RedYellow)
 * - MYSTERIOUS_MERCHANT_COLD   -> 3_mysterious_merchant_final.png (Cold/BlueWhite)
 * - MYSTERIOUS_MERCHANT_WET    -> 4_mysterious_merchant_final.png (Wet/GreenPurple)
 * - MYSTERIOUS_MERCHANT_EXOTIC -> 5_mysterious_merchant_final.png (Exotic/Wild)
 */
public class MysteriousMerchantRenderer extends WanderingTraderEntityRenderer {

    private static final String MOD_ID = "xqanzd_moonlit_broker";

    private static final Identifier TEXTURE_STANDARD = Identifier.of(MOD_ID, "textures/entity/1_mysterious_merchant_final.png");
    private static final Identifier TEXTURE_ARID     = Identifier.of(MOD_ID, "textures/entity/2_mysterious_merchant_final.png");
    private static final Identifier TEXTURE_COLD     = Identifier.of(MOD_ID, "textures/entity/3_mysterious_merchant_final.png");
    private static final Identifier TEXTURE_WET      = Identifier.of(MOD_ID, "textures/entity/4_mysterious_merchant_final.png");
    private static final Identifier TEXTURE_EXOTIC   = Identifier.of(MOD_ID, "textures/entity/5_mysterious_merchant_final.png");
    private static final Map<EntityType<?>, Identifier> TEXTURE_BY_TYPE = Map.of(
        ModEntities.MYSTERIOUS_MERCHANT, TEXTURE_STANDARD,
        ModEntities.MYSTERIOUS_MERCHANT_ARID, TEXTURE_ARID,
        ModEntities.MYSTERIOUS_MERCHANT_COLD, TEXTURE_COLD,
        ModEntities.MYSTERIOUS_MERCHANT_WET, TEXTURE_WET,
        ModEntities.MYSTERIOUS_MERCHANT_EXOTIC, TEXTURE_EXOTIC
    );

    public MysteriousMerchantRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(WanderingTraderEntity entity) {
        return entity == null
            ? TEXTURE_STANDARD
            : TEXTURE_BY_TYPE.getOrDefault(entity.getType(), TEXTURE_STANDARD);
    }
}
