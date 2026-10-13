package com.habaiwa.goetyrevenant.common.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * S2C：**叫客户端放一个音效**。
 *
 * <h3>为什么不直接让服务端放</h3>
 * <p>{@code assets/} 里的 {@code sounds.json} 是<b>客户端资源</b> ——
 * 它加载出来的音效只进<b>客户端</b>的声音注册表。服务端根本不读 {@code assets/}，
 * 所以在服务端查 {@code goety_revenant:whisper_sequence} 永远是"没有这个音效"。</p>
 *
 * <p>于是：命令/服务端只负责把 id 发过去，真正"找音效、放音效"这件事交给客户端做 ——
 * 那边才有这份清单。</p>
 */
public class PlaySoundMessage {
    private final String id;
    private final float volume;
    private final float pitch;

    public PlaySoundMessage(String id, float volume, float pitch) {
        this.id = id == null ? "" : id;
        this.volume = volume;
        this.pitch = pitch;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.id);
        buf.writeFloat(this.volume);
        buf.writeFloat(this.pitch);
    }

    public static PlaySoundMessage decode(FriendlyByteBuf buf) {
        return new PlaySoundMessage(buf.readUtf(), buf.readFloat(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> {
                    ResourceLocation loc = ResourceLocation.tryParse(this.id);
                    if (loc == null) {
                        return;
                    }
                    // 不查注册表 —— 直接按 id 造一个，客户端的声音系统会去 sounds.json 里找
                    net.minecraft.sounds.SoundEvent event =
                            net.minecraft.sounds.SoundEvent.createVariableRangeEvent(loc);
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    net.minecraft.client.resources.sounds.SimpleSoundInstance inst =
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    event, this.pitch, this.volume);
                    mc.getSoundManager().play(inst);
                    // **把结果说给玩家听** —— 这一步是为了分得清三种情况：
                    //   "播放中: true"   → 音效引擎收下了，问题在音量/设备
                    //   "播放中: false"  → 收下了但没进播放队列（资源/编码问题）
                    //   上面那句"找不到这个音效" → sounds.json 没被加载
                    if (mc.player != null) {
                        boolean active = mc.getSoundManager().isActive(inst);
                        mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                "[回魂] " + loc + " · 音高 " + this.pitch + " / 音量 " + this.volume
                                        + " · 播放中: " + active), false);
                    }
                }));
        context.setPacketHandled(true);
    }
}
