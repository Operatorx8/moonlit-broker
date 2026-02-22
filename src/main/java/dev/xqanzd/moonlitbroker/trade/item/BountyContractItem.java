package dev.xqanzd.moonlitbroker.trade.item;

import dev.xqanzd.moonlitbroker.registry.ModEntityTypeTags;
import dev.xqanzd.moonlitbroker.registry.ModItems;
import dev.xqanzd.moonlitbroker.trade.TradeConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 悬赏契约物品
 * NBT: BountyTarget, BountyRequired, BountyProgress, BountyCompleted, BountyTier, BountySchema
 */
public class BountyContractItem extends Item {
    private static final Logger LOGGER = LoggerFactory.getLogger(BountyContractItem.class);

    private static final String NBT_TARGET = "BountyTarget";
    private static final String NBT_REQUIRED = "BountyRequired";
    private static final String NBT_PROGRESS = "BountyProgress";
    private static final String NBT_COMPLETED = "BountyCompleted";
    public static final String NBT_TIER = "BountyTier";
    public static final String NBT_SCHEMA = "BountySchema";

    public static final String TIER_COMMON = "common";
    public static final String TIER_ELITE = "elite";
    public static final String TIER_RARE = "rare";

    private static final Set<String> VALID_TIERS = Set.of(TIER_COMMON, TIER_ELITE, TIER_RARE);
    private static volatile boolean unknownTierWarned = false;

    public BountyContractItem(Settings settings) {
        super(settings);
    }

    // ========== NBT Helpers ==========

    public static String getTarget(ItemStack stack) {
        NbtCompound nbt = getNbt(stack);
        return nbt != null && nbt.contains(NBT_TARGET) ? nbt.getString(NBT_TARGET) : "";
    }

    public static int getRequired(ItemStack stack) {
        NbtCompound nbt = getNbt(stack);
        return nbt != null && nbt.contains(NBT_REQUIRED) ? nbt.getInt(NBT_REQUIRED) : 0;
    }

    public static int getProgress(ItemStack stack) {
        NbtCompound nbt = getNbt(stack);
        return nbt != null && nbt.contains(NBT_PROGRESS) ? nbt.getInt(NBT_PROGRESS) : 0;
    }

    public static boolean isCompleted(ItemStack stack) {
        NbtCompound nbt = getNbt(stack);
        return nbt != null && nbt.contains(NBT_COMPLETED) && nbt.getBoolean(NBT_COMPLETED);
    }

    public static String getTier(ItemStack stack) {
        NbtCompound nbt = getNbt(stack);
        if (nbt == null || !nbt.contains(NBT_TIER)) return "";
        return normalizeTier(nbt.getString(NBT_TIER));
    }

    /**
     * Normalize a tier string: unknown values fall back to TIER_COMMON with a one-time warning.
     */
    public static String normalizeTier(String tier) {
        if (tier == null || tier.isEmpty()) return TIER_COMMON;
        if (VALID_TIERS.contains(tier)) return tier;
        if (!unknownTierWarned) {
            unknownTierWarned = true;
            LOGGER.warn("[BountyContract] action=UNKNOWN_TIER_NORMALIZED raw={} fallback={}", tier, TIER_COMMON);
        }
        return TIER_COMMON;
    }

    public static int getSchema(ItemStack stack) {
        NbtCompound nbt = getNbt(stack);
        return nbt != null && nbt.contains(NBT_SCHEMA) ? nbt.getInt(NBT_SCHEMA) : 0;
    }

    /**
     * 严格完成判定：NBT boolean + progress >= required + required > 0
     */
    public static boolean isCompletedStrict(ItemStack stack) {
        NbtCompound nbt = getNbt(stack);
        if (nbt == null) return false;
        boolean flag = nbt.contains(NBT_COMPLETED) && nbt.getBoolean(NBT_COMPLETED);
        int progress = nbt.contains(NBT_PROGRESS) ? nbt.getInt(NBT_PROGRESS) : 0;
        int required = nbt.contains(NBT_REQUIRED) ? nbt.getInt(NBT_REQUIRED) : 0;
        return flag && required > 0 && progress >= required;
    }

    /**
     * 增加进度，若达到 required 则标记 completed
     *
     * @return true if newly completed
     */
    public static boolean incrementProgress(ItemStack stack) {
        NbtCompound nbt = getOrCreateNbt(stack);
        int progress = nbt.contains(NBT_PROGRESS) ? nbt.getInt(NBT_PROGRESS) : 0;
        int required = nbt.contains(NBT_REQUIRED) ? nbt.getInt(NBT_REQUIRED) : 0;
        if (progress >= required) {
            return false; // already done
        }
        progress++;
        nbt.putInt(NBT_PROGRESS, progress);
        boolean completed = progress >= required;
        if (completed) {
            nbt.putBoolean(NBT_COMPLETED, true);
        }
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        return completed;
    }

    /**
     * 初始化一张新契约（幂等：若已有核心字段则不覆盖）
     */
    public static void initialize(ItemStack stack, String targetEntityId, int required) {
        NbtCompound nbt = getOrCreateNbt(stack);
        // 幂等：已有 target/required 等核心字段时不重新 roll
        if (nbt.contains(NBT_TARGET) && nbt.contains(NBT_REQUIRED)) {
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            return;
        }
        nbt.putString(NBT_TARGET, targetEntityId);
        nbt.putInt(NBT_REQUIRED, required);
        nbt.putInt(NBT_PROGRESS, 0);
        nbt.putBoolean(NBT_COMPLETED, false);
        // Tier/Schema: 写入默认值（调用方可覆盖）
        if (!nbt.contains(NBT_TIER)) {
            nbt.putString(NBT_TIER, TIER_COMMON);
        }
        if (!nbt.contains(NBT_SCHEMA)) {
            nbt.putInt(NBT_SCHEMA, TradeConfig.BOUNTY_SCHEMA_VERSION);
        }
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    /**
     * 完整初始化（含 tier + schema），供 BountyDropHandler 和命令使用。
     */
    public static void initializeWithTier(ItemStack stack, String targetEntityId, int required, String tier) {
        NbtCompound nbt = getOrCreateNbt(stack);
        if (nbt.contains(NBT_TARGET) && nbt.contains(NBT_REQUIRED)) {
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            return;
        }
        nbt.putString(NBT_TARGET, targetEntityId);
        nbt.putInt(NBT_REQUIRED, required);
        nbt.putInt(NBT_PROGRESS, 0);
        nbt.putBoolean(NBT_COMPLETED, false);
        nbt.putString(NBT_TIER, tier);
        nbt.putInt(NBT_SCHEMA, TradeConfig.BOUNTY_SCHEMA_VERSION);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    /**
     * 检查契约目标是否仍然有效（在 registry 中存在且仍在 bounty_targets tag 内）。
     * @return null 表示有效，非 null 字符串描述失效原因
     */
    public static String validateTarget(ItemStack stack) {
        String target = getTarget(stack);
        if (target.isEmpty()) {
            return "empty_target";
        }
        Identifier targetId = Identifier.tryParse(target);
        if (targetId == null) {
            return "invalid_identifier";
        }
        // 检查 registry 中是否存在（默认 entity type = pig，如果不存在也会返回 pig）
        EntityType<?> entityType = Registries.ENTITY_TYPE.get(targetId);
        Identifier resolvedId = Registries.ENTITY_TYPE.getId(entityType);
        if (!resolvedId.equals(targetId)) {
            return "unresolved_entity_type";
        }
        if (!entityType.isIn(ModEntityTypeTags.BOUNTY_TARGETS)) {
            return "not_in_bounty_targets";
        }
        return null; // valid
    }

    /**
     * 快捷方法：目标是否仍然有效
     */
    public static boolean isTargetStillValid(ItemStack stack) {
        return validateTarget(stack) == null;
    }

    /**
     * 检查被击杀的实体是否匹配此契约目标
     */
    public static boolean matchesTarget(ItemStack stack, EntityType<?> killedType) {
        String target = getTarget(stack);
        if (target.isEmpty())
            return false;
        Identifier targetId = Identifier.tryParse(target);
        if (targetId == null)
            return false;
        Identifier killedId = Registries.ENTITY_TYPE.getId(killedType);
        return targetId.equals(killedId);
    }

    /**
     * 判断是否为有效的悬赏契约
     */
    public static boolean isValidContract(ItemStack stack) {
        return stack.isOf(ModItems.BOUNTY_CONTRACT) && !getTarget(stack).isEmpty();
    }

    // ========== Internal ==========

    private static NbtCompound getNbt(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null)
            return null;
        return component.copyNbt();
    }

    private static NbtCompound getOrCreateNbt(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component != null) {
            return component.copyNbt();
        }
        return new NbtCompound();
    }
}
