package dev.xqanzd.moonlitbroker.trade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Random B 抽取器
 * <p>
 * 规则：
 * 1. 每变体抽 1 件
 * 2. anti-repeat：同一玩家同一变体不连续给同一 random B（最多重抽 1 次）
 * 3. 每页最多 1 EPIC：如果 anchor 是 EPIC，则 random 必须避开 EPIC
 */
public final class RandomBSelector {

    private RandomBSelector() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    /**
     * anti-repeat 记录: playerUUID:variant -> 上次抽到的 itemId
     */
    private static final Map<String, String> LAST_RANDOM_B = new ConcurrentHashMap<>();

    /**
     * 为指定玩家和变体抽取 1 件 Random B
     *
     * @param playerUuid 玩家 UUID
     * @param variant    商人变体
     * @param random     随机源
     * @return 抽到的 itemId，如果池为空返回 null
     */
    public static String select(UUID playerUuid, MerchantVariant variant, Random random) {
        String bAnchor = VariantAnchorConfig.getBAnchor(variant);
        boolean anchorIsEpic = VariantAnchorConfig.isEpic(bAnchor);

        List<String> pool = new ArrayList<>(VariantAnchorConfig.getBRandomPool(variant));
        if (pool.isEmpty()) {
            LOGGER.warn("[MoonTrace|Trade|RandomB] variant={} pool is empty!", variant);
            return null;
        }

        // 如果 anchor 是 EPIC，从池中移除所有 EPIC（每页最多 1 EPIC）
        if (anchorIsEpic) {
            pool.removeIf(VariantAnchorConfig::isEpic);
            if (pool.isEmpty()) {
                LOGGER.warn("[MoonTrace|Trade|RandomB] variant={} pool empty after EPIC filter", variant);
                return null;
            }
        }

        String antiRepeatKey = playerUuid.toString() + ":" + variant.name();
        String lastPick = LAST_RANDOM_B.get(antiRepeatKey);

        // 第一次抽取
        String pick = pool.get(random.nextInt(pool.size()));

        // anti-repeat：如果和上次一样，最多重抽 1 次
        if (pick.equals(lastPick) && pool.size() > 1) {
            String retry = pool.get(random.nextInt(pool.size()));
            if (!retry.equals(lastPick)) {
                pick = retry;
            } else {
                // 仍然撞了，换下一个候选
                for (String candidate : pool) {
                    if (!candidate.equals(lastPick)) {
                        pick = candidate;
                        break;
                    }
                }
            }
        }

        LAST_RANDOM_B.put(antiRepeatKey, pick);
        LOGGER.info("[MoonTrace|Trade|RandomB] variant={} player={} picked={} anchorEpic={}",
                variant, playerUuid, pick, anchorIsEpic);
        return pick;
    }

    /**
     * 清除指定玩家的 anti-repeat 记录
     */
    public static void clearPlayer(UUID playerUuid) {
        for (MerchantVariant v : MerchantVariant.values()) {
            LAST_RANDOM_B.remove(playerUuid.toString() + ":" + v.name());
        }
    }
}
