package com.habaiwa.goetyrevenant.common.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * S2C：七问答的状态。
 *
 * <p>题目本身是静态的（客户端直接读 {@code SevenQuestions}），这里只发
 * 「问到第几题」和「被谁看中了」—— 界面据此翻页。</p>
 */
public class QuizStateMessage {
    private final int index;
    private final boolean running;
    private final String sequence;

    public QuizStateMessage(int index, boolean running, String sequence) {
        this.index = index;
        this.running = running;
        this.sequence = sequence == null ? "" : sequence;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.index);
        buf.writeBoolean(this.running);
        buf.writeUtf(this.sequence);
    }

    public static QuizStateMessage decode(FriendlyByteBuf buf) {
        return new QuizStateMessage(buf.readVarInt(), buf.readBoolean(), buf.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.habaiwa.goetyrevenant.client.QuizClientState.accept(
                        this.index, this.running, this.sequence)));
        context.setPacketHandled(true);
    }
}
