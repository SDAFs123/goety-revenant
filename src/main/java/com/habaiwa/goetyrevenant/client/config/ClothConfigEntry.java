package com.habaiwa.goetyrevenant.client.config;

/**
 * 把配置界面挂到**原版模组列表的「配置」按钮**上。
 *
 * <p>用 Forge 官方的 {@code ConfigScreenHandler.ConfigScreenFactory} 扩展点，
 * 不依赖 ModMenu / Configured 这类前端模组。</p>
 *
 * <p><b>只有装了 Cloth Config 时才会调用这个方法</b> —— 调用点在外面做了两层守卫，
 * 所以没装 Cloth 的整合包不会因为找不到类而崩。</p>
 */
public final class ClothConfigEntry {
    private ClothConfigEntry() {
    }

    public static void register() {
        net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(
                net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> ClothConfigScreen.create(parent)));
    }
}
