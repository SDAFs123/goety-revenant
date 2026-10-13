package com.habaiwa.goetyrevenant.common.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** S2C：命令式打开改造身体界面（不用玩家自己按键）。 */
public class OpenBodyMessage {
    public OpenBodyMessage() {
    }

    public void encode(FriendlyByteBuf buf) {
        // 无载荷
    }

    public static OpenBodyMessage decode(FriendlyByteBuf buf) {
        return new OpenBodyMessage();
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.habaiwa.goetyrevenant.client.BodyClientState.openScreen()));
        context.setPacketHandled(true);
    }
}
