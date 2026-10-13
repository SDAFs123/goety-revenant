package com.habaiwa.goetyrevenant.client.fx;

import com.habaiwa.goetyrevenant.GoetyRevenant;
import com.habaiwa.goetyrevenant.common.compat.LegacyCompat;
import com.habaiwa.goetyrevenant.common.compat.WeepingWellUnlock;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * **井底那扇门，只有看得见的人才看得见。**
 *
 * <h3>什么时候亮</h3>
 * <p>同时戴着<b>七咒之戒</b>和<b>莫测之眼</b>（Enigmatic Legacy）的人，
 * 走近哭泣之井时会看到井口透出白光 —— 那就是下面那片纯白的维度。</p>
 * <p>没戴齐的人眼前什么也没有：井就只是口井。</p>
 *
 * <h3>画什么</h3>
 * <ul>
 *   <li><b>井底的白</b>：贴着那摊东西的表面，一层发光的白面</li>
 *   <li><b>光柱</b>：从井口往上的一束白光（十字交叉两面，从哪看都在）</li>
 *   <li><b>光尘</b>：井口周围慢慢往上飘的白粒子</li>
 * </ul>
 * <p>全部走**加法混合** —— 是"光透出来"，不是"贴了块白板"。</p>
 */
@Mod.EventBusSubscriber(modid = GoetyRevenant.MODID, value = Dist.CLIENT)
public final class WellGlowRenderer {
    /** 搜索半径（格）。 */
    private static final int SCAN = 7;

    private WellGlowRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        // **戴着那两件才看得见**
        if (!LegacyCompat.hasGear(minecraft.player)) {
            return;
        }
        BlockPos well = findWell(minecraft.player);
        if (well == null) {
            return;
        }

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(well.getX() + 0.5D - cam.x, well.getY() - cam.y, well.getZ() + 0.5D - cam.z);
        Matrix4f mat = pose.last().pose();

        long now = System.currentTimeMillis();
        float pulse = 0.72F + 0.28F * (float) Math.sin(now / 620.0D);

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);

        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // ① 井底的白面（两层：大的淡、小的亮）
        float y = 0.04F;
        face(buf, mat, 1.5F, y, 150, 250, 255, (int) (26 * pulse));
        face(buf, mat, 0.9F, y + 0.01F, 200, 255, 255, (int) (40 * pulse));

        // ② 光柱：十字两面，每一节都比下面淡一点
        float height = 7.0F;
        int steps = 16;
        for (int side = 0; side < 2; side++) {
            float ax = side == 0 ? 0.55F : 0.0F;
            float az = side == 0 ? 0.0F : 0.55F;
            for (int i = 0; i < steps; i++) {
                float y0 = height * i / steps;
                float y1 = height * (i + 1) / steps;
                float k = 1.0F - i / (float) steps;
                int a = (int) (30 * k * k * pulse);
                if (a <= 1) {
                    continue;
                }
                quad(buf, mat,
                        -ax, y0, -az, ax, y0, az, ax, y1, az, -ax, y1, -az,
                        190, 250, 255, a);
            }
        }

        Tesselator.getInstance().end();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    /** 一个水平面（四个角绕着中心）。 */
    private static void face(BufferBuilder buf, Matrix4f mat, float r, float y,
                             int cr, int cg, int cb, int a) {
        if (a <= 1) {
            return;
        }
        quad(buf, mat, -r, y, -r, r, y, -r, r, y, r, -r, y, r, cr, cg, cb, a);
    }

    /** 一个竖直四边形（两个角在上、两个角在下）。 */
    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer buf, Matrix4f mat,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             int cr, int cg, int cb, int a) {
        float r = cr / 255.0F;
        float g = cg / 255.0F;
        float b = cb / 255.0F;
        float al = a / 255.0F;
        buf.vertex(mat, x0, y0, z0).color(r, g, b, al).endVertex();
        buf.vertex(mat, x1, y1, z1).color(r, g, b, al).endVertex();
        buf.vertex(mat, x2, y2, z2).color(r, g, b, al).endVertex();
        buf.vertex(mat, x3, y3, z3).color(r, g, b, al).endVertex();
    }

    /** 找玩家附近"那摊东西"的顶面。 */
    private static BlockPos findWell(Player player) {
        BlockPos base = player.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN; dx <= SCAN; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -SCAN; dz <= SCAN; dz++) {
                    m.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    if (!WeepingWellUnlock.isGoop(player.level(), m)) {
                        continue;
                    }
                    double d = base.distSqr(m);
                    if (d < bestDist) {
                        bestDist = d;
                        best = m.immutable();
                    }
                }
            }
        }
        return best == null ? null : best.above();
    }

}
