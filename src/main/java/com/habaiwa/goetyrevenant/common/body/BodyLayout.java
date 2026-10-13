package com.habaiwa.goetyrevenant.common.body;

import java.util.List;

/**
 * 身体界面上各器官的位置 —— 按**中医人体图**的方位定。
 *
 * <p>坐标是相对人体面板的**比例**（0~1，x 向右、y 向下），所以换分辨率、换界面尺寸都不用改数字。
 * 面板画的是**正面人体**，心/肝/脾按中医「左肝右脾」的方位放（面向观察者的镜像）。</p>
 */
public final class BodyLayout {
    /** 界面面板的设计尺寸（像素），实际尺寸按比例缩放。 */
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 222;

    /** 一个人体部位热点。 */
    public record Spot(OrganType organ, float x, float y, float radius) {
    }

    /**
     * **七窍**的位置。头部的两窍当作**两只眼**摆（左灵右神），
     * 其余按中医人体图。
     * <ul>
     *   <li>灵窍 —— 头部左位（左眼）</li>
     *   <li>神窍 —— 头部右位（右眼）</li>
     *   <li>心 —— 胸中偏左（中医：心居胸中）</li>
     *   <li>肺 —— 上胸（中医：肺在胸中，左右各一，取右侧为代表）</li>
     *   <li>肝 —— 右胁（中医：肝居右胁）</li>
     *   <li>脾 —— 左胁（中医：脾居左胁）</li>
     *   <li>肾 —— 腰部（中医：腰为肾之府）</li>
     * </ul>
     */
    public static final List<Spot> SPOTS = List.of(
            new Spot(OrganType.SPIRIT_APERTURE, 0.500F, 0.072F, 0.070F),
            // 纵序照《针灸大成》脏腑图说：肺三椎 → 心五椎 → 肝九椎 → 脾十一椎 → 肾十四椎。
            // 躯干改成「头 + 躯干到胯」（4 头身）之后，这一列要整体重新落位。
            // 躯干：头6-58 / 颈58-62 / 肩62-95 / 腋95-103 / 胸103-126 / 腰126-150 / 胯150-190。
            // 器官按椎序落进自然的胸腹腔：肺=胸上段、心=胸中、肝=右上腹、脾=左上腹（略低）、肾=腰侧
            new Spot(OrganType.LUNG, 0.618F, 0.480F, 0.068F),
            new Spot(OrganType.HEART, 0.432F, 0.515F, 0.070F),
            new Spot(OrganType.LIVER, 0.622F, 0.612F, 0.060F),
            new Spot(OrganType.SPLEEN, 0.378F, 0.648F, 0.056F),
            new Spot(OrganType.KIDNEY, 0.500F, 0.712F, 0.060F));

    private BodyLayout() {
    }

    public static Spot spotOf(OrganType organ) {
        for (Spot spot : SPOTS) {
            if (spot.organ() == organ) {
                return spot;
            }
        }
        return SPOTS.get(0);
    }
}
