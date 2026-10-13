package com.habaiwa.goetyrevenant.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * **把那句话画到屏幕上**。
 *
 * <h3>为什么需要这个类</h3>
 * <p>{@link WhisperIntro} 负责"说什么、什么时候说"，但它<b>自己不挂在任何渲染事件上</b> ——
 * 于是它写好了却永远没人调用，字不出现、声音也不响
 * （声音是在 {@code render} 里按段触发的）。</p>
 *
 * <p>这类"写好了但没接线"的问题是静默的：编译通过、日志干净、什么都没发生。
 * 所以这里单独一个 overlay 把它接上，挂在所有 HUD 之上。</p>
 */
public final class WhisperOverlay implements IGuiOverlay {
    @Override
    public void render(net.minecraftforge.client.gui.overlay.ForgeGui gui, GuiGraphics graphics,
                       float partialTick, int width, int height) {
        WhisperIntro.render(graphics, Minecraft.getInstance().font, width, height);
    }
}
