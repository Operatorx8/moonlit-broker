package dev.xqanzd.moonlitbroker.trade.loot;

import dev.xqanzd.moonlitbroker.registry.ModItems;
import dev.xqanzd.moonlitbroker.trade.TradeConfig;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 战利品表注入
 * 卷轴：保留原有目标箱
 * Coin：仅注入高级结构箱（stronghold_library / ancient_city / trial_chambers*）
 */
public class LootTableInjector {
    private static final Logger LOGGER = LoggerFactory.getLogger(LootTableInjector.class);

    // 卷轴目标箱（保持原逻辑）
    private static final Set<Identifier> SCROLL_TARGET_CHESTS = Set.of(
        Identifier.ofVanilla("chests/simple_dungeon"),
        Identifier.ofVanilla("chests/abandoned_mineshaft"),
        Identifier.ofVanilla("chests/stronghold_corridor"),
        Identifier.ofVanilla("chests/stronghold_crossing"),
        Identifier.ofVanilla("chests/stronghold_library"),
        Identifier.ofVanilla("chests/shipwreck_treasure"),
        Identifier.ofVanilla("chests/shipwreck_supply")
    );
    private static final Identifier CHEST_STRONGHOLD_LIBRARY = Identifier.ofVanilla("chests/stronghold_library");
    private static final Identifier CHEST_ANCIENT_CITY = Identifier.ofVanilla("chests/ancient_city");
    private static final String TRIAL_CHEST_PREFIX = "minecraft:chests/trial_chambers";

    // 探索结构箱（不需要 Boss）
    private static final Identifier CHEST_BURIED_TREASURE = Identifier.ofVanilla("chests/buried_treasure");
    private static final Identifier CHEST_SHIPWRECK_TREASURE = Identifier.ofVanilla("chests/shipwreck_treasure");
    private static final Identifier CHEST_DESERT_PYRAMID = Identifier.ofVanilla("chests/desert_pyramid");
    private static final Identifier CHEST_JUNGLE_TEMPLE = Identifier.ofVanilla("chests/jungle_temple");
    private static final Identifier CHEST_ABANDONED_MINESHAFT = Identifier.ofVanilla("chests/abandoned_mineshaft");

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

            // 注入卷轴
            if (SCROLL_TARGET_CHESTS.contains(id)) {
                LootPool.Builder scrollPool = LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1))
                        .conditionally(RandomChanceLootCondition.builder(TradeConfig.CHEST_SCROLL_CHANCE))
                        .with(ItemEntry.builder(ModItems.TRADE_SCROLL));
                tableBuilder.pool(scrollPool);
                if (TradeConfig.TRADE_DEBUG) {
                    LOGGER.debug("[MoonTrade] LOOT_INJECT_SCROLL table={} chance={}",
                            id, TradeConfig.CHEST_SCROLL_CHANCE);
                }
            }

            // 注入 Coin（仅高级结构箱）
            float coinChance = coinChanceForTable(id);
            if (coinChance > 0f) {
                LootPool.Builder coinPool = LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1))
                        .conditionally(RandomChanceLootCondition.builder(coinChance))
                        .with(ItemEntry.builder(ModItems.MYSTERIOUS_COIN));
                tableBuilder.pool(coinPool);
                if (TradeConfig.TRADE_DEBUG) {
                    LOGGER.debug("[MoonTrade] LOOT_INJECT_COIN table={} chance={}",
                            id, coinChance);
                }
            }
        });

        LOGGER.info("[MoonTrade] 战利品表注入已注册，scrollTargets={} coinTargets=stronghold_library+ancient_city+trial_chambers*+buried_treasure+shipwreck_treasure+desert_pyramid+jungle_temple+abandoned_mineshaft",
                SCROLL_TARGET_CHESTS.size());
    }

    private static float coinChanceForTable(Identifier id) {
        // 高级结构箱（原有）
        if (CHEST_STRONGHOLD_LIBRARY.equals(id)) {
            return TradeConfig.CHEST_COIN_CHANCE_STRONGHOLD;
        }
        if (CHEST_ANCIENT_CITY.equals(id)) {
            return TradeConfig.CHEST_COIN_CHANCE_ANCIENT_CITY;
        }
        if (id.toString().startsWith(TRIAL_CHEST_PREFIX)) {
            return TradeConfig.CHEST_COIN_CHANCE_TRIAL;
        }
        // 探索结构箱（低概率，不需要 Boss）
        if (CHEST_BURIED_TREASURE.equals(id)) {
            return TradeConfig.CHEST_COIN_CHANCE_BURIED_TREASURE;
        }
        if (CHEST_SHIPWRECK_TREASURE.equals(id)) {
            return TradeConfig.CHEST_COIN_CHANCE_SHIPWRECK_TREASURE;
        }
        if (CHEST_DESERT_PYRAMID.equals(id)) {
            return TradeConfig.CHEST_COIN_CHANCE_DESERT_PYRAMID;
        }
        if (CHEST_JUNGLE_TEMPLE.equals(id)) {
            return TradeConfig.CHEST_COIN_CHANCE_JUNGLE_TEMPLE;
        }
        if (CHEST_ABANDONED_MINESHAFT.equals(id)) {
            return TradeConfig.CHEST_COIN_CHANCE_ABANDONED_MINESHAFT;
        }
        return 0f;
    }
}
