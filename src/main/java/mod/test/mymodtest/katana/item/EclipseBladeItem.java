package mod.test.mymodtest.katana.item;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.util.Rarity;

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
public class EclipseBladeItem extends SwordItem {

    public EclipseBladeItem(Settings settings) {
        super(ToolMaterials.NETHERITE, settings);
    }

    public static Settings createSettings() {
        return new Settings()
            .maxDamage(KatanaItems.KATANA_MAX_DURABILITY)
            .rarity(Rarity.EPIC)
            .attributeModifiers(createAttributeModifiers());
    }

    private static AttributeModifiersComponent createAttributeModifiers() {
        // NETHERITE 材质 attackDamage=4；+3 后总攻击力为 7
        return SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 3, -2.2f);
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
}
