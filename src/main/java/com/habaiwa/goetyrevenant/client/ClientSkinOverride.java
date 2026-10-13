package com.habaiwa.goetyrevenant.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 客户端记着「哪名玩家现在顶的是谁的皮」，连皮肤属性一起存。 */
public final class ClientSkinOverride {
    /** 一条覆写：皮肤属性的 base64 value + signature。 */
    public record Skin(String value, String signature) {
    }

    private static final Map<UUID, Skin> OVERRIDES = new HashMap<>();

    private ClientSkinOverride() {
    }

    public static void set(UUID player, String value, String signature) {
        if (value == null || value.isEmpty()) {
            OVERRIDES.remove(player);
        } else {
            OVERRIDES.put(player, new Skin(value, signature));
        }
    }

    public static Skin get(UUID player) {
        return OVERRIDES.get(player);
    }
}
