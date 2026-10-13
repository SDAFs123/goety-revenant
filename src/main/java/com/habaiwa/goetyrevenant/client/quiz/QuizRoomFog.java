package com.habaiwa.goetyrevenant.client.quiz;

import com.habaiwa.goetyrevenant.GoetyRevenant;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * **转场与那片白**。
 *
 * <ul>
 *   <li><b>变暗</b>：跳进井的那一下屏幕迅速暗下去 —— 遮住换场景那一刻</li>
 *   <li><b>变亮</b>：到那边之后逐渐亮起来 —— 你是"落"进那片白的</li>
 *   <li><b>白雾</b>：白房间的 6~8 格之外全部化成白色，于是它看起来没有边界</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = GoetyRevenant.MODID, value = Dist.CLIENT)
public final class QuizRoomFog {
    /**
     * 雾只用来把**地平线**融进白里，不能贴脸 —— 这片平原要看到很远才有"深度"。
     * 原来 8 格的雾等于是把远景掐掉了，那看起来就是"糊成一片"。
     */
    private static final float FAR = 160.0F;
    private static final float NEAR = 24.0F;

    private QuizRoomFog() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        QuizRoomClient.tick(0.05F);
        // 该答题却没在答题屏上 → 补开（跨维度的加载屏会顶掉我们的界面）
        com.habaiwa.goetyrevenant.client.state.QuizClientState.ensureScreen();
    }

    /** 盖黑：那一下的暗，以及亮起来的过程。 */
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        if (!VanillaGuiOverlay.HOTBAR.id().equals(event.getOverlay().id())) {
            return;
        }
        float dark = QuizRoomClient.darkness();
        if (dark <= 0.01F) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int alpha = (int) (Math.min(1.0F, dark) * 255.0F);
        graphics.fill(0, 0, event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight(), alpha << 24);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!QuizRoomClient.inside()) {
            return;
        }
        event.setRed(1.0F);
        event.setGreen(1.0F);
        event.setBlue(1.0F);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!QuizRoomClient.inside()) {
            return;
        }
        event.setNearPlaneDistance(NEAR);
        event.setFarPlaneDistance(FAR);
        event.setCanceled(true);
    }
}
