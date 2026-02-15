package dev.xqanzd.moonlitbroker.registry;

import net.minecraft.entity.EntityType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

/**
 * Entity type tags for loot/drop routing.
 */
public final class ModEntityTypeTags {
    private ModEntityTypeTags() {
    }

    /** data/xqanzd_moonlit_broker/tags/entity_type/silvernote_droppers.json */
    public static final TagKey<EntityType<?>> SILVERNOTE_DROPPERS = TagKey.of(
            RegistryKeys.ENTITY_TYPE,
            Identifier.of(ModItems.MOD_ID, "silvernote_droppers"));

    /** data/xqanzd_moonlit_broker/tags/entity_type/silvernote_neutral_droppers.json */
    public static final TagKey<EntityType<?>> SILVERNOTE_NEUTRAL_DROPPERS = TagKey.of(
            RegistryKeys.ENTITY_TYPE,
            Identifier.of(ModItems.MOD_ID, "silvernote_neutral_droppers"));

    /** data/xqanzd_moonlit_broker/tags/entity_type/bounty_targets.json */
    public static final TagKey<EntityType<?>> BOUNTY_TARGETS = TagKey.of(
            RegistryKeys.ENTITY_TYPE,
            Identifier.of(ModItems.MOD_ID, "bounty_targets"));

    /** data/xqanzd_moonlit_broker/tags/entity_type/silvernote_elite_droppers.json */
    public static final TagKey<EntityType<?>> SILVERNOTE_ELITE_DROPPERS = TagKey.of(
            RegistryKeys.ENTITY_TYPE,
            Identifier.of(ModItems.MOD_ID, "silvernote_elite_droppers"));

    /** data/xqanzd_moonlit_broker/tags/entity_type/bounty_elite_targets.json */
    public static final TagKey<EntityType<?>> BOUNTY_ELITE_TARGETS = TagKey.of(
            RegistryKeys.ENTITY_TYPE,
            Identifier.of(ModItems.MOD_ID, "bounty_elite_targets"));
}
