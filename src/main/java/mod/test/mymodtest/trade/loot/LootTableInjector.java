package mod.test.mymodtest.trade.loot;

import mod.test.mymodtest.registry.ModItems;
import mod.test.mymodtest.trade.TradeConfig;
import mod.test.mymodtest.trade.item.TradeScrollItem;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.LootFunction;
import net.minecraft.loot.function.LootFunctionType;
import net.minecraft.loot.function.LootFunctionTypes;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 战利品表注入
 * 仅向指定的4类宝箱注入交易卷轴
 */
public class LootTableInjector {
    private static final Logger LOGGER = LoggerFactory.getLogger(LootTableInjector.class);

    // 目标宝箱战利品表（仅这4类）
    private static final Set<Identifier> TARGET_CHESTS = Set.of(
        Identifier.ofVanilla("chests/simple_dungeon"),
        Identifier.ofVanilla("chests/abandoned_mineshaft"),
        Identifier.ofVanilla("chests/stronghold_corridor"),
        Identifier.ofVanilla("chests/stronghold_crossing"),
        Identifier.ofVanilla("chests/stronghold_library"),
        Identifier.ofVanilla("chests/shipwreck_treasure"),
        Identifier.ofVanilla("chests/shipwreck_supply")
    );

    /**
     * 注册战利品表修改器
     */
    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            // 只修改原版战利品表
            if (!source.isBuiltin()) {
                return;
            }

            Identifier id = key.getValue();
            
            // 检查是否为目标宝箱
            if (!TARGET_CHESTS.contains(id)) {
                return;
            }

            // 添加交易卷轴掉落池
            LootPool.Builder pool = LootPool.builder()
                .rolls(ConstantLootNumberProvider.create(1))
                .conditionally(RandomChanceLootCondition.builder(TradeConfig.CHEST_SCROLL_CHANCE))
                .with(ItemEntry.builder(ModItems.TRADE_SCROLL));

            tableBuilder.pool(pool);

            if (TradeConfig.TRADE_DEBUG) {
                LOGGER.debug("[MoonTrade] LOOT_INJECT table={} chance={}", 
                    id, TradeConfig.CHEST_SCROLL_CHANCE);
            }
        });

        LOGGER.info("[MoonTrade] 战利品表注入已注册，目标宝箱数量: {}", TARGET_CHESTS.size());
    }
}
