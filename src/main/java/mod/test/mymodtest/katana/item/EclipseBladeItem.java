package mod.test.mymodtest.katana.item;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;

import java.util.List;

/**
 * 暗月之蚀（Eclipse Blade）
 *
 * 一把带来无尽黑暗与虚弱的诅咒之刃
 *
 * 核心机制：
 * - 40% 触发概率，无环境限制
 * - 施加月蚀标记（3秒，Boss减半）+ 随机 2 种 Debuff
 * - 护甲穿透：基础 35%，对标记目标 70%
 * - 被标记目标获得 Glowing 效果
 */
public class EclipseBladeItem extends Item {

    public EclipseBladeItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings()
            .maxDamage(KatanaItems.KATANA_MAX_DURABILITY)
            .rarity(Rarity.EPIC)
            .attributeModifiers(createAttributeModifiers());
    }

    private static AttributeModifiersComponent createAttributeModifiers() {
        return AttributeModifiersComponent.builder()
            .add(
                EntityAttributes.GENERIC_ATTACK_DAMAGE,
                new EntityAttributeModifier(
                    Item.BASE_ATTACK_DAMAGE_MODIFIER_ID,
                    6.0,  // 基础伤害（总攻击力 7）
                    EntityAttributeModifier.Operation.ADD_VALUE
                ),
                AttributeModifierSlot.MAINHAND
            )
            .add(
                EntityAttributes.GENERIC_ATTACK_SPEED,
                new EntityAttributeModifier(
                    Item.BASE_ATTACK_SPEED_MODIFIER_ID,
                    -2.2,  // 攻速 1.8 = 4.0 + (-2.2)
                    EntityAttributeModifier.Operation.ADD_VALUE
                ),
                AttributeModifierSlot.MAINHAND
            )
            .build();
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // 攻击后损耗 1 点耐久
        stack.damage(1, attacker, EquipmentSlot.MAINHAND);
        return true;
    }

    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.empty());

        // 特效名称 - 深紫色
        tooltip.add(Text.translatable("item.mymodtest.eclipse_blade.effect_name")
            .formatted(Formatting.DARK_PURPLE));

        // 护甲穿透说明
        tooltip.add(Text.literal("  ")
            .append(Text.translatable("item.mymodtest.eclipse_blade.effect_penetration"))
            .formatted(Formatting.RED));

        // Debuff 说明
        tooltip.add(Text.literal("  ")
            .append(Text.translatable("item.mymodtest.eclipse_blade.effect_desc1"))
            .formatted(Formatting.GRAY));
        tooltip.add(Text.literal("  ")
            .append(Text.translatable("item.mymodtest.eclipse_blade.effect_desc2"))
            .formatted(Formatting.GRAY));

        // 风味文字
        tooltip.add(Text.literal("  ")
            .append(Text.translatable("item.mymodtest.eclipse_blade.effect_lore"))
            .formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
    }

    @Override
    public float getMiningSpeed(ItemStack stack, BlockState state) {
        if (state.isOf(Blocks.COBWEB)) return 15.0f;
        return 1.0f;
    }
}
