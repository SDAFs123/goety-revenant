package com.habaiwa.goetyrevenant.common.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** C2S：从答题空间里出来。 */
public class QuizLeaveMessage {
    public QuizLeaveMessage() {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public static QuizLeaveMessage decode(FriendlyByteBuf buf) {
        return new QuizLeaveMessage();
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                com.habaiwa.goetyrevenant.common.sequence.QuizRoom.beginLeave(player);
            }
        });
        context.setPacketHandled(true);
    }
}
