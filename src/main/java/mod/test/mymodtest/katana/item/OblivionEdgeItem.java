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
 * 窃念之黯 - 能窃取思维、翻转因果的诅咒之刃
 *
 * 特效：
 * - ReadWrite 标记：25% 概率标记目标
 * - 伴随 Debuff：虚弱II 或 缓慢II
 * - 倒因噬果：残血时可将敌人血量拉至与己相同
 * - 护甲穿透：对标记目标 20%
 */
public class OblivionEdgeItem extends Item {

    public OblivionEdgeItem(Settings settings) {
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
                    5.0,  // 基础伤害（总攻击力 6）
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
        tooltip.add(Text.translatable("item.mymodtest.oblivion_edge.effect_name")
            .formatted(Formatting.DARK_PURPLE, Formatting.BOLD));

        // ReadWrite 说明
        tooltip.add(Text.literal("  ")
            .append(Text.translatable("item.mymodtest.oblivion_edge.effect_readwrite"))
            .formatted(Formatting.GRAY));

        // 倒因噬果说明
        tooltip.add(Text.literal("  ")
            .append(Text.translatable("item.mymodtest.oblivion_edge.effect_causality"))
            .formatted(Formatting.RED));

        // 护甲穿透
        tooltip.add(Text.literal("  ")
            .append(Text.translatable("item.mymodtest.oblivion_edge.effect_penetration"))
            .formatted(Formatting.GOLD));

        // 风味文字
        tooltip.add(Text.empty());
        tooltip.add(Text.literal("  ")
            .append(Text.translatable("item.mymodtest.oblivion_edge.effect_lore"))
            .formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
    }

    @Override
    public float getMiningSpeed(ItemStack stack, BlockState state) {
        if (state.isOf(Blocks.COBWEB)) return 15.0f;
        return 1.0f;
    }
}
