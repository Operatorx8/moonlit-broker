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
 * 残念之刃 - 处刑型太刀
 * 特效：削减目标当前血量的 30%，但无法击杀
 */
public class RegretBladeItem extends Item {

    public RegretBladeItem(Settings settings) {
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
    public float getMiningSpeed(ItemStack stack, BlockState state) {
        if (state.isOf(Blocks.COBWEB)) return 15.0f;
        return 1.0f;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.empty());

        // 特效名称 - 暗红色
        tooltip.add(Text.translatable("item.mymodtest.regret_blade.effect_name")
            .formatted(Formatting.DARK_RED));

        // 特效说明
        tooltip.add(Text.literal("  ")
            .append(Text.translatable("item.mymodtest.regret_blade.effect_desc1"))
            .formatted(Formatting.GRAY));
        // 护甲穿透 - 金色高亮
        tooltip.add(Text.literal("  ")
            .append(Text.translatable("item.mymodtest.regret_blade.effect_desc2"))
            .formatted(Formatting.GOLD));
        tooltip.add(Text.literal("  ")
            .append(Text.translatable("item.mymodtest.regret_blade.effect_desc3"))
            .formatted(Formatting.GRAY));
        // 风味文字
        tooltip.add(Text.literal("  ")
            .append(Text.translatable("item.mymodtest.regret_blade.effect_desc4"))
            .formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
    }
}
