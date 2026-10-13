package com.habaiwa.goetyrevenant.common.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * C2S：**主动灵魂出窍**。
 *
 * <p>去过哭泣之井（= 解锁了改造身体）之后就能随时出窍，**不需要任何物品**。
 * 服务端会再验一次解锁状态，客户端说啥不算。</p>
 */
public class SoulOutMessage {
    public SoulOutMessage() {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public static SoulOutMessage decode(FriendlyByteBuf buf) {
        return new SoulOutMessage();
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (!com.habaiwa.goetyrevenant.common.body.BodyModData.isUnlocked(player)) {
                player.displayClientMessage(Component.translatable("message.goety_revenant.body_locked"), true);
                return;
            }
            com.habaiwa.goetyrevenant.common.possession.SoulForm.voluntarySoulOut(player);
        });
        context.setPacketHandled(true);
    }
}
