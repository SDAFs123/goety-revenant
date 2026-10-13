package com.habaiwa.goetyrevenant.common.network;

import com.habaiwa.goetyrevenant.GoetyRevenant;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** 网络通道：目前只用来把「附身了谁」同步给客户端做皮肤渲染。 */
public final class RevenantNetwork {
    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(GoetyRevenant.MODID, "main"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(VERSION::equals)
            .serverAcceptedVersions(VERSION::equals)
            .simpleChannel();

    private RevenantNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(SkinOverrideMessage.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkinOverrideMessage::encode)
                .decoder(SkinOverrideMessage::decode)
                .consumerMainThread(SkinOverrideMessage::handle)
                .add();
        CHANNEL.messageBuilder(BodyStateMessage.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BodyStateMessage::encode)
                .decoder(BodyStateMessage::decode)
                .consumerMainThread(BodyStateMessage::handle)
                .add();
        CHANNEL.messageBuilder(BodyActionMessage.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BodyActionMessage::encode)
                .decoder(BodyActionMessage::decode)
                .consumerMainThread(BodyActionMessage::handle)
                .add();
        // id 3 曾经是 OpenBodyMessage（打开旧的七窍"人体图"界面）——
        // 七窍已被魂树取代，人体图界面连同它一起删了。id 号不补。
        CHANNEL.messageBuilder(CastEmbeddedMessage.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CastEmbeddedMessage::encode)
                .decoder(CastEmbeddedMessage::decode)
                .consumerMainThread(CastEmbeddedMessage::handle)
                .add();
        CHANNEL.messageBuilder(ConverterSyncMessage.class, 5, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ConverterSyncMessage::encode)
                .decoder(ConverterSyncMessage::decode)
                .consumerMainThread(ConverterSyncMessage::handle)
                .add();
        CHANNEL.messageBuilder(ConverterSetMessage.class, 6, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ConverterSetMessage::encode)
                .decoder(ConverterSetMessage::decode)
                .consumerMainThread(ConverterSetMessage::handle)
                .add();
        CHANNEL.messageBuilder(PossessRequestMessage.class, 7, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PossessRequestMessage::encode)
                .decoder(PossessRequestMessage::decode)
                .consumerMainThread(PossessRequestMessage::handle)
                .add();
        CHANNEL.messageBuilder(SoulSyncMessage.class, 8, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SoulSyncMessage::encode)
                .decoder(SoulSyncMessage::decode)
                .consumerMainThread(SoulSyncMessage::handle)
                .add();
        // id 9 曾经是 SoulAppearanceMessage（"灵魂半透明可见"）——
        // 方向是反的（灵魂应当**完全看不见**，见 SoulForm 的注释），从未启用，已删除。
        // id 号不补，留空。
        CHANNEL.messageBuilder(ConverterQueryMessage.class, 10, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ConverterQueryMessage::encode)
                .decoder(ConverterQueryMessage::decode)
                .consumerMainThread(ConverterQueryMessage::handle)
                .add();
        CHANNEL.messageBuilder(LingHuaMessage.class, 14, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LingHuaMessage::encode)
                .decoder(LingHuaMessage::decode)
                .consumerMainThread(LingHuaMessage::handle)
                .add();
        CHANNEL.messageBuilder(ShenShiSyncMessage.class, 13, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ShenShiSyncMessage::encode)
                .decoder(ShenShiSyncMessage::decode)
                .consumerMainThread(ShenShiSyncMessage::handle)
                .add();
        CHANNEL.messageBuilder(ShenShiMessage.class, 12, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ShenShiMessage::encode)
                .decoder(ShenShiMessage::decode)
                .consumerMainThread(ShenShiMessage::handle)
                .add();
        CHANNEL.messageBuilder(QuizLeaveMessage.class, 20, NetworkDirection.PLAY_TO_SERVER)
                .encoder(QuizLeaveMessage::encode)
                .decoder(QuizLeaveMessage::decode)
                .consumerMainThread(QuizLeaveMessage::handle)
                .add();
        CHANNEL.messageBuilder(PlaySoundMessage.class, 21, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlaySoundMessage::encode)
                .decoder(PlaySoundMessage::decode)
                .consumerMainThread(PlaySoundMessage::handle)
                .add();
        CHANNEL.messageBuilder(QuizRoomMessage.class, 19, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(QuizRoomMessage::encode)
                .decoder(QuizRoomMessage::decode)
                .consumerMainThread(QuizRoomMessage::handle)
                .add();
        CHANNEL.messageBuilder(AnswerMessage.class, 17, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AnswerMessage::encode)
                .decoder(AnswerMessage::decode)
                .consumerMainThread(AnswerMessage::handle)
                .add();
        CHANNEL.messageBuilder(QuizStateMessage.class, 18, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(QuizStateMessage::encode)
                .decoder(QuizStateMessage::decode)
                .consumerMainThread(QuizStateMessage::handle)
                .add();
        CHANNEL.messageBuilder(UpgradeMessage.class, 16, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UpgradeMessage::encode)
                .decoder(UpgradeMessage::decode)
                .consumerMainThread(UpgradeMessage::handle)
                .add();
        CHANNEL.messageBuilder(PossessAppearanceMessage.class, 15, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PossessAppearanceMessage::encode)
                .decoder(PossessAppearanceMessage::decode)
                .consumerMainThread(PossessAppearanceMessage::handle)
                .add();
        CHANNEL.messageBuilder(SoulOutMessage.class, 11, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SoulOutMessage::encode)
                .decoder(SoulOutMessage::decode)
                .consumerMainThread(SoulOutMessage::handle)
                .add();
    }

    /** **叫某个客户端放一个音效**（按 id，客户端自己去 sounds.json 里找）。 */
    public static void playSound(ServerPlayer player, String id, float volume, float pitch) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PlaySoundMessage(id, volume, pitch));
    }

    /** 告诉客户端"人进 / 出答题空间了" —— 客户端据此把白铺开或收起来。 */
    public static void sendQuizRoom(ServerPlayer player, int phase, boolean inside) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new QuizRoomMessage(phase, inside));
    }

    /** 把七问答的进度发回给本人（界面据此翻页）。 */
    public static void sendQuizState(ServerPlayer player) {
        com.habaiwa.goetyrevenant.common.sequence.Sequence seq =
                com.habaiwa.goetyrevenant.common.sequence.Sequence.of(player);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new QuizStateMessage(
                com.habaiwa.goetyrevenant.common.sequence.SevenQuestions.progress(player),
                com.habaiwa.goetyrevenant.common.sequence.SevenQuestions.running(player),
                seq == null ? "" : seq.cn()));
    }

    /** 广播「某玩家附身了生物」—— 别人看他是那个生物（空串 = 取消外观）。 */
    public static void broadcastPossessAppearance(ServerPlayer player, String entityTypeId) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new PossessAppearanceMessage(player.getUUID(), entityTypeId));
    }

    /** 同上，但把生物自己的存档数据一起带上（变体：颜色 / 花纹 / 大小 / 职业）。 */
    public static void broadcastPossessAppearance(ServerPlayer player, String entityTypeId,
                                                  net.minecraft.nbt.CompoundTag data) {
        CHANNEL.send(PacketDistributor.ALL.noArg(),
                new PossessAppearanceMessage(player.getUUID(), entityTypeId, data));
    }

    /**
     * 把「回魂灵魂状态」同步给客户端。
     * 客户端靠它区分**我们的灵魂**和**原版旁观者**，并驱动寻尸高亮/灵魂 HUD。
     */
    public static void sendSoulState(ServerPlayer player) {
        boolean soul = com.habaiwa.goetyrevenant.common.soul.SoulData.isSoul(player);
        // 身体的位置只对灵魂算 —— 不是灵魂就发「没有」，省掉一次实体扫描
        int distance = -1;
        float bearing = Float.NaN;
        if (soul) {
            com.habaiwa.goetyrevenant.common.entity.CorpseEntity corpse =
                    com.habaiwa.goetyrevenant.common.possession.Possession.findCorpse(player);
            if (corpse != null) {
                distance = com.habaiwa.goetyrevenant.common.soul.Guidance.distance(player, corpse);
                // 不同维度就没有「方向」可言，发 NaN 让客户端画成「另一个世界」
                if (!com.habaiwa.goetyrevenant.common.soul.Guidance.otherDimension(player, corpse)) {
                    bearing = com.habaiwa.goetyrevenant.common.soul.Guidance.bearing(player, corpse);
                }
            }
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SoulSyncMessage(
                soul,
                com.habaiwa.goetyrevenant.common.soul.SoulData.getSelfAttunement(player),
                com.habaiwa.goetyrevenant.common.soul.SoulData.getEnergy(player),
                com.habaiwa.goetyrevenant.common.soul.SoulData.getTicks(player),
                com.habaiwa.goetyrevenant.common.soul.SoulData.getPossessTicks(player),
                distance, bearing,
                com.habaiwa.goetyrevenant.common.soul.SoulEnergy.max(player),
                com.habaiwa.goetyrevenant.common.soul.SoulData.isDrifting(player)));
    }

    /** 把转化台配置推给客户端（同时也用来打开界面）。 */
    public static void sendConverterSync(ServerPlayer player, net.minecraft.core.BlockPos pos,
                                         com.habaiwa.goetyrevenant.common.block.SpiritConverterBlockEntity converter) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ConverterSyncMessage(pos, converter.getSelectedMask(),
                        converter.cageSouls(), converter.getProgress()));
    }

    /** 把改造状态整份推给该玩家的客户端。 */
    public static void sendBodyState(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), BodyStateMessage.of(player));
    }

    /** 告诉客户端：这名玩家现在顶的是谁的皮（null = 恢复自己）。 */
    public static void sendSkin(ServerPlayer player, String value, String signature) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SkinOverrideMessage(player.getUUID(), value, signature));
    }
}
