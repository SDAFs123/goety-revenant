package com.habaiwa.goetyrevenant.client.input;

import com.habaiwa.goetyrevenant.common.network.BodyActionMessage;
import com.habaiwa.goetyrevenant.common.network.RevenantNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** 「改造身体」界面的按键。默认 B，可在设置里改。 */
@Mod.EventBusSubscriber(modid = com.habaiwa.goetyrevenant.GoetyRevenant.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BodyKeybinds {
    public static final String CATEGORY = "key.categories.goety_revenant";

    public static final KeyMapping OPEN_BODY = new KeyMapping(
            "key.goety_revenant.body", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);

    /** 聚晶轮盘。 */
    public static final KeyMapping OPEN_RADIAL = new KeyMapping(
            "key.goety_revenant.radial", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    /** 主动灵魂出窍（去过哭泣之井之后随时可用，不需要物品）。 */
    public static final KeyMapping SOUL_OUT = new KeyMapping(
            "key.goety_revenant.soul_out", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    /** 神识扫视 —— 主动把当前神识半径内的东西报一遍。 */
    public static final KeyMapping SHEN_SHI = new KeyMapping(
            "key.goety_revenant.shen_shi", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);

    /** 灵化 —— 肉身与灵魂完全融合之后，随时把身体变成魂的形态。 */
    public static final KeyMapping LING_HUA = new KeyMapping(
            "key.goety_revenant.ling_hua", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);

    /**
     * **强化槽：切换鼠标下那片叶子的方向**（只在魂树界面里有效）。
     *
     * <p>一个一个点开界面去改太麻烦了 —— 悬停到叶子上按这个键，
     * 直接 空 → 攻 → 守 → 速 → 空 循环。</p>
     */
    public static final KeyMapping UPGRADE_CYCLE = new KeyMapping(
            "key.goety_revenant.upgrade_cycle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    /** **在那片白里开口**（落地、听完耳边那句话之后自己按一下，问答才开始）。 */
    public static final KeyMapping QUIZ_BEGIN = new KeyMapping(
            "key.goety_revenant.quiz_begin", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_ENTER, CATEGORY);

    /** **从答题空间里出来**（答完之后想走的时候按）。 */
    public static final KeyMapping QUIZ_LEAVE = new KeyMapping(
            "key.goety_revenant.quiz_leave", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, CATEGORY);

    private BodyKeybinds() {
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_BODY);
        event.register(OPEN_RADIAL);
        event.register(SOUL_OUT);
        event.register(SHEN_SHI);
        event.register(LING_HUA);
        event.register(UPGRADE_CYCLE);
        event.register(QUIZ_LEAVE);
        event.register(QUIZ_BEGIN);
    }

    /** 客户端 tick：按键打开界面（状态由服务端回包后填入）。 */
    @Mod.EventBusSubscriber(modid = com.habaiwa.goetyrevenant.GoetyRevenant.MODID, value = Dist.CLIENT)
    public static final class Handler {
        private Handler() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            // 灵魂状态的「左键附身」已经挪到 SoulClientEvents ——
            // 这里原本用 keyAttack.consumeClick()，但原版 handleKeybinds() 会先把点击吃干净，
            // 轮到 tick 事件时永远是 false，所以那个写法根本不会触发。
            while (LING_HUA.consumeClick()) {
                if (Minecraft.getInstance().player != null) {
                    RevenantNetwork.CHANNEL.sendToServer(
                            new com.habaiwa.goetyrevenant.common.network.LingHuaMessage());
                }
            }
            while (SHEN_SHI.consumeClick()) {
                if (Minecraft.getInstance().player != null) {
                    RevenantNetwork.CHANNEL.sendToServer(
                            new com.habaiwa.goetyrevenant.common.network.ShenShiMessage());
                }
            }
            while (SOUL_OUT.consumeClick()) {
                if (Minecraft.getInstance().player != null) {
                    RevenantNetwork.CHANNEL.sendToServer(
                            new com.habaiwa.goetyrevenant.common.network.SoulOutMessage());
                }
            }
            while (OPEN_RADIAL.consumeClick()) {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().setScreen(new com.habaiwa.goetyrevenant.client.screen.wheel.RadialScreen());
                }
            }
            // 在那片白里按一下才开口（不自动弹界面）
            while (QUIZ_BEGIN.consumeClick()) {
                if (Minecraft.getInstance().player != null
                        && com.habaiwa.goetyrevenant.client.quiz.QuizRoomClient.inside()) {
                    com.habaiwa.goetyrevenant.client.state.QuizClientState.begin();
                }
            }
            // 答完之后想走：按一下，白收起来，人回到原来的地方
            while (QUIZ_LEAVE.consumeClick()) {
                if (Minecraft.getInstance().player != null) {
                    RevenantNetwork.CHANNEL.sendToServer(
                            new com.habaiwa.goetyrevenant.common.network.QuizLeaveMessage());
                }
            }
            // **快捷键轮换当前槽的方向** ——
            // 用户 2026-10-10：「同样的被动效果一个个调很麻烦，
            // 增加快捷键可以切换当前序列的选项」。
            // 发 index = -1，服务端会去读"当前选中的槽"（界面里点过的那个）。
            while (UPGRADE_CYCLE.consumeClick()) {
                if (Minecraft.getInstance().player != null) {
                    RevenantNetwork.CHANNEL.sendToServer(
                            new com.habaiwa.goetyrevenant.common.network.UpgradeMessage(-1, 0));
                }
            }
            while (OPEN_BODY.consumeClick()) {
                if (Minecraft.getInstance().player == null) {
                    continue;
                }
                RevenantNetwork.CHANNEL.sendToServer(new BodyActionMessage(BodyActionMessage.OPEN, ""));
                // 嵌入界面重做成**树**（魂树）。旧的人体解剖图保留在 BodyScreen，
            // 需要时可以用命令打开，但默认入口是树。
            Minecraft.getInstance().setScreen(new com.habaiwa.goetyrevenant.client.screen.tree.TreeScreen());
            }
        }
    }
}
