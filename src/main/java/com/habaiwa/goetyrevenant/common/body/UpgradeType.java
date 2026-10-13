package com.habaiwa.goetyrevenant.common.body;

/**
 * **强化被动** —— 枝干上那些叶子能选的方向。
 *
 * <p>一共 <b>12 个</b>，按回魂自己的体系分成四个族。<b>每个族三个</b>，
 * 每个强化槽只会给出**某一个族**的三个选项（也就是"三选一"），
 * 所以想要的东西没法全堆在一棵树上 —— 这天然就是"没有全能选项"。</p>
 *
 * <h3>四族</h3>
 * <table>
 *   <tr><td><b>魂</b></td><td>灵魂能量本身：吸、攒、回</td></tr>
 *   <tr><td><b>战</b></td><td>攻 / 守 / 速 —— 最直白的三个方向</td></tr>
 *   <tr><td><b>携</b></td><td>连携与元素反应：烧得重、共鸣强、串得多</td></tr>
 *   <tr><td><b>灵</b></td><td>神识 / 灵化 / 灵魂温度</td></tr>
 * </table>
 *
 * <p>每个方向都带代价 —— 加了什么就一定拿走点什么。</p>
 */
public final class UpgradeType {

    /** 四个族。 */
    public enum Group {
        SOUL("魂"), WAR("战"), COMBO("携"), SPIRIT("灵");

        private final String cn;

        Group(String cn) {
            this.cn = cn;
        }

        public String cn() {
            return this.cn;
        }
    }

    /** 一个被动方向。 */
    public record Type(String id, String cn, Group group, int color, String gain, String cost) {
    }

    /* ------------------------------------------------------------------ */
    /*  12 个方向                                                          */
    /* ------------------------------------------------------------------ */

    private static final Type[] ALL = {
            // ===== 魂：灵魂能量 =====
            new Type("soul_eater", "噬灵", Group.SOUL, 0xFF9A6AE8,
                    "击杀敌人回 3% 灵魂能量上限", "灵魂上限 -2%"),
            new Type("soul_surge", "魂涌", Group.SOUL, 0xFF8A5AD8,
                    "灵魂能量上限 +8%", "受到伤害 +1%"),
            new Type("soul_condense", "凝魂", Group.SOUL, 0xFFB08AF0,
                    "灵魂能量回复 +10%", "伤害 -2%"),

            // ===== 战：攻 / 守 / 速 =====
            new Type("force", "锐爪", Group.WAR, 0xFFD8544A,
                    "伤害 +6%", "受到伤害 +2%"),
            new Type("ward", "铁骨", Group.WAR, 0xFF5A9BD8,
                    "受到伤害 -5%", "移速 -1.5%"),
            new Type("swift", "迅羽", Group.WAR, 0xFF52E8DC,
                    "聚晶冷却 -6%", "伤害 -3%"),

            // ===== 携：连携与元素反应 =====
            new Type("kindle", "灼魂", Group.COMBO, 0xFFE08A3A,
                    "连携伤害 +10%", "受到伤害 +1%"),
            new Type("resonance", "共鸣", Group.COMBO, 0xFFA86AD8,
                    "同元素反应效果 +15%", "冷却 +2%"),
            new Type("chain_lightning", "链雷", Group.COMBO, 0xFF6AD8F0,
                    "连携多串 1 个目标（每 2 槽）", "灵魂上限 -4%"),

            // ===== 灵：神识 / 灵化 / 温度 =====
            new Type("deep_sense", "深识", Group.SPIRIT, 0xFF52E8DC,
                    "神识半径 +12%", "受到伤害 +1%"),
            new Type("ghost_step", "幽步", Group.SPIRIT, 0xFF8AD8B0,
                    "灵化与灵魂状态移速 +8%", "伤害 -2%"),
            new Type("endure", "熬火", Group.SPIRIT, 0xFFD8A03A,
                    "高温时额外减伤 3%", "低温时受伤 +2%"),
    };

    private UpgradeType() {
    }

    public static Type[] all() {
        return ALL;
    }

    public static Type byIndex(int index) {
        return index >= 0 && index < ALL.length ? ALL[index] : null;
    }

    public static Type byId(String id) {
        for (Type t : ALL) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }

    /** 某个族的三个方向（就是一个强化槽给出的三个选项）。 */
    public static Type[] ofGroup(Group group) {
        java.util.List<Type> out = new java.util.ArrayList<>(3);
        for (Type t : ALL) {
            if (t.group() == group) {
                out.add(t);
            }
        }
        return out.toArray(new Type[0]);
    }

    /** 某个方向的序号（存进玩家数据用）。 */
    public static int indexOf(Type type) {
        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i] == type) {
                return i;
            }
        }
        return -1;
    }

    /** **第 index 个槽给哪一个族**（四个族轮流，所以每种都拿得到，但没法全堆一种）。 */
    public static Group groupOfSlot(int slotIndex) {
        Group[] all = Group.values();
        return all[Math.floorMod(slotIndex, all.length)];
    }
}
