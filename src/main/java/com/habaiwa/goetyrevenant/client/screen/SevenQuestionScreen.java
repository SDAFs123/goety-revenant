package com.habaiwa.goetyrevenant.client.screen;

import com.habaiwa.goetyrevenant.client.QuizClientState;
import com.habaiwa.goetyrevenant.client.fx.GlowRenderer;
import com.habaiwa.goetyrevenant.client.fx.TextFx;
import com.habaiwa.goetyrevenant.common.network.AnswerMessage;
import com.habaiwa.goetyrevenant.common.network.RevenantNetwork;
import com.habaiwa.goetyrevenant.common.sequence.Sequence;
import com.habaiwa.goetyrevenant.common.sequence.SevenQuestions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * **意识空间** —— 七问答发生的地方。
 *
 * <h3>它不是什么</h3>
 * <p>不是一块血肉（那是身体的事），也不是一个"界面"：没有框、没有按钮、没有底板。
 * 这里面只有**念头**。</p>
 *
 * <h3>它是什么</h3>
 * <ul>
 *   <li><b>底</b>：极浅的青白 —— 和外面那片白是同一个地方，只是这儿更静</li>
 *   <li><b>思绪</b>：几道横着慢慢淌过去的淡光，速度各不相同（想事情的时候就是这样的）</li>
 *   <li><b>记忆</b>：十来粒极淡的光点，缓缓明灭</li>
 *   <li><b>问题</b>：浮在中间，深青的字，底下一条几乎看不见的横线</li>
 *   <li><b>选项</b>：像念头一样一件件浮上来 —— 悬停时亮起、向右轻轻让开一点；
 *       点下去，它以魂青散开</li>
 * </ul>
 *
 * <p>整屏没有一根边框。要的就是"这些东西是从你自己脑子里浮出来的"。</p>
 */
public class SevenQuestionScreen extends Screen {
    /** 极浅的青白（和外面那片白同源，只是更静）。 */
    private static final int MIST_TOP = 0xFFEFF7F8;
    private static final int MIST_BOTTOM = 0xFFD6E5E7;
    /** 念头/文字的主色：深青。 */
    private static final int INK = 0xFF16383E;
    private static final int INK_SOFT = 0xFF4A7078;
    private static final int SOUL = 0x52E8DC;

    private static final long MELT_MS = 700L;
    private static final long APPEAR_MS = 620L;
    private static final int ROW = 22;

    private final List<int[]> rows = new ArrayList<>();

    private int hovered = -1;
    private long meltAt = 0L;
    private int meltColor = SOUL;
    private long appearAt = 0L;

    public SevenQuestionScreen() {
        super(Component.literal("意识空间"));
    }

    private float appearProgress(long now) {
        if (this.appearAt == 0L) {
            this.appearAt = now;
        }
        float t = Math.min(1.0F, (now - this.appearAt) / (float) APPEAR_MS);
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    private static int fade(int color, float alpha) {
        float a = Math.max(0.0F, Math.min(1.0F, alpha));
        int r = (int) (((color >> 16) & 0xFF) * a + (MIST_TOP >> 16 & 0xFF) * (1.0F - a));
        int g = (int) (((color >> 8) & 0xFF) * a + (MIST_TOP >> 8 & 0xFF) * (1.0F - a));
        int b = (int) ((color & 0xFF) * a + (MIST_TOP & 0xFF) * (1.0F - a));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        float appear = this.appearProgress(now);

        // ① 底：极浅的青白，上亮下暗一点
        for (int y = 0; y < this.height; y += 2) {
            float t = y / (float) Math.max(1, this.height);
            graphics.fill(0, y, this.width, y + 2, mix(MIST_TOP, MIST_BOTTOM, t));
        }
        // ② 思绪：几道横着慢慢淌过去的淡光（速度各不相同）
        for (int i = 0; i < 4; i++) {
            float speed = 0.010F + i * 0.004F;
            float p = ((now / 1000.0F) * speed + i * 0.27F) % 1.0F;
            int y = (int) (this.height * (0.16F + i * 0.21F));
            int w = (int) (this.width * (0.42F + i * 0.09F));
            int x = (int) (-w + (this.width + w * 2) * p);
            int a = 10 + i * 3;
            graphics.fill(x, y, x + w, y + 2, (a << 24) | (SOUL & 0xFFFFFF));
            graphics.fill(x + w / 4, y - 3, x + w * 3 / 4, y + 5, (a / 2 << 24) | (SOUL & 0xFFFFFF));
        }
        // ③ 记忆：十来粒极淡的光点，缓缓明灭
        for (int i = 0; i < 12; i++) {
            double seed = i * 12.9898D;
            int x = (int) ((Math.sin(seed) * 0.5D + 0.5D) * this.width);
            int y = (int) ((Math.sin(seed * 2.7D) * 0.5D + 0.5D) * this.height);
            float blink = (float) (Math.sin(now / 1400.0D + i) * 0.5D + 0.5D);
            int a = (int) (blink * 26);
            if (a <= 2) {
                continue;
            }
            int r = 1 + (i % 2);
            graphics.fill(x - r, y - r, x + r + 1, y + r + 1, (a << 24) | (SOUL & 0xFFFFFF));
        }

        int index = QuizClientState.index();
        SevenQuestions.Question question = SevenQuestions.question(index);
        if (question == null) {
            this.drawMelt(graphics, now, this.width / 2, this.height / 2);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        int cx = this.width / 2;
        int baseY = (int) (this.height * 0.40F);

        // ④ 第几问（很淡）
        TextFx.glowCentered(graphics, this.font,
                Component.literal("第 " + (index + 1) + " 问"), cx, baseY - 46,
                fade(INK_SOFT, appear * 0.8F), SOUL, appear * 0.25F);

        // ⑤ 问题：深青的字，底下一条几乎看不见的横线
        TextFx.glowCentered(graphics, this.font, Component.literal(question.text()),
                cx, baseY - 26, fade(INK, appear), SOUL, appear * 0.30F);
        int lineW = Math.min(this.width - 120, this.font.width(question.text()) + 80);
        graphics.fill(cx - lineW / 2, baseY - 10, cx + lineW / 2, baseY - 9,
                (int) (26 * appear) << 24 | (INK_SOFT & 0xFFFFFF));

        // ⑥ 选项：像念头一样一件件浮上来
        this.rows.clear();
        this.hovered = -1;
        List<SevenQuestions.Choice> choices = question.choices();
        int rowTop = baseY + 12;
        for (int i = 0; i < choices.size(); i++) {
            String label = choices.get(i).text();
            int w = this.font.width(label);
            int x0 = cx - w / 2 - 14;
            int top = rowTop + i * ROW;
            this.rows.add(new int[]{x0, top - 4, w + 28, ROW});
            boolean hover = mouseX >= x0 && mouseX < x0 + w + 28
                    && mouseY >= top - 4 && mouseY < top + ROW - 4;
            if (hover) {
                this.hovered = i;
            }
            // 每一条比上一条晚一点浮出来
            float rowAppear = Math.max(0.0F, Math.min(1.0F, appear * 1.8F - i * 0.16F));
            if (rowAppear <= 0.01F) {
                continue;
            }
            // 悬停：整条向右让开一点、字变亮、前面浮一个光点
            int shift = hover ? 4 : 0;
            int color = hover ? 0xFF0A2026 : fade(INK, rowAppear);
            TextFx.glow(graphics, this.font, Component.literal(label),
                    cx - w / 2 + shift, top, color, SOUL, hover ? 0.5F : rowAppear * 0.18F);
            if (hover) {
                GlowRenderer.halo(graphics, cx - w / 2.0F - 10.0F, top + 4.0F, 5.0F, 5.0F,
                        SOUL & 0xFFFFFF, 3, 110);
            }
        }

        // ⑦ 底部一句极淡的提示（不用按键，点就行）
        TextFx.glowCentered(graphics, this.font, Component.literal("选一个"),
                cx, this.height - 30, fade(INK_SOFT, appear * 0.35F), SOUL, appear * 0.12F);

        this.drawMelt(graphics, now, cx, baseY + 30);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 选定：以魂青散开（念头成形又散掉）。 */
    /**
     * **点下去的回应** —— 重做过：分批涌出的粒子 + 冲击波 + 中心白闪。
     *
     * <p><b>只有一件事：念头散开。</b>
     * 之前白闪 + 冲击波 + 粒子三样一起上，太乱 —— 全撤了。</p>
     * <ul>
     *   <li>粒子<b>分 12 批</b>陆续出生（每个有自己的时刻）—— 低帧率也不会塌成一片</li>
     *   <li>缓出：出发快、后面慢慢飘</li>
     *   <li>双色：三成序列色、七成魂青；每批头一颗带尾迹</li>
     *   <li>中心留一小团极淡的余光，充当"刚散开"的余韵</li>
     * </ul>
     */
    private void drawMelt(GuiGraphics graphics, long now, int cx, int cy) {
        if (this.meltAt == 0L) {
            return;
        }
        float t = (now - this.meltAt) / (float) MELT_MS;
        if (t >= 1.0F) {
            this.meltAt = 0L;
            return;
        }
        int soul = SOUL & 0xFFFFFF;

        // **只有一件事：念头散开。**
        // 之前这里有白闪 + 冲击波 + 粒子三样一起上，看着乱。
        // 现在只留粒子 —— 但把它做得更细：
        //   · 分 12 批陆续出生（每个粒子有自己的时刻）
        //   · 缓出（出发快、后面慢慢飘）
        //   · 双色（三成序列色、七成魂青）
        //   · 每批头一颗带尾迹
        //   · 中心留一小团极淡的余光，充当"刚散开"的余韵

        // **只有粒子。** 之前还留了一团中心余光 —— 那也算"一样东西"，
        // 数起来就是两个。现在整段只有粒子在动。

        // 粒子：分 12 批陆续出生（每批有自己的出生时刻）
        int batches = 12;
        for (int i = 0; i < 108; i++) {
            float born = (i % batches) / (float) batches * 0.55F;
            float pt = (t - born) / Math.max(0.05F, 1.0F - born);
            if (pt <= 0.0F || pt >= 1.0F) {
                continue;
            }
            // 缓出：出发快、后面慢慢飘
            float ease = 1.0F - (1.0F - pt) * (1.0F - pt);
            double ang = i * 2.399963D;
            float dist = ease * (26.0F + (i % 13) * 15.0F);
            int px = cx + (int) (Math.cos(ang) * dist);
            int py = cy + (int) (Math.sin(ang) * dist * 0.58F);
            int alpha = (int) ((1.0F - pt) * 165);
            if (alpha <= 5) {
                continue;
            }
            int rgb = (i % 10 < 3) ? (this.meltColor & 0xFFFFFF) : soul;
            int r = 1 + (i % 3);
            graphics.fill(px - r, py - r, px + r + 1, py + r + 1, (alpha << 24) | rgb);
            // 每一批的头几颗带个尾迹
            if (i % 12 == 0 && pt < 0.6F) {
                graphics.fill(px - r - 2, py, px + r + 3, py + 1, (alpha / 3 << 24) | rgb);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        SevenQuestions.Question question = SevenQuestions.question(QuizClientState.index());
        if (question != null) {
            for (int i = 0; i < this.rows.size(); i++) {
                int[] r = this.rows.get(i);
                if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                    net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F));
                    QuizClientState.addScore(question.choices().get(i).toward());
                    Sequence leaning = QuizClientState.leaning();
                    this.meltColor = leaning == null ? SOUL : leaning.color();
                    this.meltAt = System.currentTimeMillis();
                    this.appearAt = 0L;
                    RevenantNetwork.CHANNEL.sendToServer(new AnswerMessage(i));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        return 0xFF000000
                | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
