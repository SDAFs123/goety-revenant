package com.habaiwa.goetyrevenant.common.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * S2C：**神识感知到的目标快照**。
 *
 * <p>神识是感知，所以画面全在客户端画 —— 服务端只负责「感知到什么」这件事，
 * 把坐标和类型发过去就行。</p>
 */
public class ShenShiSyncMessage {
    /** 感知到的类型（客户端按这个决定颜色和画法）。 */
    public static final byte KIND_LIVING = 0;
    public static final byte KIND_PLAYER = 1;
    public static final byte KIND_CORPSE = 2;
    /** 自己的躯壳 —— 回魂最想知道的东西，客户端会特别标出来。 */
    public static final byte KIND_OWN_CORPSE = 3;
    /** 掉在地上的聚晶。 */
    public static final byte KIND_FOCUS = 4;

    /** 一个被感知到的目标。 */
    public record Contact(double x, double y, double z, byte kind) {
    }

    private final Contact[] contacts;
    /** 这一次是不是「主动扫视」（触发波纹 / 照亮）。 */
    private final boolean pulse;
    /** 神识半径（客户端画雷达环用）。 */
    private final double radius;

    public ShenShiSyncMessage(Contact[] contacts, boolean pulse, double radius) {
        this.contacts = contacts;
        this.pulse = pulse;
        this.radius = radius;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.pulse);
        buf.writeDouble(this.radius);
        buf.writeVarInt(this.contacts.length);
        for (Contact contact : this.contacts) {
            buf.writeDouble(contact.x());
            buf.writeDouble(contact.y());
            buf.writeDouble(contact.z());
            buf.writeByte(contact.kind());
        }
    }

    public static ShenShiSyncMessage decode(FriendlyByteBuf buf) {
        boolean pulse = buf.readBoolean();
        double radius = buf.readDouble();
        int size = buf.readVarInt();
        List<Contact> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new Contact(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readByte()));
        }
        return new ShenShiSyncMessage(list.toArray(new Contact[0]), pulse, radius);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.habaiwa.goetyrevenant.client.state.ClientShenShi.accept(
                        this.contacts, this.pulse, this.radius)));
        context.setPacketHandled(true);
    }
}
