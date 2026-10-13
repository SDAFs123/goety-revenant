package com.habaiwa.goetyrevenant.common.tooltip;

import com.habaiwa.goetyrevenant.client.BodyClientState;
import com.habaiwa.goetyrevenant.common.compat.RevelationHalo;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * **给启示录的光环加上我们的内容**。
 *
 * <h3>为什么要这么做</h3>
 * <p>启示录闭源、没有 API，我们改不了 {@code AscensionHalo} 这个类。
 * 但「往别人的物品 tooltip 里追加几行」有官方入口 —— Forge 的 {@link ItemTooltipEvent}，
 * 和我们对所有聚晶做的是同一套办法（零 Mixin、零侵入）。</p>
 *
 * <h3>文字风格照抄启示录自己的</h3>
 * <p>它的 tooltip 是这套约定（读它的语言文件看出来的）：</p>
 * <table border="1">
 *   <tr><th>用途</th><th>颜色码</th><th>ChatFormatting</th></tr>
 *   <tr><td>经文 / 叙述</td><td>§5§o</td><td>DARK_PURPLE + ITALIC</td></tr>
 *   <tr><td>神圣强调</td><td>§6</td><td>GOLD</td></tr>
 *   <tr><td>能力描述</td><td>§8§o</td><td>DARK_GRAY + ITALIC</td></tr>
 *   <tr><td>段落标题</td><td>§7§l</td><td>GRAY + BOLD</td></tr>
 * </table>
 * <p>而且它是**按住 SHIFT 才展开**（`tooltip.goety_revelation.tip0` = "按下[SHIFT]显示更多内容"）。
 * 我们跟着这个习惯走 —— 不然我们的字会比它自己的还显眼，喧宾夺主。</p>
 */
// 同样：已经在 GoetyRevenant.java 里手动注册过了，不要再加类级注解。
public final class RevelationHaloTooltip {
    private RevelationHaloTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        RevelationHalo.Tier tier = RevelationHalo.tierOf(stack);
        if (tier == null) {
            return;
        }
        List<Component> lines = event.getToolTip();
        boolean expanded = net.minecraft.client.gui.screens.Screen.hasShiftDown();

        if (!expanded) {
            // 收起状态：只留一句提示，风格和它自己的 tip0 一致
            lines.add(Component.empty());
            lines.add(Component.translatable("tooltip.goety_revenant.halo.hint")
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
            return;
        }

        // 展开：照它的分段写法来
        lines.add(Component.empty());
        lines.add(Component.translatable("tooltip.goety_revenant.halo.header")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD));

        int opened = BodyClientState.openedCount();
        int total = BodyClientState.total();
        lines.add(Component.translatable("tooltip.goety_revenant.halo.orifices", opened, total)
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        lines.add(Component.translatable("tooltip.goety_revenant.halo.energy",
                        BodyClientState.soulEnergy(), BodyClientState.energyMax())
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

        // 按光环阶级给一句"经文"，颜色跟着启示录的叙述色
        lines.add(Component.translatable("tooltip.goety_revenant.halo." + RevelationHalo.describe(tier))
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }
}
