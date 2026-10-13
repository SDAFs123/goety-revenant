package com.habaiwa.goetyrevenant.common.body;

import com.Polarice3.Goety.api.magic.SpellType;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * **七窍**的槽位。
 *
 * <p>五脏（五行）+ 头部的**两窍**（灵窍 / 神窍）= 七窍。</p>
 * <ul>
 *   <li><b>灵窍</b> —— 亡灵派系（NECROMANCY）</li>
 *   <li><b>神窍</b> —— 不属于任何五行的「外来」派系（整合包附属自己
 *       `SpellType.create(...)` 造的那些），以及本体留空的 NONE。
 *       它也是**登神**的席位。</li>
 * </ul>
 *
 * <p>映射集中在枚举里，要调只改这一处。</p>
 */
public enum OrganType {
    /** 心 —— 火 */
    HEART("heart", "fire", false, SpellType.NETHER, SpellType.STORM),
    /** 肝 —— 木 */
    LIVER("liver", "wood", false, SpellType.WILD),
    /** 脾 —— 土 */
    SPLEEN("spleen", "earth", false, SpellType.GEOMANCY),
    /** 肺 —— 金 */
    LUNG("lung", "metal", false, SpellType.ILL, SpellType.WIND),
    /** 肾 —— 水 */
    KIDNEY("kidney", "water", false, SpellType.FROST, SpellType.ABYSS, SpellType.VOID),
    /** 灵窍 —— 亡灵，头部左位 */
    SPIRIT_APERTURE("spirit_aperture", "spirit", true, SpellType.NECROMANCY);

    private final String id;
    /** 这个窍的**元素名**（五行 + 灵）—— 开窍魔药按它命名。 */
    private final String element;
    private final boolean special;
    private final List<SpellType> accepted;

    OrganType(String id, String element, boolean special, SpellType... accepted) {
        this.id = id;
        this.element = element;
        this.special = special;
        this.accepted = List.of(accepted);
    }

    public String getId() {
        return this.id;
    }

    /** 元素 id：`fire` / `wood` / `earth` / `metal` / `water` / `spirit`。 */
    public String getElement() {
        return this.element;
    }

    /** 元素显示名（火 / 木 / 土 / 金 / 水 / 灵）。 */
    public Component elementName() {
        return Component.translatable("element.goety_revenant." + this.element);
    }

    /** 灵窍是特殊槽位（亡灵专属），不和五脏抢位置。 */
    public boolean isSpecial() {
        return this.special;
    }

    public List<SpellType> acceptedTypes() {
        return this.accepted;
    }

    public boolean accepts(SpellType type) {
        return this.accepted.contains(type);
    }

    /**
     * 由派系反查该进哪个窍（每个派系只对应一个窍，所以嵌入不用先选器官）。
     *
     * <p><b>永远不返回 null</b> —— 整合包里的附属模组会自己
     * `SpellType.create(...)` 造新派系，那些派系不在我们的表里。所以：</p>
     * <ol>
     *   <li>先在五行表里精确匹配</li>
     *   <li>匹配不到就**按派系名里的关键词猜五行**（派系名带 fire 的进心、带 frost 的进肺……）</li>
     *   <li>还猜不出就进 **灵窍**（它专门收「不属于这个世界的」外来派系）</li>
     * </ol>
     */
    public static OrganType forSpellType(SpellType type) {
        if (type == null) {
            return SPIRIT_APERTURE;
        }
        for (OrganType organ : values()) {
            if (organ.accepts(type)) {
                return organ;
            }
        }
        OrganType guessed = guessByName(String.valueOf(type).toLowerCase(java.util.Locale.ROOT));
        return guessed == null ? SPIRIT_APERTURE : guessed;
    }

    /** 按派系名里的关键词猜该进哪个窍（五行 + 灵）。 */
    @javax.annotation.Nullable
    private static OrganType guessByName(String name) {
        if (match(name, "fire", "nether", "inferno", "blaze", "flame", "solar", "ember")) {
            return HEART;
        }
        if (match(name, "frost", "ice", "snow", "winter", "cryo")) {
            return LUNG;
        }
        if (match(name, "wind", "aero", "air", "sky", "zephyr", "gale", "storm", "thunder", "lightning")) {
            return LUNG;
        }
        if (match(name, "geo", "earth", "stone", "rock", "terra", "quake")) {
            return SPLEEN;
        }
        if (match(name, "water", "aqua", "abyss", "ocean", "sea", "tide", "deep")) {
            return KIDNEY;
        }
        if (match(name, "wild", "nature", "life", "plant", "wood", "forest", "bloom")) {
            return LIVER;
        }
        if (match(name, "metal", "iron", "steel", "forge")) {
            return LUNG;
        }
        if (match(name, "void", "end", "null", "warp", "rift", "gravity", "astral", "star", "cosmic")) {
            return KIDNEY;
        }
        if (match(name, "necro", "death", "soul", "undead", "wither", "grave", "spirit", "phantom")) {
            return SPIRIT_APERTURE;
        }
        if (match(name, "ill", "raid", "pillage", "war")) {
            return HEART;
        }
        return null;
    }

    private static boolean match(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** 五脏（不含灵窍）。 */
    public static OrganType[] viscera() {
        return new OrganType[]{HEART, LIVER, SPLEEN, LUNG, KIDNEY};
    }

    public Component displayName() {
        return Component.translatable("organ.goety_revenant." + this.id);
    }

    /**
     * 这个窍的魔药颜色（给 `potion_overlay` 那层染色，见客户端 {@code RegisterColorHandlersEvent.Item}）。
     * 五脏按五行取色，灵窍取亡灵紫。
     */
    public int brewColor() {
        return switch (this) {
            case HEART -> 0xE8503A;
            case LIVER -> 0x4BC46B;
            case SPLEEN -> 0xC4913A;
            case LUNG -> 0xE8E0B0;
            case KIDNEY -> 0x3D6BE8;
            case SPIRIT_APERTURE -> 0x8B4BD4;
        };
    }

    /**
     * 这个窍对应的「开窍魔药」注册名。
     *
     * <p>按**元素**命名（`fire_brew` / `wood_brew` / `earth_brew` / `metal_brew` /
     * `water_brew` / `spirit_brew`），不按器官 —— 金木水火土一眼就对得上五行。</p>
     */
    public String brewId() {
        return this.element + "_brew";
    }

    public Component brewName() {
        return Component.translatable("item.goety_revenant." + brewId());
    }
}
