package com.habaiwa.goetyrevenant.common;

import com.habaiwa.goetyrevenant.common.entity.CorpseEntity;

/**
 * 「灵魂视角高亮尸体」的钩子。
 *
 * <p>判断要用到客户端类，所以做成钩子：专用服务器上默认恒为 false，
 * 客户端启动时再替换成真实实现（{@code client.ClientHighlight}）。</p>
 */
public final class HighlightHook {
    /** 客户端实现。 */
    public interface Impl {
        boolean isHighlighted(CorpseEntity corpse);
    }

    private static Impl impl = corpse -> false;

    private HighlightHook() {
    }

    public static void set(Impl implementation) {
        impl = implementation;
    }

    public static boolean isHighlighted(CorpseEntity corpse) {
        return impl.isHighlighted(corpse);
    }
}
