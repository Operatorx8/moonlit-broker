package mod.test.mymodtest.katana.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 月蚀标记状态管理器
 *
 * 用于追踪被月蚀标记的目标
 */
public class EclipseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Eclipse");

    private static final Map<Integer, EclipseState> marks = new ConcurrentHashMap<>();

    public record EclipseState(int expireAtTick, UUID sourcePlayer) {}

    /**
     * 检查目标是否有月蚀标记（用于护甲穿透判定）
     */
    public static boolean hasMark(LivingEntity target) {
        EclipseState state = marks.get(target.getId());
        if (state == null) return false;

        int now = (int) target.getWorld().getTime();
        if (state.expireAtTick() < now) {
            marks.remove(target.getId());
            return false;
        }
        return true;
    }

    /**
     * 施加月蚀标记
     */
    public static void applyMark(LivingEntity target, PlayerEntity source, int durationTicks) {
        int expireAt = (int) target.getWorld().getTime() + durationTicks;
        marks.put(target.getId(), new EclipseState(expireAt, source.getUuid()));

        if (EclipseConfig.DEBUG) {
            LOGGER.info("[Eclipse] Applied mark to {} for {} ticks",
                target.getName().getString(), durationTicks);
        }
    }

    /**
     * 清理过期标记
     */
    public static void tickCleanup(long currentTick) {
        marks.entrySet().removeIf(e -> e.getValue().expireAtTick() < currentTick);
    }
}
