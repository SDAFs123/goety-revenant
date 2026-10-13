package com.habaiwa.goetyrevenant.common.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * S2C：把「这名玩家现在顶的是谁的皮」发给客户端。
 *
 * <p><b>必须连皮肤属性（base64 的 value / signature）一起发。</b>
 * 只发 UUID 的话客户端查不到 `textures` 属性，只会回退成默认皮肤 ——
 * 这就是之前「附身别人但皮肤没变」的原因。</p>
 */
public class SkinOverrideMessage {
    private final UUID player;
    private final String value;
    private final String signature;

    public SkinOverrideMessage(UUID player, String value, String signature) {
        this.player = player;
        this.value = value == null ? "" : value;
        this.signature = signature == null ? "" : signature;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.player);
        buf.writeUtf(this.value);
        buf.writeUtf(this.signature);
    }

    public static SkinOverrideMessage decode(FriendlyByteBuf buf) {
        return new SkinOverrideMessage(buf.readUUID(), buf.readUtf(), buf.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.habaiwa.goetyrevenant.client.state.ClientSkinOverride.set(
                        this.player, this.value, this.signature)));
        context.setPacketHandled(true);
    }
}
