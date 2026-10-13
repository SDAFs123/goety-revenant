package com.habaiwa.goetyrevenant.client.fx;

import com.habaiwa.goetyrevenant.GoetyRevenant;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * **血肉风格的物品提示框**。
 *
 * <h3>为什么是"接管"而不是"只改颜色"</h3>
 * <p>理想做法是不取消原版、只在 {@code RenderTooltipEvent.Color} 里改底色和边框色。
 * 但 1.20.1 的那个事件<b>只有 getter、没有 setter</b> —— 传进去的颜色是只读的，
 * 改不了（要改只能写 mixin 动原版的 {@code TooltipRenderUtil}）。
 * 所以"血肉底 + 血边"这套只能靠自己画。</p>
 *
 * <h3>接管带来两个问题，都补掉了</h3>
 * <ol>
 *   <li><b>图片类的组件画不出来</b>：原来对每一行都调 {@code renderText}，
 *       而图片组件（物品图标、图表之类）得调 <b>{@code renderImage}</b> 才会画。
 *       现在按类型分开处理 —— 这也是为什么"别人加的东西看起来被吞了"：
 *       其实行都在，只是图片那部分没被画。</li>
 *   <li><b>和"也接管渲染"的模组打架</b>：两边都 setCanceled 的话，
 *       谁取消谁、坐标各算各的 —— 表现是框错位、字叠一起。
 *       这种模组（典型的是 Modern UI）在场时我们<b>直接退让</b>。</li>
 * </ol>
 *
 * <h3>两道门槛</h3>
 * <ul>
 *   <li><b>只对我们自己的物品生效</b>（{@code goety_revenant} 命名空间）——
 *       别人家的面包、铁镐不该长成这副样子</li>
 *   <li><b>Modern UI 在场就退让</b> —— 它有整套自己的 tooltip 渲染</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = GoetyRevenant.MODID, value = Dist.CLIENT)
public final class FleshTooltip {
    /** 底：几乎不透明的暗红。 */
    private static final int BG = 0xF00E0405;
    /** 横向肉纹（压在底上）。 */
    private static final int FLESH_LINE = 0x30B01B22;
    /** 边框渐变的起（深）与止（血色）。 */
    private static final int BORDER_DARK = 0xFF4A060B;
    private static final int BORDER_LIT = 0xFFB01B22;
    private static final int SOUL = 0xFF52E8DC;

    /** Modern UI 在不在 —— 它有自己的一套 tooltip 渲染，我们不该插手。 */
    private static final boolean HAS_MODERN_UI = checkModernUi();

    private FleshTooltip() {
    }

    private static boolean checkModernUi() {
        try {
            return net.minecraftforge.fml.ModList.get().isLoaded("modernui");
        } catch (Throwable t) {
            return false;
        }
    }

    /** 这个东西是不是"血肉"的 —— 只有它才该长成这副样子。 */
    private static boolean isFleshItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            ResourceLocation id = stack.getItem().builtInRegistryHolder().key().location();
            return "goety_revenant".equals(id.getNamespace());
        } catch (Throwable t) {
            return false;
        }
    }

    @SubscribeEvent
    public static void onRenderTooltip(RenderTooltipEvent.Pre event) {
        // 门槛一：只对我们自己的物品生效
        if (!isFleshItem(event.getItemStack())) {
            return;
        }
        // 门槛二：Modern UI 在场就退让（避免两边抢渲染）
        if (HAS_MODERN_UI) {
            return;
        }
        GuiGraphics graphics = event.getGraphics();
        Font font = event.getFont();
        List<ClientTooltipComponent> lines = event.getComponents();
        if (lines.isEmpty()) {
            return;
        }
        event.setCanceled(true);

        // --- 自己算框的大小（宽度取最宽的一行）---
        int w = 0;
        int h = 0;
        for (ClientTooltipComponent line : lines) {
            w = Math.max(w, line.getWidth(font));
            h += line.getHeight();
        }
        w += 18;
        h += 12;

        // --- 位置（鼠标右下方，出界就翻到另一边）---
        int x = event.getX() + 10;
        int y = event.getY() - 12;
        if (x + w > event.getScreenWidth() - 4) {
            x = event.getScreenWidth() - w - 4;
        }
        if (y + h > event.getScreenHeight() - 4) {
            y = event.getScreenHeight() - h - 4;
        }
        if (y < 4) {
            y = 4;
        }

        // --- 底：**平铺一块真的肉纹**（不是纯色 + 横线）---
        // 先生成（只做一次），再按框的大小把它铺满。
        // 一个 200×100 的框只要 8 次 blit —— 比每帧撒几百个点便宜，而且是有质感的。
        FleshTexture.ensure();
        boolean tiled = false;
        try {
            int tile = FleshTexture.TILE;
            for (int ty = 0; ty < h; ty += tile) {
                for (int tx = 0; tx < w; tx += tile) {
                    int tw = Math.min(tile, w - tx);
                    int th = Math.min(tile, h - ty);
                    // 目标(tx,ty) 尺寸 tw×th  ←  源(0,0) 尺寸 tw×th  纹理 tile×tile
                    graphics.blit(FleshTexture.texture(), x + tx, y + ty, tw, th,
                            0.0F, 0.0F, tw, th, tile, tile);
                }
            }
            tiled = true;
        } catch (Throwable ignored) {
            tiled = false;
        }
        // 万一纹理没生成出来，退回纯色底（功能不能因此断掉）
        if (!tiled) {
            graphics.fill(x, y, x + w, y + h, BG);
            for (int i = 1; i < h; i += 5) {
                graphics.fill(x + 3, y + i, x + w - 3, y + i + 1, FLESH_LINE);
            }
        }
        // 再压一层很淡的整体暗色 —— 让底不至于太抢，文字才清楚
        graphics.fill(x, y, x + w, y + h, 0x48000000);
        // 血色的发光边（加法混合 —— 是"亮出来"，不是"描一圈"）
        GlowRenderer.glowOutline(graphics, x - 0.5F, y - 0.5F, w + 1.0F, h + 1.0F,
                BORDER_LIT & 0xFFFFFF, 120);
        graphics.renderOutline(x, y, w, h, BORDER_DARK);
        // 四角的小血斑
        int[][] corners = {
                {x - 1, y - 1}, {x + w - 3, y - 1},
                {x - 1, y + h - 3}, {x + w - 3, y + h - 3},
        };
        for (int[] c : corners) {
            graphics.fill(c[0], c[1], c[0] + 4, c[1] + 4, 0xFF6A0A10);
        }

        // --- 逐行画 ---
        int ly = y + 6;
        for (int i = 0; i < lines.size(); i++) {
            ClientTooltipComponent line = lines.get(i);
            int lh = line.getHeight();
            int tx = (i == 0) ? x + 12 : x + 9;
            if (i == 0) {
                // 标题：左边一根灵魂色的竖杠（做成发光的）
                GlowRenderer.glowBox(graphics, x + 6, ly - 1, x + 8, ly + lh - 2, SOUL, 190);
                GlowRenderer.halo(graphics, x + 7, ly + lh / 2.0F - 1, 8, lh, SOUL, 3, 40);
            }
            // **按类型分开处理** —— 这是"别人加的内容被看起来吞掉"的真正原因：
            // 图片类组件必须走 renderImage，只调 renderText 是什么都不会画的。
            try {
                if (line instanceof net.minecraft.client.gui.screens.inventory.tooltip
                        .ClientTooltipComponent) {
                    // 先试图片（图片组件会在这里画出东西）
                    line.renderImage(font, tx, ly, graphics);
                    // 再画文字（纯文字组件会在这里画出东西；两者互不冲突）
                    line.renderText(font, tx, ly, graphics.pose().last().pose(),
                            graphics.bufferSource());
                }
            } catch (Throwable ignored) {
                // 某一行画不出来就算了，别让整个 tooltip 崩掉
            }
            ly += lh;
        }
    }

    /** 给正文用的骨白（外面想统一颜色时可以调）。 */
    public static int bone() {
        return 0xFFE8DCC8;
    }
}
