package dev.xqanzd.moonlitbroker.katana.sound;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {
    // 你的模组ID，必须小写且与 sounds.json 里的路径一致
    public static final String MODID = "xqanzd_moonlit_broker";

    // 定义音效事件：对应 sounds.json 里的 "moontrace.mark"
    public static final SoundEvent MOONTRACE_MARK = register("moontrace.mark");

    // 注册方法 (Fabric 风格)
    private static SoundEvent register(String id) {
        // 使用 Identifier.of (如果是旧版本 Fabric 可能需要 new Identifier(MODID, id))
        Identifier identifier = Identifier.of(MODID, id);
        return Registry.register(Registries.SOUND_EVENT, identifier, SoundEvent.of(identifier));
    }

    // 初始化方法
    public static void init() {
        // 空方法即可，调用它只是为了触发静态代码块加载
        System.out.println("Katana ModSounds Initialized!");
    }
}