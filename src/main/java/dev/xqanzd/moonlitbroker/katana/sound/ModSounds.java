package dev.xqanzd.moonlitbroker.katana.sound;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {
    public static final String MODID = "xqanzd_moonlit_broker";

    // Merchant
    public static final SoundEvent MERCHANT_DRINK = register("merchant.drink");

    // Katana core
    public static final SoundEvent MOONTRACE_MARK = register("moontrace.mark");
    public static final SoundEvent MOONTRACE_CONSUME_CRIT = register("blade.moontrace_crit");
    public static final SoundEvent LIFECUT_STRIKE = register("blade.lifecut_strike");
    public static final SoundEvent LIFECUT_HEARTBEAT = register("blade.heartbeat");
    public static final SoundEvent ECLIPSE_WHISPER = register("blade.eclipse_whisper");
    public static final SoundEvent ECLIPSE_AURA = register("blade.eclipse_aura");
    public static final SoundEvent ECLIPSE_DEPTH = register("blade.eclipse_depth");
    public static final SoundEvent OBLIVION_READ = register("blade.oblivion_read");
    public static final SoundEvent OBLIVION_ENCHANT = register("blade.oblivion_enchant");
    public static final SoundEvent OBLIVION_CAUSALITY = register("blade.oblivion_causality");
    public static final SoundEvent OBLIVION_SHIFT = register("blade.oblivion_shift");
    public static final SoundEvent OBLIVION_DOOM = register("blade.oblivion_doom");
    public static final SoundEvent NMAP_CRIT = register("blade.nmap_crit");
    public static final SoundEvent NMAP_FIREWALL = register("blade.nmap_firewall");
    public static final SoundEvent NMAP_SCAN_ON = register("blade.nmap_scan_on");
    public static final SoundEvent NMAP_SCAN_BREAK = register("blade.nmap_scan_break");

    // Armor
    public static final SoundEvent ARMOR_RETRACER_GUARD = register("armor.retracer_guard");
    public static final SoundEvent ARMOR_STEALTH_CHARGE = register("armor.stealth_charge");
    public static final SoundEvent ARMOR_STEALTH_FALL_PAD = register("armor.stealth_fall_pad");
    public static final SoundEvent ARMOR_SENTINEL_PULSE = register("armor.sentinel_pulse");

    private static SoundEvent register(String id) {
        Identifier identifier = Identifier.of(MODID, id);
        return Registry.register(Registries.SOUND_EVENT, identifier, SoundEvent.of(identifier));
    }

    public static void init() {
        // Trigger static field initialization.
    }
}
