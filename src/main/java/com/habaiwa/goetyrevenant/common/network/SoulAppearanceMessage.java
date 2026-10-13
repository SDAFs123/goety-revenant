package com.habaiwa.goetyrevenant.common.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * S2C：把「某名玩家进入了/退出了灵魂状态」广播给**所有人**。
 *
 * <p>与 {@link SoulSyncMessage} 不同 —— 那个只发给自己（驱动自己的 HUD）；
 * 这个是给**别人**看的（渲染灵魂外观：半透明）。</p>
 */
public class SoulAppearanceMessage {
    private final UUID player;
    private final boolean soul;

    public SoulAppearanceMessage(UUID player, boolean soul) {
        this.player = player;
        this.soul = soul;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.player);
        buf.writeBoolean(this.soul);
    }

    public static SoulAppearanceMessage decode(FriendlyByteBuf buf) {
        return new SoulAppearanceMessage(buf.readUUID(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.habaiwa.goetyrevenant.client.ClientSoulRegistry.set(this.player, this.soul)));
        context.setPacketHandled(true);
    }
}
