package com.habaiwa.goetyrevenant.common.util;

import net.minecraft.network.chat.Component;

/**
 * **按键名显示** —— 提示永远跟着玩家改过的按键走。
 *
 * <p>做法：反射调客户端 {@code KeyMapping.createNameGetter(keyName)}，
 * 它返回一个<b>实时解析</b>的组件 —— 玩家改完键，提示里显示的按键名跟着变。</p>
 *
 * <p>专用服务器上没有这个类（{@code net.minecraft.client.KeyMapping} 是客户端类），
 * 反射失败就退回按键的翻译名（比如"七窍"），不会崩。</p>
 */
public final class KeyNames {
    private KeyNames() {
    }

    /** 给出某个键位（如 {@code key.goety_revenant.body}）当前绑定的按键显示名。 */
    public static Component key(String keyName) {
        try {
            Class<?> km = Class.forName("net.minecraft.client.KeyMapping");
            java.lang.reflect.Method getter = km.getMethod("createNameGetter", String.class);
            return (Component) getter.invoke(null, keyName);
        } catch (Throwable t) {
            return Component.translatable(keyName);
        }
    }
}
