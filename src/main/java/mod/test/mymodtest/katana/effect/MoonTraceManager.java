package mod.test.mymodtest.katana.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MoonTraceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    // EntityId -> State
    private static final Map<Integer, MoonTraceState> marks = new ConcurrentHashMap<>();

    public record MoonTraceState(int expireAtTick, UUID sourcePlayer) {}

    public static void applyMark(LivingEntity target, PlayerEntity source, int durationTicks) {
        int expireAt = (int) target.getWorld().getTime() + durationTicks;
        marks.put(target.getId(), new MoonTraceState(expireAt, source.getUuid()));

        if (MoonTraceConfig.DEBUG) {
            LOGGER.info("[MoonTrace] Applied mark to {} by {}, expires at tick {}",
                target.getName().getString(), source.getName().getString(), expireAt);
        }
    }

    public static Optional<MoonTraceState> getAndConsume(LivingEntity target, PlayerEntity attacker) {
        MoonTraceState state = marks.get(target.getId());
        if (state == null) return Optional.empty();

        int now = (int) target.getWorld().getTime();
        if (state.expireAtTick() < now) {
            marks.remove(target.getId());
            if (MoonTraceConfig.DEBUG) {
                LOGGER.info("[MoonTrace] Mark on {} expired", target.getName().getString());
            }
            return Optional.empty();
        }

        if (!state.sourcePlayer().equals(attacker.getUuid())) {
            if (MoonTraceConfig.DEBUG) {
                LOGGER.info("[MoonTrace] Attacker {} is not the source", attacker.getName().getString());
            }
            return Optional.empty();
        }

        marks.remove(target.getId());
        if (MoonTraceConfig.DEBUG) {
            LOGGER.info("[MoonTrace] Consumed mark on {} by {}",
                target.getName().getString(), attacker.getName().getString());
        }
        return Optional.of(state);
    }

    public static void tickCleanup(long currentTick) {
        int removed = 0;
        var iterator = marks.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expireAtTick() < currentTick) {
                iterator.remove();
                removed++;
            }
        }
        if (MoonTraceConfig.DEBUG && removed > 0) {
            LOGGER.info("[MoonTrace] Cleaned up {} expired marks", removed);
        }
    }
}
