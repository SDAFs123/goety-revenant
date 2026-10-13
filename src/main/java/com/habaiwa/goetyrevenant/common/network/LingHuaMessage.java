package com.habaiwa.goetyrevenant.common.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** C2S：**灵化开关**（肉身与灵魂完全融合之后的能力）。 */
public class LingHuaMessage {
    public LingHuaMessage() {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public static LingHuaMessage decode(FriendlyByteBuf buf) {
        return new LingHuaMessage();
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                com.habaiwa.goetyrevenant.common.soul.LingHua.toggle(player);
            }
        });
        context.setPacketHandled(true);
    }
}
