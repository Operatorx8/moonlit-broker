package dev.xqanzd.moonlitbroker.registry;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModTooltips {
    private static final String MOD_ID = "xqanzd_moonlit_broker";
    private static final Map<String, List<String>> KATANA_INSCRIPTIONS = createKatanaInscriptions();
    private static boolean initialized;

    private ModTooltips() {}

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ItemTooltipCallback.EVENT.register((stack, context, type, tooltip) -> appendTooltip(stack, type, tooltip));
    }

    private static void appendTooltip(ItemStack stack, TooltipType tooltipType, List<Text> tooltip) {
        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (!MOD_ID.equals(id.getNamespace())) {
            return;
        }

        String path = id.getPath();
        List<String> inscriptions = TooltipHelper.isKatana(path) ? KATANA_INSCRIPTIONS.get(path) : null;

        TooltipComposer.compose(
                MOD_ID,
                path,
                stack,
                tooltipType,
                tooltip,
                inscriptions
        );
    }

    private static Map<String, List<String>> createKatanaInscriptions() {
        Map<String, List<String>> map = new HashMap<>();
        map.put("moon_glow_katana", List.of("朧に霞む春の月", "淡き光、掌に残る"));
        map.put("regret_blade", List.of("癒えぬ傷、名を呼ぶ"));
        map.put("eclipse_blade", List.of("月影、静かに喰らう"));
        map.put("oblivion_edge", List.of("思念、闇に沈む"));
        map.put("nmap_katana", List.of("兆しは既に在り"));
        return map;
    }
}
