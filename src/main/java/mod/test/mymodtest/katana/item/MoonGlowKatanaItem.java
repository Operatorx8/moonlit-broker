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

public class MoonGlowKatanaItem extends Item {

    public MoonGlowKatanaItem(Settings settings) {
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
                    6.0,  // 基础伤害（总攻击力 7，与钻石剑相当）
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
        // 攻击后损耗 1 点耐久（仅在服务端执行）
        stack.damage(1, attacker, EquipmentSlot.MAINHAND);
        return true;
    }

    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return false;
    }

    @Override
    public float getMiningSpeed(ItemStack stack, BlockState state) {
        // 太刀可快速破坏蜘蛛网
        if (state.isOf(Blocks.COBWEB)) return 15.0f;
        return 1.0f;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        // 空行分隔
        tooltip.add(Text.empty());

        // 特效名称 - 蓝色
        tooltip.add(Text.translatable("item.mymodtest.moon_glow_katana.effect_name")
            .formatted(Formatting.BLUE));

        // 特效说明 - 灰色，带缩进
        tooltip.add(Text.literal("  ")
            .append(Text.translatable("item.mymodtest.moon_glow_katana.effect_desc1"))
            .formatted(Formatting.GRAY));
        tooltip.add(Text.literal("  ")
            .append(Text.translatable("item.mymodtest.moon_glow_katana.effect_desc2"))
            .formatted(Formatting.GRAY));
    }
}
