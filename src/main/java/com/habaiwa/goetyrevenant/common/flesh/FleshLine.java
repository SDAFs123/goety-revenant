package com.habaiwa.goetyrevenant.common.flesh;

/**
 * **血肉线 · 三条路**。
 *
 * <p>觉醒之后，每个序列者的血肉都会往其中一个方向走。**不是选的** ——
 * 是底色决定的：向外要的人异化，向内收的人灵化，向下沉的人硅化。</p>
 *
 * <table>
 *   <tr><th>路</th><th>方向</th><th>血肉变成</th><th>越走越</th><th>终点</th></tr>
 *   <tr><td>异化</td><td>向外变</td><td>别的生物</td><td>越像怪物</td><td>血肉之源</td></tr>
 *   <tr><td>灵化</td><td>向内变</td><td>灵体</td><td>越像灵魂</td><td>灵魂之源</td></tr>
 *   <tr><td>硅化</td><td>向无机变</td><td>矿物 / 晶体</td><td>越像石头</td><td>硅基之源</td></tr>
 * </table>
 *
 * <p><b>三条路不分好坏</b> —— 只是不同的"不再是人了"。</p>
 *
 * <h3>硅化到底是什么意思</h3>
 * <p>不是"变成石头"，是<b>血肉被硅基替代</b>：骨头变晶柱、血液变液态硅、
 * 肌肉变纤维状矿物、皮肤变半透明晶体、内脏变晶核。
 * 越走越硬、越走越冷、越走越慢，但越走越不朽 ——
 * 最后你是一块<b>会动的晶体</b>，还活着，但已经不剩血肉。</p>
 *
 * <h3>哪些是占位</h3>
 * <p>三条路的名字、方向、血肉变成什么、终点，都是设计里的原话。
 * <b>阶名是占位的</b>（{@link #stepName}）—— 每一阶具体是什么还没推，
 * 等推出来按顺序填进 {@code STEPS} 就行，别的代码不用动。</p>
 */
public enum FleshLine {
    /** 向外变：吞噬、融合、变异、增殖 —— 变成别的形态。 */
    MUTATION("异化", "向外变", "别的生物", "血肉之源", "越像怪物",
            new String[]{"初变", "增生", "融合", "畸变", "吞噬", "巨化", "源"}),
    /**
     * 向内变：越走越轻，越走越接近灵魂。
     *
     * <p><b>设定（已记下，未实现）</b>：走到灵体之后，可以<b>随意穿梭维度</b> ——
     * 不再需要传送门。这条路走到后面的玩家，空间对他不再是墙。</p>
     */
    SPIRIT("灵化", "向内变", "灵体", "灵魂之源", "越像灵魂",
            new String[]{"轻身", "透体", "离形", "魂游", "凝意", "灵躯", "源"}),
    /** 向无机变：血肉被硅基替代，越走越硬、越冷、越慢，但越不朽。 */
    SILICON("硅化", "向无机变", "矿物与晶体", "硅基之源", "越像石头",
            new String[]{"结晶", "硬化", "替换", "透明", "晶核", "不朽", "源"});

    /** 阶数：和序列一样七阶（最后一阶是"源"）。 */
    public static final int STAGES = 7;

    private final String cn;
    private final String direction;
    private final String becomes;
    private final String source;
    private final String trend;
    private final String[] steps;

    FleshLine(String cn, String direction, String becomes, String source,
              String trend, String[] steps) {
        this.cn = cn;
        this.direction = direction;
        this.becomes = becomes;
        this.source = source;
        this.trend = trend;
        this.steps = steps;
    }

    public String cn() {
        return this.cn;
    }

    /** 往哪变（向外 / 向内 / 向无机）。 */
    public String direction() {
        return this.direction;
    }

    /** 血肉最终变成什么。 */
    public String becomes() {
        return this.becomes;
    }

    /** 终点那个源。 */
    public String source() {
        return this.source;
    }

    /** 越走越怎么样。 */
    public String trend() {
        return this.trend;
    }

    /**
     * 第 n 阶叫什么。
     *
     * <p><b>占位</b> —— 三条路每一阶具体是什么还没推出来。
     * 推出来之后按顺序填 {@code STEPS} 即可，其它代码读的就是这里。</p>
     *
     * @param stage 1 ~ {@link #STAGES}
     */
    public String stepName(int stage) {
        if (stage <= 0) {
            return "未起";
        }
        if (stage > STAGES) {
            return this.source;
        }
        return this.steps[Math.min(this.steps.length, stage) - 1];
    }

    /** 这个阶段的颜色（脉动/染色都用它）—— 三条路各有各的调子。 */
    public int color() {
        return switch (this) {
            case MUTATION -> 0xFFC02030;   // 异化：血肉的暗红
            case SPIRIT -> 0xFF8AE8F0;     // 灵化：灵体的冷青
            case SILICON -> 0xFFE8F4FF;    // 硅化：晶体的近白
        };
    }

    public static FleshLine byIndex(int index) {
        FleshLine[] all = values();
        return index >= 0 && index < all.length ? all[index] : null;
    }

    public static FleshLine byName(String name) {
        for (FleshLine line : values()) {
            if (line.name().equalsIgnoreCase(name) || line.cn.equals(name)) {
                return line;
            }
        }
        return null;
    }
}
