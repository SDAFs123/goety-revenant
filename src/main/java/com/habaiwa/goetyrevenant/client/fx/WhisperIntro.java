package com.habaiwa.goetyrevenant.client.fx;

import com.habaiwa.goetyrevenant.common.compat.LegacyCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/**
 * **那句话** —— 分三段说，字幕照 Enigmatic Legacy 的莫测之眼来做。
 *
 * <pre>
 *   One who has returned.   →  whisper_en_1.ogg   (~3.7 秒)
 *   The mist follows sound. →  whisper_en_2.ogg   (~3.8 秒)
 *   Order is the heart.     →  whisper_en_3.ogg   (~3.3 秒)
 * </pre>
 *
 * <h3>从莫测之眼那儿学来的两件事</h3>
 * <ol>
 *   <li><b>走 VOICE 声道</b>：它的 {@code QuoteHandler} 构造声音实例时用的是
 *       {@code SoundSource.VOICE} —— 这是选项里独立的一档音量（"声音"那一栏），
 *       不是主音量也不是环境音。我们之前用的 {@code SimpleSoundInstance.forUI}
 *       里的 MASTER 是另一回事，那一档拉满也未必管用。</li>
 *   <li><b>字幕落在屏幕下方、居中、带一层衬底</b>，淡入淡出；
 *       它那边的字幕数据本身就是分段的（{@code Subtitles.add(时刻, 台词)}），
 *       所以一段一段地浮出来是这个系统的原生行为，不是我们发明的。</li>
 * </ol>
 *
 * <p>触发条件也和它对齐：<b>戴着莫测之眼的人才听得见</b>
 * （见 {@link LegacyCompat#hasGear}）。</p>
 */
public final class WhisperIntro {
    /**
     * **字幕显示的中文**（音频本身说的是英语）。
     *
     * <p>像电影那样：声音是原声，字是译文 —— 所以这三行是中文，
     * 而 {@code whisper_en_N.ogg} 里念的是对应的英语句子：</p>
     * <pre>
     *   回魂之人      ←  One who has returned.
     *   迷雾循声      ←  The mist follows sound.
     *   序为本心      ←  Order is the heart.
     * </pre>
     */
    private static final String[] LINES = {
            "回魂之人",
            "迷雾循声",
            "序为本心",
    };
    /** 每段占用（音频 3.3~3.8 秒 + 余韵）。 */
    private static final long SEG_MS = 4100L;
    /** 一句之内：从"开始浮现"到"完全显形"。 */
    private static final long FADE_IN = 260L;
    /** 一句说完后淡出的时长。 */
    private static final long FADE_OUT = 420L;

    private static final int VOICE_LINE = 0xFFDCD6F0;   // 字幕主色（冷白偏紫，和它一个调子）
    private static final int VOICE_SHADOW = 0xFF1A1424;
    private static final int SOUL = 0xFF9AF2E8;
    private static final int SOUL_DIM = 0xFF2E8C88;

    private static long startedAt = 0L;
    private static int playedSeg = -1;
    private static boolean shown = false;

    private WhisperIntro() {
    }

    public static void trigger() {
        if (shown) {
            return;
        }
        shown = true;
        startedAt = System.currentTimeMillis();
        playedSeg = -1;
        // 临时诊断：说一句"我开始了"。看不到这句话就说明根本没触发到这儿。
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("[回魂] 低语开始（三段）"), false);
        }
    }

    public static boolean done() {
        return shown && System.currentTimeMillis() - startedAt > SEG_MS * LINES.length;
    }

    public static void reset() {
        shown = false;
        playedSeg = -1;
        startedAt = 0L;
    }

    public static int currentSegment() {
        if (!shown) {
            return -1;
        }
        int seg = (int) ((System.currentTimeMillis() - startedAt) / SEG_MS);
        return seg >= LINES.length ? -1 : seg;
    }

    /**
     * 每帧。
     *
     * <p>字幕画在<b>屏幕下方居中</b>（{@code height * 0.74}），
     * 底下垫一条很淡的横带 —— 和莫测之眼那边一样，是为了在花哨的背景上还读得清。</p>
     */
    public static void render(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                              int width, int height) {
        if (!shown) {
            return;
        }
        long el = System.currentTimeMillis() - startedAt;
        if (el > SEG_MS * LINES.length) {
            return;
        }
        int seg = (int) (el / SEG_MS);
        if (seg >= LINES.length) {
            return;
        }
        long inSeg = el - (long) seg * SEG_MS;

        // **该这一段了就把它的音放出来**
        if (seg != playedSeg) {
            playedSeg = seg;
            playSegment(seg);
        }

        // 淡入 / 淡出（一句话一个来回）
        float alpha = 1.0F;
        if (inSeg < FADE_IN) {
            alpha = inSeg / (float) FADE_IN;
        } else if (inSeg > SEG_MS - FADE_OUT) {
            alpha = (SEG_MS - inSeg) / (float) FADE_OUT;
        }
        alpha = Math.max(0.0F, Math.min(1.0F, alpha));

        String line = LINES[seg];
        int cx = width / 2;
        int y = (int) (height * 0.74F);
        int w = font.width(line);

        // 衬底：一条随文字宽度走的暗带（两条 fill，够用了）
        int pad = 10;
        int bandA = (int) (alpha * 96);
        if (bandA > 4) {
            graphics.fill(cx - w / 2 - pad, y - 4, cx + w / 2 + pad, y + 11,
                    (bandA << 24));
        }

        // 字幕本体：冷白偏紫 + 深色描边（在亮背景上也压得住）
        graphics.drawString(font, Component.literal(line),
                cx - w / 2 + 1, y + 1, ((int) (alpha * 200) << 24) | (VOICE_SHADOW & 0xFFFFFF), false);
        graphics.drawString(font, Component.literal(line), cx - w / 2, y,
                ((int) (alpha * 255) << 24) | (VOICE_LINE & 0xFFFFFF), false);

        // 已经说过的那几段：淡淡留在下面（像话音还悬在空气里）
        for (int s = 0; s < seg; s++) {
            String prev = LINES[s];
            int py = y - (seg - s) * 13;
            int pw = font.width(prev);
            graphics.drawString(font, Component.literal(prev), cx - pw / 2, py,
                    ((int) (alpha * 70) << 24) | (VOICE_LINE & 0xFFFFFF), false);
        }

        // 一点点魂青的光，接上这个模组自己的调子
        int glowA = (int) (alpha * 40);
        if (glowA > 4) {
            graphics.fill(cx - w / 2 - pad - 3, y + 10, cx + w / 2 + pad + 3, y + 12,
                    (glowA << 24) | (SOUL & 0xFFFFFF));
        }
        if (inSeg < FADE_IN) {
            com.habaiwa.goetyrevenant.client.fx.GlowRenderer.halo(graphics,
                    cx, y + 4, w / 2.0F + 26.0F, 12.0F, SOUL & 0xFFFFFF, 2,
                    (int) ((1.0F - alpha) * 60));
        }
    }

    /**
     * 放第 seg 段的语音。
     *
     * <p><b>关键：走 {@link SoundSource#VOICE}</b> —— 和莫测之眼一致。
     * 它不是主音量那一档，是选项里单独的一条。</p>
     */
    private static void playSegment(int seg) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation loc = new ResourceLocation(
                "goety_revenant:whisper_en_" + (seg + 1));
        // 手工构造：forUI 只能给 MASTER，而我们要 VOICE
        SimpleSoundInstance inst = new SimpleSoundInstance(
                loc,
                SoundSource.VOICE,
                1.0F,                                  // 音量
                1.0F,                                  // 音高
                net.minecraft.util.RandomSource.create(),
                false, 0,
                SoundInstance.Attenuation.NONE,         // 不带距离衰减
                0.0D, 0.0D, 0.0D,
                true);
        minecraft.getSoundManager().play(inst);
    }
}
