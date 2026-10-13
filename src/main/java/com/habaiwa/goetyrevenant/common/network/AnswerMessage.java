package com.habaiwa.goetyrevenant.common.network;

import com.habaiwa.goetyrevenant.common.sequence.Sequence;
import com.habaiwa.goetyrevenant.common.sequence.SevenQuestions;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** C2S：回答七问答里的当前这一题（choice = 选项序号，-1 = 放弃/跳过）。 */
public class AnswerMessage {
    private final int choice;

    public AnswerMessage(int choice) {
        this.choice = choice;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.choice);
    }

    public static AnswerMessage decode(FriendlyByteBuf buf) {
        return new AnswerMessage(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || this.choice < 0) {
                return;
            }
            Sequence matched = SevenQuestions.answer(player, this.choice);
            // **注意顺序**：状态要等"觉醒"写进玩家数据之后再发 ——
            // 早发一步，客户端收到的序列就是空的，于是既不会弹序列名，
            // 也不会自动收起答题屏（得手动按 ESC）。
            if (matched == null) {
                // 还没答完：只同步进度，界面据此翻到下一题
                RevenantNetwork.sendQuizState(player);
                return;
            }
            {
                // **觉醒**：锁定序列 + 树睁开眼 + 烛火值从觉醒阶段起
                Sequence.awaken(player, matched);
                com.habaiwa.goetyrevenant.common.body.BodyModData.setUnlocked(player, true);
                // **觉醒即开窍** —— 开窍魔药那套已经取消，身体是那七问答改造的，
                // 六窍直接全开。少了这一步，嵌入位永远是锁的（点了没反应）。
                for (com.habaiwa.goetyrevenant.common.body.OrganType organ
                        : com.habaiwa.goetyrevenant.common.body.OrganType.values()) {
                    com.habaiwa.goetyrevenant.common.body.BodyModData.setOpened(player, organ, true);
                }
                com.habaiwa.goetyrevenant.common.sequence.CandleValue.setStage(player, 0);
                com.habaiwa.goetyrevenant.common.sequence.CandleValue.holdAtMax(player);
                // **先让觉醒演完，再送回井边** —— 直接传送会把觉醒那一刻盖掉，
                // 玩家会觉得"答完了什么也没发生"。倒计时走完由 QuizRoom.tick 送走。
                player.getPersistentData().putInt("GRAwakenShow", 76);
                RevenantNetwork.sendBodyState(player);
                // **不往聊天栏写"觉醒了什么"** —— 那一刻由屏幕中央的框来说，
                // 聊天栏再重复一遍就把仪式感冲淡了。
            }
        });
        context.setPacketHandled(true);
    }
}
