package com.habaiwa.goetyrevenant.common.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * S2C：答题空间的**转场**。
 *
 * <pre>
 *   phase 0  开始白（客户端把白涨满）
 *   phase 1  已经到位（白退去）—— 传送就发生在 0 和 1 之间那一下
 * </pre>
 *
 * <p>所以传送发生在**玩家什么都看不见的时候**：进和出都是"白先糊上来、
 * 白底下的世界换掉、白再退" —— 无缝。</p>
 */
public class QuizRoomMessage {
    private final int phase;
    private final boolean inside;

    public QuizRoomMessage(int phase, boolean inside) {
        this.phase = phase;
        this.inside = inside;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.phase);
        buf.writeBoolean(this.inside);
    }

    public static QuizRoomMessage decode(FriendlyByteBuf buf) {
        return new QuizRoomMessage(buf.readVarInt(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.habaiwa.goetyrevenant.client.quiz.QuizRoomClient.accept(
                        this.phase, this.inside)));
        context.setPacketHandled(true);
    }
}
