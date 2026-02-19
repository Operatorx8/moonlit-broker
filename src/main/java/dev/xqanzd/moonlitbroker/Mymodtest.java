package dev.xqanzd.moonlitbroker;

import dev.xqanzd.moonlitbroker.armor.ArmorInit;
import dev.xqanzd.moonlitbroker.armor.ArmorBalanceValidator;
import dev.xqanzd.moonlitbroker.armor.ArmorSpecs;
import dev.xqanzd.moonlitbroker.armor.item.ArmorDyeSupport;
import dev.xqanzd.moonlitbroker.armor.transitional.TransitionalArmorInit;
import dev.xqanzd.moonlitbroker.entity.spawn.MysteriousMerchantSpawner;
import dev.xqanzd.moonlitbroker.katana.KatanaInit;
import dev.xqanzd.moonlitbroker.screen.ModScreenHandlers;
import dev.xqanzd.moonlitbroker.weapon.transitional.TransitionalWeaponInit;
import dev.xqanzd.moonlitbroker.registry.ModBlocks;
import dev.xqanzd.moonlitbroker.registry.ModEntities;
import dev.xqanzd.moonlitbroker.registry.ModItemGroups;
import dev.xqanzd.moonlitbroker.registry.ModItems;
import dev.xqanzd.moonlitbroker.trade.command.BountyContractCommand;
import dev.xqanzd.moonlitbroker.trade.command.BountySubmitCommand;
import dev.xqanzd.moonlitbroker.trade.command.MoonlitCommands;
import dev.xqanzd.moonlitbroker.trade.loot.BountyDropHandler;
import dev.xqanzd.moonlitbroker.trade.loot.BountyProgressHandler;
import dev.xqanzd.moonlitbroker.trade.loot.LootTableInjector;
import dev.xqanzd.moonlitbroker.trade.loot.MobDropHandler;
import dev.xqanzd.moonlitbroker.trade.network.TradeNetworking;
import dev.xqanzd.moonlitbroker.registry.ModEntityTypeTags;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Mymodtest implements ModInitializer {
    public static final String MOD_ID = "xqanzd_moonlit_broker";
    private static final Logger LOGGER = LoggerFactory.getLogger(Mymodtest.class);

    // Phase 4: 每个世界维度一个生成器实例
    private static final Map<ServerWorld, MysteriousMerchantSpawner> spawners = new HashMap<>();

    @Override
    public void onInitialize() {
        LOGGER.info("[Mymodtest] Initializing Mysterious Merchant mod...");

        // 注册商人系统
        ModItems.register();
        ModBlocks.register();
        ModEntities.register();
        ModScreenHandlers.register();

        // 初始化 Katana 子系统
        KatanaInit.init();

        // 初始化 Armor 子系统
        ArmorInit.init();

        // 初始化过渡武器子系统
        TransitionalWeaponInit.init();

        // 初始化过渡护甲子系统
        TransitionalArmorInit.init();

        // 非胸甲 toughness 硬阀门校验（仅告警）
        ArmorBalanceValidator.validateNonChestplateToughnessCaps();

        // ArmorSpecs 覆写表自检：校验 key 是否对应已注册 item（防拼错）
        ArmorSpecs.validateOverrideKeys();

        // 将所有借用皮革模型的护甲绑定到炼药锅清洗行为
        ArmorDyeSupport.registerCauldronCleaningBehavior();

        // 注册创造模式物品分组
        ModItemGroups.init();

        // Trade System: 注册网络包
        TradeNetworking.registerServer();

        // Trade System: 注册战利品表注入
        LootTableInjector.register();

        // Trade System: 注册怪物掉落处理器
        MobDropHandler.register();

        // Trade System: 注册 Bounty 命令
        BountySubmitCommand.register();
        BountyContractCommand.register();

        // Trade System: 注册悬赏进度处理器
        BountyProgressHandler.register();

        // Trade System: 注册悬赏契约掉落处理器
        BountyDropHandler.register();

        // Trade System: 注册 /moonlit 命令
        MoonlitCommands.register();

        // 护栏 A: 服务器启动后验证掉落相关 tag 非空（datapack 已加载）
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            verifyTagNotEmpty(ModEntityTypeTags.SILVERNOTE_DROPPERS, "silvernote_droppers");
            verifyTagNotEmpty(ModEntityTypeTags.SILVERNOTE_NEUTRAL_DROPPERS, "silvernote_neutral_droppers");
            verifyTagNotEmpty(ModEntityTypeTags.BOUNTY_TARGETS, "bounty_targets");
            verifyTagNotEmpty(ModEntityTypeTags.SILVERNOTE_ELITE_DROPPERS, "silvernote_elite_droppers");
            verifyTagNotEmpty(ModEntityTypeTags.BOUNTY_ELITE_TARGETS, "bounty_elite_targets");
            verifyTagNotEmpty(ModEntityTypeTags.BOUNTY_RARE_TARGETS, "bounty_rare_targets");
            verifyTagNotEmpty(ModEntityTypeTags.BOUNTY_NEUTRAL_TARGETS, "bounty_neutral_targets");

            // 护栏 B: elite 子集校验 — bounty_elite_targets 必须是 bounty_targets 的子集
            validateEliteSubset();
        });

        // Phase 4: 注册世界 tick 事件，用于自然生成
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            // 只在主世界生成商人
            if (world.getRegistryKey() == World.OVERWORLD) {
                MysteriousMerchantSpawner spawner = spawners.computeIfAbsent(
                        world,
                        w -> new MysteriousMerchantSpawner());
                spawner.trySpawn(world);
            }
        });

        LOGGER.info("[Mymodtest] Mod initialization complete!");
    }

    /**
     * 护栏 A: 验证 entity type tag 在 datapack 加载后非空。
     * 若为空说明资源路径错误（如 entity_types/ vs entity_type/），立即在日志中报错。
     */
    private static void verifyTagNotEmpty(TagKey<EntityType<?>> tag, String name) {
        boolean empty = Registries.ENTITY_TYPE.streamTagsAndEntries()
                .noneMatch(pair -> pair.getFirst().equals(tag));
        if (empty) {
            LOGGER.error("[MoonTrade] TAG_EMPTY tag={} — 掉落系统不会工作！请检查 data/{}/tags/entity_type/{}.json 是否存在",
                    name, MOD_ID, name);
        } else {
            LOGGER.info("[MoonTrade] TAG_OK tag={}", name);
        }
    }

    /**
     * 护栏 B: 校验 bounty_elite_targets 必须是 bounty_targets 的子集。
     * 若存在孤立条目，打出 WARN 日志指引服主修数据包。不会崩服。
     */
    private static void validateEliteSubset() {
        Set<EntityType<?>> targets = new HashSet<>();
        Registries.ENTITY_TYPE.streamTagsAndEntries()
                .filter(pair -> pair.getFirst().equals(ModEntityTypeTags.BOUNTY_TARGETS))
                .flatMap(pair -> pair.getSecond().stream())
                .forEach(entry -> targets.add(entry.value()));

        List<String> orphans = new ArrayList<>();
        Registries.ENTITY_TYPE.streamTagsAndEntries()
                .filter(pair -> pair.getFirst().equals(ModEntityTypeTags.BOUNTY_ELITE_TARGETS))
                .flatMap(pair -> pair.getSecond().stream())
                .forEach(entry -> {
                    if (!targets.contains(entry.value())) {
                        orphans.add(Registries.ENTITY_TYPE.getId(entry.value()).toString());
                    }
                });

        if (!orphans.isEmpty()) {
            LOGGER.warn("[MoonTrade] ELITE_SUBSET_VIOLATION — bounty_elite_targets contains {} entries NOT in bounty_targets: {}",
                    orphans.size(), orphans);
            LOGGER.warn("[MoonTrade] Fix: add these to data/{}/tags/entity_type/bounty_targets.json or remove from bounty_elite_targets.json",
                    MOD_ID);
        } else {
            LOGGER.info("[MoonTrade] ELITE_SUBSET_OK — all bounty_elite_targets are in bounty_targets");
        }

        // Also validate bounty_rare_targets subset
        List<String> rareOrphans = new ArrayList<>();
        Registries.ENTITY_TYPE.streamTagsAndEntries()
                .filter(pair -> pair.getFirst().equals(ModEntityTypeTags.BOUNTY_RARE_TARGETS))
                .flatMap(pair -> pair.getSecond().stream())
                .forEach(entry -> {
                    if (!targets.contains(entry.value())) {
                        rareOrphans.add(Registries.ENTITY_TYPE.getId(entry.value()).toString());
                    }
                });

        if (!rareOrphans.isEmpty()) {
            LOGGER.warn("[MoonTrade] RARE_SUBSET_VIOLATION — bounty_rare_targets contains {} entries NOT in bounty_targets: {}",
                    rareOrphans.size(), rareOrphans);
            LOGGER.warn("[MoonTrade] Fix: add these to data/{}/tags/entity_type/bounty_targets.json or remove from bounty_rare_targets.json",
                    MOD_ID);
        } else {
            LOGGER.info("[MoonTrade] RARE_SUBSET_OK — all bounty_rare_targets are in bounty_targets");
        }
    }
}
