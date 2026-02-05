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

    public enum MarkType {
        MOONLIGHT_MARK,
        LIGHT_MARK
    }

    // EntityId -> State (distinct maps to keep moonlight and light marks independent)
    private static final Map<Integer, MoonTraceState> moonlightMarks = new ConcurrentHashMap<>();
    private static final Map<Integer, MoonTraceState> lightMarks = new ConcurrentHashMap<>();

    public record MoonTraceState(int expireAtTick, UUID sourcePlayer, MarkType markType) {}

    public static void applyMoonlightMark(LivingEntity target, PlayerEntity source, int durationTicks) {
        int expireAt = (int) target.getWorld().getTime() + durationTicks;
        moonlightMarks.put(target.getId(), new MoonTraceState(expireAt, source.getUuid(), MarkType.MOONLIGHT_MARK));

        if (MoonTraceConfig.DEBUG) {
            LOGGER.info("[MoonTrace] Applied {} to {} by {}, expires at tick {}",
                MarkType.MOONLIGHT_MARK, target.getName().getString(), source.getName().getString(), expireAt);
        }
    }

    public static void applyLightMark(LivingEntity target, PlayerEntity source, int durationTicks) {
        int expireAt = (int) target.getWorld().getTime() + durationTicks;
        lightMarks.put(target.getId(), new MoonTraceState(expireAt, source.getUuid(), MarkType.LIGHT_MARK));

        if (MoonTraceConfig.DEBUG) {
            LOGGER.info("[MoonTrace] Applied {} to {} by {}, expires at tick {}",
                MarkType.LIGHT_MARK, target.getName().getString(), source.getName().getString(), expireAt);
        }
    }

    public static Optional<MoonTraceState> getAndConsume(LivingEntity target, PlayerEntity attacker) {
        Optional<MoonTraceState> moonlight = getAndConsumeFromMap(target, attacker, moonlightMarks);
        if (moonlight.isPresent()) return moonlight;
        return getAndConsumeFromMap(target, attacker, lightMarks);
    }

    private static Optional<MoonTraceState> getAndConsumeFromMap(LivingEntity target, PlayerEntity attacker,
                                                                 Map<Integer, MoonTraceState> marks) {
        MoonTraceState state = marks.get(target.getId());
        if (state == null) {
            return Optional.empty();
        }

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
            LOGGER.info("[MoonTrace] Consumed {} on {} by {}",
                state.markType(), target.getName().getString(), attacker.getName().getString());
        }
        return Optional.of(state);
    }

    public static void tickCleanup(long currentTick) {
        int removed = 0;
        var moonlightIterator = moonlightMarks.entrySet().iterator();
        while (moonlightIterator.hasNext()) {
            if (moonlightIterator.next().getValue().expireAtTick() < currentTick) {
                moonlightIterator.remove();
                removed++;
            }
        }
        var lightIterator = lightMarks.entrySet().iterator();
        while (lightIterator.hasNext()) {
            if (lightIterator.next().getValue().expireAtTick() < currentTick) {
                lightIterator.remove();
                removed++;
            }
        }
        if (MoonTraceConfig.DEBUG && removed > 0) {
            LOGGER.info("[MoonTrace] Cleaned up {} expired marks", removed);
        }
    }
}
