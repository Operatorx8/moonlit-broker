package dev.xqanzd.moonlitbroker.armor;

import org.jetbrains.annotations.Nullable;

/**
 * 单件装备的数值覆写记录。
 * 所有字段均可为 null，表示"不覆写，回退到 ArmorConfig 默认值"。
 */
public record ArmorSpec(
        @Nullable Integer protection,
        @Nullable Float toughness,
        @Nullable Float knockbackResistance
) {
    /** 便捷构造：仅覆写 toughness */
    public static ArmorSpec ofToughness(float toughness) {
        return new ArmorSpec(null, toughness, null);
    }

    /** 获取 protection，缺省则回退到 fallback */
    public int protectionOr(int fallback) {
        return protection != null ? protection : fallback;
    }

    /** 获取 toughness，缺省则回退到 fallback */
    public float toughnessOr(float fallback) {
        return toughness != null ? toughness : fallback;
    }

    /** 获取 knockbackResistance，缺省则回退到 fallback */
    public float knockbackResistanceOr(float fallback) {
        return knockbackResistance != null ? knockbackResistance : fallback;
    }

    /** 是否有任何字段被覆写 */
    public boolean hasAnyOverride() {
        return protection != null || toughness != null || knockbackResistance != null;
    }
}
