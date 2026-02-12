package dev.xqanzd.moonlitbroker.registry;

import dev.xqanzd.moonlitbroker.block.MysteriousAnvilBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModBlocks {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModBlocks.class);

    public static final Identifier MYSTERIOUS_ANVIL_ID = Identifier.of(ModItems.MOD_ID, "mysterious_anvil");

    public static final Block MYSTERIOUS_ANVIL = Registry.register(
        Registries.BLOCK,
        MYSTERIOUS_ANVIL_ID,
        new MysteriousAnvilBlock(AbstractBlock.Settings.copy(Blocks.ANVIL))
    );

    public static final Item MYSTERIOUS_ANVIL_ITEM = Registry.register(
        Registries.ITEM,
        MYSTERIOUS_ANVIL_ID,
        new BlockItem(MYSTERIOUS_ANVIL, new Item.Settings())
    );

    private ModBlocks() {}

    public static void register() {
        LOGGER.info("[Mymodtest] 方块已注册: {}", MYSTERIOUS_ANVIL_ID);
    }
}
