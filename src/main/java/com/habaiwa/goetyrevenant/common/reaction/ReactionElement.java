package com.habaiwa.goetyrevenant.common.reaction;

import com.Polarice3.Goety.api.magic.SpellType;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * **元素反应的基础元素（七种）**。
 *
 * <pre>
 *   火 fire      冰 ice       雷 lightning
 *   灵魂 soul（死灵同族）      荒野 wild
 *   下界 nether  虚空 void
 * </pre>
 *
 * <p>与"五行开窍"那套（火木土金水 + 灵）<b>不是一回事</b>：
 * 那边管的是开窍与超载，这边管的是**战斗里的连携与反应**。</p>
 *
 * <h3>怎么认出一个聚晶是什么元素</h3>
 * <p>先看 {@code SpellType} 的映射表（可在 {@code element_reactions.json} 里改），
 * 认不出来就用默认元素。附属模组自造派系也能在配置里指派。</p>
 */
public enum ReactionElement {
    FIRE("火", "fire"),
    ICE("冰", "ice"),
    LIGHTNING("雷", "lightning"),
    SOUL("灵魂", "soul"),
    WILD("荒野", "wild"),
    NETHER("下界", "nether"),
    VOID("虚空", "void");

    private final String cn;
    private final String id;

    /** SpellType 的注册名 → 元素。由 {@link ElementReaction#load} 从数据文件填。 */
    private static final Map<String, ReactionElement> BY_SPELL_TYPE = new HashMap<>();
    private static ReactionElement fallback = SOUL;

    ReactionElement(String cn, String id) {
        this.cn = cn;
        this.id = id;
    }

    /** 中文名（火 / 冰 / 雷 / 灵魂 / 荒野 / 下界 / 虚空）。 */
    public String cn() {
        return this.cn;
    }

    public String id() {
        return this.id;
    }

    public static ReactionElement byId(String id) {
        for (ReactionElement e : values()) {
            if (e.id.equalsIgnoreCase(id) || e.cn.equals(id)) {
                return e;
            }
        }
        return null;
    }

    /* ------------------------------------------------------------------ */

    /** 配置加载时调用：把一个 SpellType 的注册名指派给某个元素。 */
    public static void map(String spellTypeId, ReactionElement element) {
        BY_SPELL_TYPE.put(spellTypeId.toLowerCase(java.util.Locale.ROOT), element);
    }

    /** 配置里写的"认不出来时用哪个元素"。 */
    public static void setFallback(@Nullable ReactionElement element) {
        fallback = element == null ? SOUL : element;
    }

    /** 这个聚晶（法术）是什么元素。 */
    public static ReactionElement of(@Nullable SpellType type) {
        if (type == null) {
            return fallback;
        }
        ReactionElement mapped = BY_SPELL_TYPE.get(spellTypeId(type));
        if (mapped != null) {
            return mapped;
        }
        return guessFromName(spellTypeId(type));
    }

    /**
     * SpellType 的标识名。
     *
     * <p>诡厄巫法的 {@code SpellType} 是个**枚举**（不是注册表项），
     * 所以直接用枚举名小写：{@code nether} / {@code frost} / {@code storm} /
     * {@code necromancy} / {@code wild} / {@code void} / {@code abyss} ……</p>
     */
    public static String spellTypeId(SpellType type) {
        try {
            return type.name().toLowerCase(java.util.Locale.ROOT);
        } catch (Throwable t) {
            return "";
        }
    }

    /** 名字里带 frost/ice、storm/lightning 这类关键词时也能认出来。 */
    private static ReactionElement guessFromName(String id) {
        String s = id.toLowerCase(java.util.Locale.ROOT);
        if (s.contains("frost") || s.contains("ice") || s.contains("snow")) {
            return ICE;
        }
        if (s.contains("storm") || s.contains("light") || s.contains("thunder")) {
            return LIGHTNING;
        }
        if (s.contains("nether") || s.contains("flame") || s.contains("fire") || s.contains("blaze")) {
            return NETHER;
        }
        if (s.contains("void") || s.contains("abyss") || s.contains("end")) {
            return VOID;
        }
        if (s.contains("wild") || s.contains("nature") || s.contains("geo")) {
            return WILD;
        }
        if (s.contains("necro") || s.contains("soul") || s.contains("death") || s.contains("spirit")) {
            return SOUL;
        }
        return fallback;
    }
}
