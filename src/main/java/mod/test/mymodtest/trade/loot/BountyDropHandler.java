package mod.test.mymodtest.trade.loot;

import mod.test.mymodtest.registry.ModItems;
import mod.test.mymodtest.trade.item.BountyContractItem;
import mod.test.mymodtest.trade.item.MerchantMarkItem;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;

/**
 * Bounty v2: 怪物击杀 → 掉落悬赏契约
 * 0.5% 基础概率，需持有 MerchantMark，背包中无契约时才掉落
 *
 * --- 测试步骤 ---
 * 1) /give @p mymodtest:merchant_mark   (或首次右键商人自动获得)
 * 2) 击杀敌对生物，0.5% 概率掉落悬赏契约（查看日志 action=BOUNTY_CONTRACT_DROP）
 *    或临时命令: /give @p mymodtest:bounty_contract
 *    然后手动初始化: 使用 /bountycontract give <player> zombie 5
 * 3) 杀对应目标至进度满（日志 action=BOUNTY_PROGRESS）
 * 4) 右键神秘商人提交契约（日志 action=BOUNTY_SUBMIT_ACCEPT）
 * 5) 获得 Trade Scroll (Grade=NORMAL, Uses=3)
 * 6) 用 Scroll 刷新 NORMAL 页，确认:
 *    - 日志中 offersHash 变化（对比 OPEN_UI 前后）
 *    - Scroll NBT Uses 减 1
 *    - 日志出现 action=REFRESH / NORMAL_BUILD
 */
public class BountyDropHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(BountyDropHandler.class);
    private static final Random RANDOM = new Random();

    private static final float BASE_DROP_CHANCE = 0.005f; // 0.5%

    private record RequiredRange(int min, int max) {
        private int roll(Random random) {
            return min + random.nextInt(max - min + 1);
        }
    }

    // 仅对这 5 类目标生效，required 按目标类型区间随机
    private static final Map<String, RequiredRange> TARGET_REQUIRED_RANGES = Map.of(
            "minecraft:zombie", new RequiredRange(3, 6),
            "minecraft:skeleton", new RequiredRange(3, 6),
            "minecraft:spider", new RequiredRange(4, 7),
            "minecraft:creeper", new RequiredRange(3, 5),
            "minecraft:enderman", new RequiredRange(2, 4));

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(BountyDropHandler::onMobDeath);
        LOGGER.info("[MoonTrade] action=BOUNTY_DROP_HANDLER_REGISTER side=S");
    }

    private static void onMobDeath(LivingEntity entity, DamageSource source) {
        // 仅玩家击杀
        if (!(source.getAttacker() instanceof ServerPlayerEntity player)) return;
        Identifier mobId = Registries.ENTITY_TYPE.getId(entity.getType());
        String mobKey = mobId.toString();
        RequiredRange requiredRange = TARGET_REQUIRED_RANGES.get(mobKey);
        // 仅目标池内生物参与悬赏掉落
        if (requiredRange == null) return;

        // Gate: 需持有 MerchantMark
        if (!MerchantMarkItem.playerHasValidMark(player)) return;

        // 背包中已有契约则不掉落（maxCount=1 精神）
        if (playerHasBountyContract(player)) return;

        // 概率判定
        float roll = RANDOM.nextFloat();
        if (roll >= BASE_DROP_CHANCE) return;
        int required = requiredRange.roll(RANDOM);

        // 生成契约
        ItemStack contract = new ItemStack(ModItems.BOUNTY_CONTRACT, 1);
        BountyContractItem.initialize(contract, mobKey, required);
        entity.dropStack(contract);

        LOGGER.info("[MoonTrade] action=BOUNTY_CONTRACT_DROP mob={} required={} roll={} side=S player={}",
                mobKey, required, roll, player.getName().getString());
    }

    private static boolean playerHasBountyContract(ServerPlayerEntity player) {
        for (ItemStack stack : player.getInventory().main) {
            if (stack.isOf(ModItems.BOUNTY_CONTRACT)) return true;
        }
        for (ItemStack stack : player.getInventory().offHand) {
            if (stack.isOf(ModItems.BOUNTY_CONTRACT)) return true;
        }
        return false;
    }
}
