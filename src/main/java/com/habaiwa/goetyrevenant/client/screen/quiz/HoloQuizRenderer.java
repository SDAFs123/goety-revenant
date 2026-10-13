package com.habaiwa.goetyrevenant.client.screen.quiz;

import com.habaiwa.goetyrevenant.GoetyRevenant;
import com.habaiwa.goetyrevenant.client.state.QuizClientState;
import com.habaiwa.goetyrevenant.client.quiz.QuizRoomClient;
import com.habaiwa.goetyrevenant.common.sequence.SevenQuestions;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * **全息答题界面** —— 选项不是贴在屏幕上，而是像投影一样浮在那片白里。
 *
 * <h3>它长什么样</h3>
 * <pre>
 *        ╔══════════════╗
 *        ║ 一截还在烧的蜡烛 ║   ← 半透明的光板 + 发亮的边
 *        ╚══════════════╝      + 一条慢慢扫过的亮线
 * </pre>
 * <ul>
 *   <li><b>光板</b>：半透明的青色底，随风轻轻浮沉</li>
 *   <li><b>边</b>：亮一圈；被准星看中的那块会亮到底、还会微微放大、并向你挪近</li>
 *   <li><b>扫描线</b>：一条横线自上而下慢慢扫过，全息投影的味道</li>
 *   <li><b>朝向</b>：四块围成一圈，面朝站在中间的<b>你</b> —— 转个头就能看遍四个选项</li>
 * </ul>
 *
 * <h3>怎么选</h3>
 * <p>把准星对上去（看中的那块会亮），然后左键（或回车）。
 * 选中的那块会散成光屑，下一问的四块从更远处浮回来。</p>
 */
@Mod.EventBusSubscriber(modid = GoetyRevenant.MODID, value = Dist.CLIENT)
public final class HoloQuizRenderer {
    /** 白空间的中心（人站这儿）。 */
    private static final double CX = 0.5D;
    private static final double CZ = 0.5D;
    private static final double PANEL_Y = 65.35D;
    /** 面板离中心多远。 */
    private static final double RING = 2.6D;
    /** 面板尺寸（世界单位）。 */
    private static final float PANEL_W = 1.45F;
    private static final float PANEL_H = 0.34F;
    /** 看中判定：视线与"玩家→面板"的夹角小于这个度数才算。 */
    public static final double AIM_DEGREES = 11.0D;

    /** 这一帧被看中的是第几块（-1 = 没有）。 */
    private static int aimed = -1;

    private HoloQuizRenderer() {
    }

    /** 被看中的那一块（给选择逻辑用）。 */
    public static int aimed() {
        return aimed;
    }

    /**
     * 四块面板的世界坐标：围成一圈，等分。
     */
    public static Vec3 panelPos(int index, int count) {
        double a = Math.toRadians(45.0D + index * (360.0D / Math.max(1, count)));
        return new Vec3(CX + Math.sin(a) * RING, PANEL_Y, CZ + Math.cos(a) * RING);
    }

    /** 全息那套已经不用了（答题改成血肉界面）。留着代码，但先关掉。 */
    private static final boolean ENABLED = false;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!ENABLED) {
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        if (!QuizRoomClient.inside()) {
            return;
        }
        int index = QuizClientState.index();
        SevenQuestions.Question question = SevenQuestions.question(index);
        if (question == null) {
            aimed = -1;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        updateAim(minecraft, question);

        long now = System.currentTimeMillis();
        int count = question.choices().size();
        for (int i = 0; i < count; i++) {
            Vec3 pos = panelPos(i, count);
            boolean hot = i == aimed;
            drawPanel(minecraft, pose, buffers, cam, pos, question.choices().get(i).text(),
                    hot, now, i);
        }
        buffers.endBatch();
    }

    /** 算准星对着哪一块。 */
    private static void updateAim(Minecraft minecraft, SevenQuestions.Question question) {
        aimed = -1;
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 look = minecraft.player.getViewVector(1.0F).normalize();
        double best = AIM_DEGREES;
        int count = question.choices().size();
        for (int i = 0; i < count; i++) {
            Vec3 dir = panelPos(i, count).subtract(eye).normalize();
            double dot = Math.max(-1.0D, Math.min(1.0D, dir.dot(look)));
            double deg = Math.toDegrees(Math.acos(dot));
            if (deg < best) {
                best = deg;
                aimed = i;
            }
        }
    }

    /** 画一块面板。 */
    private static void drawPanel(Minecraft minecraft, PoseStack pose, MultiBufferSource buffers,
                                  Vec3 cam, Vec3 pos, String text, boolean hot, long now, int seed) {
        // 轻轻浮沉（每块相位不同）
        double bob = Math.sin(now / 900.0D + seed * 1.3D) * 0.035D;
        // 被看中：往玩家这边挪一点、并放大一点
        Vec3 toward = new Vec3(cam.x - pos.x, 0.0D, cam.z - pos.z).normalize();
        double pull = hot ? 0.22D : 0.0D;
        double px = pos.x + toward.x * pull;
        double pz = pos.z + toward.z * pull;

        pose.pushPose();
        pose.translate(px - cam.x, pos.y + bob - cam.y, pz - cam.z);
        // 面向摄像机（全息板要正对着你才读得清）
        pose.mulPose(Axis.YP.rotationDegrees(-(float) Math.toDegrees(Math.atan2(cam.x - px, cam.z - pz))));
        pose.mulPose(Axis.XP.rotationDegrees(-(float) Math.toDegrees(
                Math.atan2(cam.y - pos.y, Math.sqrt((cam.x - px) * (cam.x - px)
                        + (cam.z - pz) * (cam.z - pz))))));
        float scale = hot ? 0.0105F : 0.0095F;
        pose.scale(-scale, -scale, scale);

        Matrix4f mat = pose.last().pose();
        // 缩放坐标系里的尺寸 = 世界尺寸 ÷ 缩放
        float halfW = (PANEL_W / scale) * 0.5F;
        float halfH = (PANEL_H / scale) * 0.5F;

        // **刀剑神域那种系统窗口**：半透明的深蓝紫面板 + 一圈清晰的亮边 + 细网格，
        // 高亮的时候整块亮起来。不是一团虚光，也不是实心板子 —— 是"悬在空中的一块玻璃"。
        VertexConsumer fill = buffers.getBuffer(RenderType.debugFilledBox());   // 普通混合：半透明底
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());        // 叠加混合：光
        float bright = hot ? 1.0F : 0.72F;

        // ① 半透明底（能看见后面的白，但面板本身有厚度感）
        int fillAlpha = hot ? 0xEE : 0xD8;
        fillQuad(fill, mat, -halfW, -halfH, halfW, halfH,
                0x1A, 0x15, 0x33, fillAlpha);
        fillQuad(fill, mat, -halfW, halfH * 0.35F, halfW, halfH,
                0x2A, 0x20, 0x50, (int) (fillAlpha * 0.7F));
        // ② 外发光（贴着边往外散一点）
        glowQuad(glow, mat, -halfW * 1.10F, -halfH * 1.35F, halfW * 1.10F, halfH * 1.35F,
                90, 200, 255, (int) (16 * bright));
        // ③ 细网格（面板上的方格 —— SAO 那种质感就靠这个）
        int grid = (int) (30 * bright);
        for (int i = 1; i <= 4; i++) {
            float gy = -halfH + (halfH * 2.0F) * i / 5.0F;
            glowQuad(glow, mat, -halfW + 1.0F, gy - 0.55F, halfW - 1.0F, gy + 0.55F,
                    160, 230, 255, grid);
        }
        for (int i = 1; i <= 6; i++) {
            float gx = -halfW + (halfW * 2.0F) * i / 7.0F;
            glowQuad(glow, mat, gx - 0.55F, -halfH + 1.0F, gx + 0.55F, halfH - 1.0F,
                    160, 230, 255, grid);
        }
        // ④ 四边：清晰的亮青细线（SAO 面板最显眼的就是这圈边）
        int edge = (int) ((hot ? 235 : 150) * bright);
        float t = 1.7F;
        int er = hot ? 220 : 130;
        int eg = hot ? 250 : 220;
        glowQuad(glow, mat, -halfW, -halfH, halfW, -halfH + t, er, eg, 255, edge);
        glowQuad(glow, mat, -halfW, halfH - t, halfW, halfH, er, eg, 255, edge);
        glowQuad(glow, mat, -halfW, -halfH, -halfW + t, halfH, er, eg, 255, edge);
        glowQuad(glow, mat, halfW - t, -halfH, halfW, halfH, er, eg, 255, edge);
        // ⑤ 四角的小折角（细节：让边看起来是"有接口的面板"）
        float c = Math.min(halfW, halfH) * 0.42F;
        int corner = (int) ((hot ? 190 : 110) * bright);
        glowQuad(glow, mat, -halfW, -halfH + c, -halfW + 3.4F, -halfH + c * 0.55F, 200, 245, 255, corner);
        glowQuad(glow, mat, halfW - 3.4F, -halfH + c * 0.55F, halfW, -halfH + c, 200, 245, 255, corner);
        glowQuad(glow, mat, -halfW, halfH - c, -halfW + 3.4F, halfH - c * 0.55F, 200, 245, 255, corner);
        glowQuad(glow, mat, halfW - 3.4F, halfH - c * 0.55F, halfW, halfH - c, 200, 245, 255, corner);
        // ⑥ 扫描线（自下而上扫过）
        float scan = (float) ((now / 1500.0D + seed * 0.31D) % 1.0D);
        float sy = -halfH + scan * halfH * 2.0F;
        glowQuad(glow, mat, -halfW + 2.0F, sy - 2.0F, halfW - 2.0F, sy + 2.0F,
                235, 252, 255, (int) (54 * bright));
        // ⑦ 下面一小截"投影柱"：面板像是被一束光托着的
        glowQuad(glow, mat, -halfW * 0.55F, -halfH * 3.0F, halfW * 0.55F, -halfH,
                90, 190, 250, (int) (10 * bright));

        // 字：白字，高亮时更亮（SAO 的字是干净的白/浅蓝）
        Component label = Component.literal(text);
        int textColor = hot ? 0xFFFFFFFF : 0xFFD6ECFF;
        float textX = -minecraft.font.width(label) / 2.0F;
        minecraft.font.drawInBatch(label, textX, -4.0F, textColor, false, mat, buffers,
                net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        pose.popPose();
    }

    /** 普通混合的半透明底（能透出后面的白）。 */
    private static void fillQuad(VertexConsumer buf, Matrix4f mat,
                                 float x0, float y0, float x1, float y1,
                                 int r, int g, int b, int a) {
        buf.vertex(mat, x0, y0, 0.0F).color(r, g, b, a).endVertex();
        buf.vertex(mat, x0, y1, 0.0F).color(r, g, b, a).endVertex();
        buf.vertex(mat, x1, y1, 0.0F).color(r, g, b, a).endVertex();
        buf.vertex(mat, x1, y0, 0.0F).color(r, g, b, a).endVertex();
    }

    /** 叠加混合下的一层光：参数就是它的亮度贡献。 */
    private static void glowQuad(VertexConsumer buf, Matrix4f mat,
                                 float x0, float y0, float x1, float y1,
                                 int r, int g, int b, int a) {
        if (a <= 0) {
            return;
        }
        buf.vertex(mat, x0, y0, 0.0F).color(r, g, b, a).endVertex();
        buf.vertex(mat, x0, y1, 0.0F).color(r, g, b, a).endVertex();
        buf.vertex(mat, x1, y1, 0.0F).color(r, g, b, a).endVertex();
        buf.vertex(mat, x1, y0, 0.0F).color(r, g, b, a).endVertex();
    }

    private static void quad(VertexConsumer buf, Matrix4f mat,
                             float x0, float y0, float x1, float y1, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        buf.vertex(mat, x0, y0, 0.0F).color(r, g, b, a).endVertex();
        buf.vertex(mat, x0, y1, 0.0F).color(r, g, b, a).endVertex();
        buf.vertex(mat, x1, y1, 0.0F).color(r, g, b, a).endVertex();
        buf.vertex(mat, x1, y0, 0.0F).color(r, g, b, a).endVertex();
    }
}
