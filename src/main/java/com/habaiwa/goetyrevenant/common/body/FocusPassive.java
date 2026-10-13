package com.habaiwa.goetyrevenant.common.body;

import com.Polarice3.Goety.api.items.magic.IFocus;
import com.Polarice3.Goety.api.magic.ISpell;
import com.Polarice3.Goety.api.magic.SpellType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * 聚晶嵌进身体后的**被动能力**。
 *
 * <h3>一枚聚晶给两条被动</h3>
 * <ol>
 *   <li><b>元素扭曲（主）</b> —— 聚晶进去的那个**窍**会把它原本的性子扭一下。
 *       火窍把什么都往凶里扭、水窍往韧里扭、土窍往硬里扭、金窍往锐里扭、木窍往久里扭，
 *       而<b>灵窍是反转</b> —— 召唤的会被扭成吞噬、守护的会被扭成掠夺。
 *       这就是「装进身体之后效果产生了扭曲」。</li>
 *   <li><b>聚晶本职（次）</b> —— 它自己原本的性子还在，只是打了对折。</li>
 * </ol>
 *
 * <h3>越难做的聚晶越强</h3>
 * <p>数值统一乘一个**等级倍率**（{@link #tierMultiplier}），等级来自三处取最大：</p>
 * <ul>
 *   <li><b>稀有度</b>（物品名字的颜色）—— 普通 1 / 罕见 2 / 稀有 3 / 史诗 4</li>
 *   <li><b>注册名关键词</b> —— `greater_` / `supreme_` / `ancient_` / `astral_` … 拉到 4 级；
 *       `lesser_` / `crude_` … 压到 1 级（附属常用这些词）</li>
 *   <li><b>点名表</b> —— {@link FocusPassives#registerTier} 可以钉死某个聚晶的等级</li>
 * </ul>
 * <p>倍率 = `1 + 0.35 × (等级 - 1)`，所以史诗（4 级）= **2.05 倍**，普通 = 1 倍。</p>
 *
 * <h3>本职方向怎么定（四级查找）</h3>
 * <ol>
 *   <li>点名表 → 2. 派系表 → 3. 派系名关键词启发 → 4. 兜底 `战斗力 +2`</li>
 * </ol>
 * <p>所以整合包里 300 多枚聚晶**每一枚**都有被动。</p>
 */
public final class FocusPassive {
    /** 被动所属的方向。一个方向 = 一条属性。 */
    public enum Category {
        /** 行动：跑得快 */
        MOBILITY("mobility", Attributes.MOVEMENT_SPEED, 0.05D, true),
        /** 生存：血厚 */
        SURVIVAL("survival", Attributes.MAX_HEALTH, 4.0D, false),
        /** 战斗力：打得疼 */
        COMBAT("combat", Attributes.ATTACK_DAMAGE, 2.0D, false),
        /** 护甲：抗揍 */
        ARMOR("armor", Attributes.ARMOR, 2.0D, false),
        /** 韧性：扛穿甲 */
        TOUGHNESS("toughness", Attributes.ARMOR_TOUGHNESS, 2.0D, false),
        /** 击退：把人打飞 */
        KNOCKBACK("knockback", Attributes.ATTACK_KNOCKBACK, 0.5D, false),
        /** 坚毅：自己不被推动 */
        STEADFAST("steadfast", Attributes.KNOCKBACK_RESISTANCE, 0.1D, true),
        /** 迅捷：挥得快 */
        SWIFT("swift", Attributes.ATTACK_SPEED, 0.1D, false),
        /** 幸运：掉落与附魔运 */
        FORTUNE("fortune", Attributes.LUCK, 1.0D, false);

        private final String id;
        private final Attribute attribute;
        private final double defaultValue;
        private final boolean percent;

        Category(String id, Attribute attribute, double defaultValue, boolean percent) {
            this.id = id;
            this.attribute = attribute;
            this.defaultValue = defaultValue;
            this.percent = percent;
        }

        public String getId() {
            return this.id;
        }

        public Attribute attribute() {
            return this.attribute;
        }

        public double defaultValue() {
            return this.defaultValue;
        }

        public boolean percent() {
            return this.percent;
        }

        /** 显示名（行动 / 生存 / 战斗力……）。 */
        public Component displayName() {
            return Component.translatable("passive.goety_revenant." + this.id);
        }

        /** 数值显示（`+5%` / `+2`）。 */
        public String format(double value) {
            if (this.percent) {
                return "+" + Math.round(value * 100.0D) + "%";
            }
            if (value == Math.rint(value)) {
                return "+" + (long) value;
            }
            return "+" + value;
        }
    }

    /**
     * **元素扭曲表**：`TWIST[窍的序数][聚晶本职方向的序数] = 扭曲之后的方向`。
     *
     * <p>行的顺序 = {@link OrganType} 的声明顺序：心(火) / 肝(木) / 脾(土) / 肺(金) / 肾(水) / 灵窍。<br>
     * 列的顺序 = {@link Category} 的声明顺序：行动 / 生存 / 战斗力 / 护甲 / 韧性 / 击退 / 坚毅 / 迅捷 / 幸运。</p>
     *
     * <p>前五个元素是「往自己的性子扭」，**灵窍是反转**（召唤→吞噬、守护→掠夺那一类）。</p>
     */
    private static final Category[][] TWIST = {
            // 心 · 火 —— 攻伐：什么都往凶里扭
            {Category.COMBAT, Category.COMBAT, Category.SWIFT, Category.KNOCKBACK, Category.COMBAT,
                    Category.KNOCKBACK, Category.SWIFT, Category.COMBAT, Category.FORTUNE},
            // 肝 · 木 —— 生长：往长久、往运里扭
            {Category.MOBILITY, Category.SURVIVAL, Category.FORTUNE, Category.SURVIVAL, Category.SURVIVAL,
                    Category.MOBILITY, Category.FORTUNE, Category.MOBILITY, Category.FORTUNE},
            // 脾 · 土 —— 厚重：往硬里扭
            {Category.TOUGHNESS, Category.ARMOR, Category.TOUGHNESS, Category.ARMOR, Category.TOUGHNESS,
                    Category.STEADFAST, Category.ARMOR, Category.TOUGHNESS, Category.FORTUNE},
            // 肺 · 金 —— 锐利：往快、往准里扭
            {Category.SWIFT, Category.ARMOR, Category.COMBAT, Category.ARMOR, Category.TOUGHNESS,
                    Category.KNOCKBACK, Category.STEADFAST, Category.SWIFT, Category.FORTUNE},
            // 肾 · 水 —— 绵长：往韧里扭
            {Category.MOBILITY, Category.SURVIVAL, Category.SURVIVAL, Category.ARMOR, Category.STEADFAST,
                    Category.STEADFAST, Category.SURVIVAL, Category.MOBILITY, Category.FORTUNE},
            // 灵窍 · 灵 —— **反转**：把方向翻到它的对立面
            {Category.STEADFAST, Category.COMBAT, Category.SURVIVAL, Category.SWIFT, Category.KNOCKBACK,
                    Category.TOUGHNESS, Category.MOBILITY, Category.ARMOR, Category.COMBAT},
    };

    /** 本职那条被动打几折。 */
    public static final double SECONDARY_RATIO = 0.5D;
    /** 每升一级加多少倍率。 */
    public static final double TIER_STEP = 0.35D;

    /** 完全没线索时的兜底。 */
    public static final FocusPassive FALLBACK = new FocusPassive(Category.COMBAT, Category.COMBAT.defaultValue());

    private static final Map<ResourceLocation, FocusPassive> REGISTRY = new HashMap<>();
    private static final Map<SpellType, FocusPassive> BY_SPELL_TYPE = new HashMap<>();
    private static final Map<ResourceLocation, Integer> PINNED_TIERS = new HashMap<>();

    private final Category category;
    private final double value;

    public FocusPassive(Category category, double value) {
        this.category = category;
        this.value = value;
    }

    public Category category() {
        return this.category;
    }

    public double value() {
        return this.value;
    }

    // ===== 登记 =====

    public static void register(String itemId, Category category, double value) {
        REGISTRY.put(new ResourceLocation(itemId), new FocusPassive(category, value));
    }

    public static void register(String itemId, Category category) {
        register(itemId, category, category.defaultValue());
    }

    public static void registerType(SpellType type, Category category, double value) {
        BY_SPELL_TYPE.put(type, new FocusPassive(category, value));
    }

    public static void registerType(SpellType type, Category category) {
        registerType(type, category, category.defaultValue());
    }

    /** 钉死某个聚晶的等级（1~5）。作者觉得稀有度不准时用这个。 */
    public static void registerTier(String itemId, int tier) {
        PINNED_TIERS.put(new ResourceLocation(itemId), Math.max(1, Math.min(5, tier)));
    }

    // ===== 本职方向的四级查找 =====

    /** 解析一个聚晶的**本职**被动 —— 永远不返回 null。 */
    public static FocusPassive resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return FALLBACK;
        }
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        FocusPassive explicit = REGISTRY.get(id);
        if (explicit != null) {
            return explicit;
        }
        SpellType type = spellTypeOf(stack);
        if (type == null) {
            return FALLBACK;
        }
        FocusPassive byType = BY_SPELL_TYPE.get(type);
        if (byType != null) {
            return byType;
        }
        return guessByTypeName(String.valueOf(type));
    }

    @Nullable
    public static FocusPassive find(ResourceLocation itemId) {
        return REGISTRY.get(itemId);
    }

    @Nullable
    public static SpellType spellTypeOf(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IFocus focus)) {
            return null;
        }
        ISpell spell = focus.getSpell();
        return spell == null ? null : spell.getSpellType();
    }

    /** 附属自己 `SpellType.create(...)` 的新派系 —— 按名字里的关键词猜方向。 */
    public static FocusPassive guessByTypeName(String typeName) {
        String name = typeName == null ? "" : typeName.toLowerCase(Locale.ROOT);
        if (contains(name, "fire", "nether", "inferno", "blaze", "flame", "solar", "ember")) {
            return category(Category.COMBAT);
        }
        if (contains(name, "frost", "ice", "snow", "winter", "cryo")) {
            return category(Category.ARMOR);
        }
        if (contains(name, "wind", "aero", "air", "sky", "zephyr", "gale")) {
            return category(Category.MOBILITY);
        }
        if (contains(name, "storm", "thunder", "lightning", "shock", "volt")) {
            return category(Category.SWIFT);
        }
        if (contains(name, "geo", "earth", "stone", "rock", "terra", "quake")) {
            return category(Category.TOUGHNESS);
        }
        if (contains(name, "water", "aqua", "abyss", "ocean", "sea", "tide", "deep")) {
            return category(Category.SURVIVAL);
        }
        if (contains(name, "wild", "nature", "life", "plant", "wood", "forest", "bloom")) {
            return category(Category.SURVIVAL);
        }
        if (contains(name, "metal", "iron", "steel", "gold", "forge")) {
            return category(Category.ARMOR);
        }
        if (contains(name, "void", "end", "null", "warp", "rift", "gravity")) {
            return category(Category.MOBILITY);
        }
        if (contains(name, "astral", "star", "lunar", "moon", "cosmic", "celestial", "nebula", "comet")) {
            return category(Category.FORTUNE);
        }
        if (contains(name, "necro", "death", "soul", "undead", "wither", "grave", "spirit", "phantom", "summon")) {
            return category(Category.FORTUNE);
        }
        if (contains(name, "ill", "raid", "pillage", "brute", "war")) {
            return category(Category.KNOCKBACK);
        }
        return FALLBACK;
    }

    private static FocusPassive category(Category category) {
        return new FocusPassive(category, category.defaultValue());
    }

    private static boolean contains(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    // ===== 等级 =====

    /** 这枚聚晶的等级（1~5）。 */
    public static int tierOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 1;
        }
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        Integer pinned = PINNED_TIERS.get(id);
        if (pinned != null) {
            return pinned;
        }

        int tier = 1;
        net.minecraft.world.item.Rarity rarity = stack.getRarity();
        if (rarity == net.minecraft.world.item.Rarity.UNCOMMON) {
            tier = 2;
        } else if (rarity == net.minecraft.world.item.Rarity.RARE) {
            tier = 3;
        } else if (rarity == net.minecraft.world.item.Rarity.EPIC) {
            tier = 4;
        }

        // 注册名里的关键词（附属很爱用 greater_ / supreme_ 这类前缀）
        String path = id.getPath().toLowerCase(Locale.ROOT);
        if (contains(path, "lesser", "minor", "crude", "basic", "small", "apprentice", "weak", "broken")) {
            tier = Math.min(tier, 1);
        }
        if (contains(path, "greater", "master", "supreme", "grand", "elder", "ancient", "prime",
                "ultimate", "forbidden", "divine", "astral", "black_hole", "meteor", "c_moon", "true_")) {
            tier = Math.max(tier, 4);
        }
        return Math.max(1, Math.min(5, tier));
    }

    /** 等级倍率：1 级 = 1.0，4 级 = 2.05 倍。 */
    public static double tierMultiplier(ItemStack stack) {
        return 1.0D + TIER_STEP * (tierOf(stack) - 1);
    }

    // ===== 扭曲后的实际加成 =====

    /** 这枚聚晶装进这个窍之后，主被动落在哪个方向（= 扭曲之后的方向）。 */
    public static Category twistedCategory(ItemStack stack, OrganType organ) {
        FocusPassive base = resolve(stack);
        Category[][] table = TWIST;
        int row = Math.max(0, Math.min(table.length - 1, organ.ordinal()));
        int col = Math.max(0, Math.min(table[row].length - 1, base.category.ordinal()));
        return table[row][col];
    }

    /**
     * 这枚聚晶装进这个窍之后，**某个方向**实际能拿多少。
     *
     * @return 0 表示这个方向不给（{@link Embedding#applyAll} 会跳过）
     */
    public static double grantedAt(ItemStack stack, OrganType organ, Category category) {
        FocusPassive base = resolve(stack);
        double mult = tierMultiplier(stack);
        Category twisted = twistedCategory(stack, organ);
        double mainValue = base.value * mult;
        double ownValue = base.value * mult * SECONDARY_RATIO;

        if (twisted == base.category) {
            // 扭曲之后和本职撞在同一个方向 → 合成一条
            return category == twisted ? round(category, mainValue + ownValue) : 0.0D;
        }
        if (category == twisted) {
            return round(category, mainValue);
        }
        if (category == base.category) {
            return round(category, ownValue);
        }
        return 0.0D;
    }

    /** 装进某个窍之后实际会拿到的全部被动（界面 / tooltip 用，按「主 → 次」排好序）。 */
    public static List<Map.Entry<Category, Double>> grants(ItemStack stack, OrganType organ) {
        List<Map.Entry<Category, Double>> out = new ArrayList<>();
        for (Category category : Category.values()) {
            double value = grantedAt(stack, organ, category);
            if (value != 0.0D) {
                out.add(Map.entry(category, value));
            }
        }
        return out;
    }

    private static double round(Category category, double value) {
        if (category.percent) {
            // 百分比按 0.5% 步进
            return Math.round(value * 200.0D) / 200.0D;
        }
        // 平值按 0.5 步进
        return Math.round(value * 2.0D) / 2.0D;
    }

    // ===== 属性修饰符 =====

    public static java.util.UUID modifierId(OrganType organ, Attribute attribute) {
        String key = "gr_embed:" + organ.getId() + ":" + attribute.getDescriptionId();
        return java.util.UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static AttributeModifier modifier(OrganType organ, Category category, double value) {
        return new AttributeModifier(modifierId(organ, category.attribute()),
                "goety_revenant.embedded." + organ.getId(),
                value,
                AttributeModifier.Operation.ADDITION);
    }
}
