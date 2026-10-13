package com.habaiwa.goetyrevenant.common.ritual;

import com.Polarice3.Goety.api.ritual.IRitualType;
import com.Polarice3.Goety.api.ritual.RitualType;
import com.Polarice3.Goety.common.blocks.entities.DarkAltarBlockEntity;
import com.Polarice3.Goety.common.blocks.entities.RitualBlockEntity;
import com.Polarice3.Goety.common.crafting.RitualRecipe;
import com.habaiwa.goetyrevenant.common.embed.BodyModData;
import com.habaiwa.goetyrevenant.common.embed.OrganType;
import com.habaiwa.goetyrevenant.init.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * **血肉仪式** —— 我们自己的仪式种类，挂在**本体的仪式系统**上。
 *
 * <h3>它不是"另一种黑暗祭坛仪式"</h3>
 * <p>本体 {@code IRitualType} 的名字会进 {@code RitualType.RITUAL_TYPE_LIST}，
 * 而 {@code RitualRequirements#getProperStructure} 拿 {@code craftType} 去查的正是这张表。</p>
 * <p>所以配方里写 {@code "craftType": "flesh_devour"}，本体就会回调到这里 ——
 * 我们就这么"寄生"进了本体的仪式系统，JEI、进度条、材料消耗全部白送。</p>
 *
 * <h3>两个种类</h3>
 * <table border="1">
 *   <tr><th>名字</th><th>craftType</th><th>做什么</th></tr>
 *   <tr><td>献祭</td><td>{@code flesh_sacrifice}</td><td>把五脏献出去 → 成神</td></tr>
 *   <tr><td>吞合</td><td>{@code flesh_devour}</td><td>把聚晶"吞"进对应的窍</td></tr>
 * </table>
 *
 * <h3>祭坛与笼子</h3>
 * <p>仪式只在**血肉祭坛**（{@code goety_revenant:flesh_altar}）上发动 ——
 * 因为只有它的方块实体认血肉之笼。祭坛正下方必须是**血肉之笼**（顶替诅咒之笼），
 * 笼子里要有灵魂图腾和灵魂能量给仪式扣。</p>
 */
public final class FleshRitual implements IRitualType {
    /** 成神要求开几个窍。 */
    /** 献祭要求开几窍 —— 读配置。 */
    public static int orificesRequired() {
        return Math.max(0, com.habaiwa.goetyrevenant.common.config.RevenantConfig.sacrificeOrifices);
    }

    /** 献祭 / 吞合。 */
    public enum Kind {
        SACRIFICE("flesh_sacrifice"),
        DEVOUR("flesh_devour");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }
    }

    private final Kind kind;

    public FleshRitual(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return this.kind;
    }

    @Override
    public String getName() {
        return this.kind.id();
    }

    @Override
    public ItemStack getJeiIcon() {
        return new ItemStack(ModBlocks.FLESH_ALTAR.get());
    }

    /** 注册两个仪式种类（在 mod 构造里调）。 */
    public static void register() {
        for (Kind kind : Kind.values()) {
            RitualType.addRitualType(kind.id(), new FleshRitual(kind));
        }
    }

    /* ------------------------------------------------------------------ */
    /*  给血肉祭坛用的判定                                                  */
    /* ------------------------------------------------------------------ */

    /**
     * 这条配方是不是血肉仪式？
     *
     * <p>血肉祭坛靠它把本体的召唤 / 转化 / 附魔仪式挡在门外 ——
     * 那些仪式的 {@code craftType} 是 {@code magic} / {@code necroturgy} 之类，查不到我们头上。</p>
     */
    public static boolean isFleshRitual(RitualRecipe recipe) {
        if (recipe == null || recipe.getCraftType() == null) {
            return false;
        }
        return RitualType.getRitualType(recipe.getCraftType()) instanceof FleshRitual;
    }

    /** 取这条配方对应的那个血肉仪式种类，不是的话返回 null。 */
    public static FleshRitual of(RitualRecipe recipe) {
        if (recipe == null || recipe.getCraftType() == null) {
            return null;
        }
        IRitualType type = RitualType.getRitualType(recipe.getCraftType());
        return type instanceof FleshRitual flesh ? flesh : null;
    }

    /**
     * **发动前的最后一道门**（血肉祭坛在 {@code activate} 里调，只提示一次）。
     *
     * <p>骰子不该在仪式跑完之后才摇 —— 献祭要开六窍、吞合要有一枚能吞的聚晶，
     * 不满足就当场拦下来，别让玩家白掏一堆材料。</p>
     */
    public static boolean canStart(Player player, ItemStack activationItem, RitualRecipe recipe) {
        FleshRitual flesh = of(recipe);
        if (flesh == null) {
            return false;
        }
        switch (flesh.kind) {
            case SACRIFICE -> {
                int open = 0;
                for (OrganType organ : OrganType.values()) {
                    if (BodyModData.isSlotUnlocked(player, organ)) {
                        open++;
                    }
                }
                if (open < orificesRequired()) {
                    player.displayClientMessage(Component.translatable(
                            "message.goety_revenant.ritual_need_orifices", orificesRequired(), open)
                            .withStyle(ChatFormatting.RED), true);
                    return false;
                }
            }
            case DEVOUR -> {
                if (!FleshRites.isEmbeddable(activationItem)) {
                    player.displayClientMessage(Component.translatable(
                            "message.goety_revenant.ritual_no_focus").withStyle(ChatFormatting.RED), true);
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * **方块要求** —— 本体的 {@code RitualRequirements#getProperStructure} 会回调到这里，
     * 而且**每 tick 都调一次**。
     *
     * <p>所以这里<b>什么都不做</b>，直接放行：祭坛与血肉之笼的判定已经由
     * {@code FleshAltarBlockEntity} 在驱动仪式之前把关了，不必在这条热路径上重复数一遍方块。</p>
     */
    @Override
    public boolean getRequirement(RitualBlockEntity altar, Player player, BlockPos pos, Level level) {
        return true;
    }

    /**
     * **仪式走完的效果**。
     *
     * <h3>为什么是 onFinishRitual 而不是 onPerformRitual</h3>
     * <p>{@code Ritual#update} 里调的是 {@code onPerformRitual}（仪式推进过程中），
     * 而 {@code onFinishRitual} 只在 {@code Ritual#finish} 里调一次
     * （{@code stopRitual(true)} → {@code finish}）。</p>
     * <p>嵌入聚晶、献祭五脏这种事<b>只能发生一次</b>，所以挂在 finish 上。</p>
     *
     * <p>调用顺序也正好合用：{@code CraftItemRitual#finish} 先 {@code super.finish(...)}
     * （也就是这里），<b>之后</b>才 {@code activationItem.shrink(1)} ——
     * 所以我们拿到的聚晶还是完整的。</p>
     */
    @Override
    public void onFinishRitual(Level world, BlockPos altarPos, DarkAltarBlockEntity altar,
                               Player player, ItemStack activationItem) {
        if (world.isClientSide || !(player instanceof net.minecraft.server.level.ServerPlayer server)) {
            return;
        }
        switch (this.kind) {
            case DEVOUR -> FleshRites.devour(server, activationItem);
            case SACRIFICE -> FleshRites.sacrifice(server);
        }
    }
}
