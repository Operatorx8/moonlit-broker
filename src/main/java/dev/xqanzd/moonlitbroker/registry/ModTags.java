package dev.xqanzd.moonlitbroker.registry;

import dev.xqanzd.moonlitbroker.armor.ArmorConfig;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

/**
 * 自定义 Tag 定义
 */
public final class ModTags {
    private ModTags() {}

    public static final class Items {
        /**
         * 核心战利品：护腿1（走私者之胫）对此类物品的额外/双倍掉落概率减半
         * Tag 路径: data/xqanzd_moonlit_broker/tags/item/core_loot.json
         */
        public static final TagKey<Item> CORE_LOOT = TagKey.of(
                RegistryKeys.ITEM,
                Identifier.of(ArmorConfig.MOD_ID, "core_loot")
        );

        /**
         * 神器 katana 统一标签
         * Tag 路径: data/xqanzd_moonlit_broker/tags/item/katana.json
         */
        public static final TagKey<Item> KATANA = TagKey.of(
                RegistryKeys.ITEM,
                Identifier.of(ArmorConfig.MOD_ID, "katana")
        );

        private Items() {}
    }
}
