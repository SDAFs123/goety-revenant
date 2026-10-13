package com.habaiwa.goetyrevenant.common.sequence;

import net.minecraft.network.chat.Component;

/**
 * **七条序列** —— 跳进哭泣之井，七问答之后被其中一条看中；觉醒即锁定，不可更换。
 *
 * <h3>内核：失去</h3>
 * <p>每条序列都从一次「失去」开始。状态是结果，事情是原因 ——
 * 他不是天生不被感知，是**被忽视**这件事把他变成了不被感知的人。</p>
 *
 * <table>
 *   <tr><th>序列</th><th>失去</th><th>状态</th><th>活法</th><th>源</th><th>温度</th><th>核心</th><th>场</th></tr>
 *   <tr><td>烛守</td><td>被忽视</td><td>不被感知</td><td>烧自己让别人看见</td><td>烬之源</td><td>高温</td><td>烛火值</td><td>温场</td></tr>
 *   <tr><td>拾荒</td><td>被抛弃</td><td>没有归属</td><td>捡东西</td><td>遗存之源</td><td>低温</td><td>拾骸</td><td>遗场</td></tr>
 *   <tr><td>缄口</td><td>被控制</td><td>不能决定</td><td>咽下去</td><td>静默之源</td><td>低温</td><td>咽晶</td><td>默场</td></tr>
 *   <tr><td>追影</td><td>被否定</td><td>不重要</td><td>追</td><td>未及之源</td><td>低温</td><td>逐晶</td><td>逐场</td></tr>
 *   <tr><td>筑墙</td><td>被剥夺</td><td>时间不在我这边</td><td>砌墙</td><td>界域之源</td><td>高温</td><td>界石</td><td>界场</td></tr>
 *   <tr><td>归墟</td><td>被背叛</td><td>随时被伤害</td><td>往回走</td><td>终末之源</td><td>高温</td><td>溯晶</td><td>溯场</td></tr>
 *   <tr><td>回声</td><td>被取代</td><td>不再是"我是谁"</td><td>喊</td><td>回响之源</td><td>低温</td><td>响晶</td><td>响场</td></tr>
 * </table>
 *
 * <h3>七个阶段（每条序列自己的一套名字）</h3>
 * <p>阶梯是一样的：<b>觉醒 → 一阶 → … → 七阶 → 成源</b>；
 * 但每一阶叫什么，是这条序列自己的事（烛守是候火、递火、燃身、残烛、守火、传火、余温）。</p>
 *
 * <p>四阶起<b>灵体化</b>：四阶灵魂出窍、五阶神识与念力、六阶灵环与三我聚晶（回魂失效）、七阶灵体稳定。</p>
 */
public enum Sequence {
    CANDLE_KEEPER("烛守", "被忽视", "不被感知", "烧自己让别人看见", "烬之源", true, "烛火值", "温场",
            new String[]{"候火", "递火", "燃身", "残烛", "守火", "传火", "余温"}),
    SCAVENGER("拾荒", "被抛弃", "没有归属", "捡东西", "遗存之源", false, "拾骸", "遗场",
            new String[]{"攒聚", "拆解", "重组", "残器", "守藏", "传遗", "余藏"}),
    SILENT("缄口", "被控制", "不能决定", "咽下去", "静默之源", false, "咽晶", "默场",
            new String[]{"积言", "封存", "吞法", "残言", "守默", "传默", "余声"}),
    SHADOW("追影", "被否定", "不重要", "追", "未及之源", false, "逐晶", "逐场",
            new String[]{"起步", "追逐", "追远", "残影", "守途", "传途", "余踪"}),
    WALL("筑墙", "被剥夺", "时间不在我这边", "砌墙", "界域之源", true, "界石", "界场",
            new String[]{"砌石", "成墙", "围城", "残垣", "守界", "传界", "余垣"}),
    RETURN("归墟", "被背叛", "随时被伤害", "往回走", "终末之源", true, "溯晶", "溯场",
            new String[]{"回望", "逆行", "溯远", "残途", "守归", "传归", "余溯"}),
    ECHO("回声", "被取代", "不再是\"我是谁\"", "喊", "回响之源", false, "响晶", "响场",
            new String[]{"发声", "传声", "回响", "残响", "守响", "传响", "余响"});

    private final String cn;
    private final String loss;
    private final String state;
    private final String way;
    private final String source;
    private final boolean hot;
    private final String resource;
    private final String field;
    private final String[] steps;

    Sequence(String cn, String loss, String state, String way, String source, boolean hot,
             String resource, String field, String[] steps) {
        this.cn = cn;
        this.loss = loss;
        this.state = state;
        this.way = way;
        this.source = source;
        this.hot = hot;
        this.resource = resource;
        this.field = field;
        this.steps = steps;
    }

    public String cn() {
        return this.cn;
    }

    /** 失去的东西 —— 内核。 */
    public String loss() {
        return this.loss;
    }

    /** 因此变成的状态。 */
    public String state() {
        return this.state;
    }

    /** 他怎么应对（活法）。 */
    public String way() {
        return this.way;
    }

    /** 通往的源。 */
    public String source() {
        return this.source;
    }

    /** 默认灵魂温度是不是高温。 */
    public boolean hot() {
        return this.hot;
    }

    /** 核心资源叫什么（烛守 = 烛火值）。 */
    public String resource() {
        return this.resource;
    }

    /** 场地叫什么（烛守 = 温场）。 */
    public String field() {
        return this.field;
    }

    /**
     * 阶名。
     *
     * @param stage 0 = 觉醒，1~7 = 一阶到七阶，8 = 成源
     */
    public String stepName(int stage) {
        if (stage <= 0) {
            return "觉醒";
        }
        if (stage >= 8) {
            return "源";
        }
        return this.steps[Math.min(this.steps.length, stage) - 1];
    }

    /** 完整的阶名（觉醒·烛守 / 一阶·候火 …）。 */
    public Component stepLabel(int stage) {
        if (stage <= 0) {
            return Component.literal("觉醒 · " + this.cn);
        }
        if (stage >= 8) {
            return Component.literal("成源 · " + this.source);
        }
        String[] count = {"", "一", "二", "三", "四", "五", "六", "七"};
        return Component.literal(count[Math.min(7, stage)] + "阶 · " + stepName(stage));
    }

    /**
     * **代表色** —— 问答弹窗、消散特效、觉醒光效都用它。
     *
     * <p>七问答的过程中，弹窗会随着"你现在最像谁"一点一点变成这条颜色。</p>
     */
    public int color() {
        return switch (this) {
            case CANDLE_KEEPER -> 0xFFFF8A3A;   // 烛守：烬火橙
            case SCAVENGER -> 0xFFC8A24A;       // 拾荒：遗物铜
            case SILENT -> 0xFF9A8AB8;          // 缄口：静默灰紫
            case SHADOW -> 0xFF5AA8E8;          // 追影：未及青
            case WALL -> 0xFFB8B8AE;            // 筑墙：界域石白
            case RETURN -> 0xFF9A5AD8;          // 归墟：终末紫
            case ECHO -> 0xFF52E8DC;            // 回声：回响青
        };
    }

    /** 按中文名反查（客户端只有名字时用）。 */
    public static Sequence byCn(String cn) {
        for (Sequence s : values()) {
            if (s.cn.equals(cn)) {
                return s;
            }
        }
        return null;
    }

    /**
     * **暗色副色** —— 和 {@link #color()} 配成一对，用来做**渐变文字**。
     *
     * <p>一个序列的题字不该是一块死色：从暗的这一头过渡到亮的那一头，
     * 才有"东西在里面烧"的感觉。比如烛守是「烬暗红 → 亮橙黄」。</p>
     */
    public int colorDark() {
        return switch (this) {
            case CANDLE_KEEPER -> 0xFF6E2408;   // 烛守：烬
            case SCAVENGER -> 0xFF5E4A1C;       // 拾荒：旧铜
            case SILENT -> 0xFF40384E;          // 缄口：暗灰紫
            case SHADOW -> 0xFF173E68;          // 追影：远影
            case WALL -> 0xFF5E5E56;            // 筑墙：石影
            case RETURN -> 0xFF3E165E;          // 归墟：沉底
            case ECHO -> 0xFF0A4A46;            // 回声：深水
        };
    }

    /** 按序号取（越界给中性色）。 */
    public static int colorOf(int ordinal) {
        Sequence[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal].color() : 0xFF8AA8A8;
    }

    public static int colorDarkOf(int ordinal) {
        Sequence[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal].colorDark() : 0xFF4A5A5A;
    }

    public static Sequence byIndex(int index) {
        Sequence[] all = values();
        return index >= 0 && index < all.length ? all[index] : null;
    }

    /** 觉醒即锁定 —— 存在玩家身上的那一条。没觉醒返回 null。 */
    public static Sequence of(net.minecraft.world.entity.player.Player player) {
        String id = player.getPersistentData().getString("GRSequence");
        if (id.isEmpty()) {
            return null;
        }
        for (Sequence s : values()) {
            if (s.name().equals(id)) {
                return s;
            }
        }
        return null;
    }

    /** **觉醒**某条序列 —— 只写一次，之后再调不生效（不可更换）。 */
    public static boolean awaken(net.minecraft.world.entity.player.Player player, Sequence sequence) {
        if (of(player) != null || sequence == null) {
            return false;
        }
        player.getPersistentData().putString("GRSequence", sequence.name());
        return true;
    }
}
