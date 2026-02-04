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

public class NmapKatanaItem extends Item {

    public NmapKatanaItem(Settings settings) {
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
                    5.0,  // Base damage (+1 from hand = 6 total)
                    EntityAttributeModifier.Operation.ADD_VALUE
                ),
                AttributeModifierSlot.MAINHAND
            )
            .add(
                EntityAttributes.GENERIC_ATTACK_SPEED,
                new EntityAttributeModifier(
                    Item.BASE_ATTACK_SPEED_MODIFIER_ID,
                    -2.2,  // Attack speed 1.8 = 4.0 + (-2.2)
                    EntityAttributeModifier.Operation.ADD_VALUE
                ),
                AttributeModifierSlot.MAINHAND
            )
            .build();
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.damage(1, attacker, EquipmentSlot.MAINHAND);
        return true;
    }

    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        // Lore subtitle
        tooltip.add(Text.literal("nmap -sV -O -A --script=vuln")
            .formatted(Formatting.DARK_GRAY, Formatting.ITALIC));

        tooltip.add(Text.empty());

        // Effect title
        tooltip.add(Text.translatable("item.mymodtest.nmap_katana.effect_name")
            .formatted(Formatting.GOLD, Formatting.BOLD));

        // Module list
        tooltip.add(Text.literal("  \u25b8 ")
            .append(Text.translatable("item.mymodtest.nmap_katana.module_discovery"))
            .formatted(Formatting.AQUA));
        tooltip.add(Text.literal("  \u25b8 ")
            .append(Text.translatable("item.mymodtest.nmap_katana.module_enum"))
            .formatted(Formatting.GREEN));
        tooltip.add(Text.literal("  \u25b8 ")
            .append(Text.translatable("item.mymodtest.nmap_katana.module_vuln"))
            .formatted(Formatting.RED));
        tooltip.add(Text.literal("  \u25b8 ")
            .append(Text.translatable("item.mymodtest.nmap_katana.module_firewall"))
            .formatted(Formatting.LIGHT_PURPLE));
    }

    @Override
    public float getMiningSpeed(ItemStack stack, BlockState state) {
        if (state.isOf(Blocks.COBWEB)) return 15.0f;
        return 1.0f;
    }
}
