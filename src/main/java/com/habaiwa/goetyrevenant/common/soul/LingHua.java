package com.habaiwa.goetyrevenant.common.soul;

import com.habaiwa.goetyrevenant.common.network.RevenantNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * **灵化** —— 肉身与灵魂完全融合之后得到的能力。
 *
 * <h3>它是什么</h3>
 * <p>不是「灵魂出窍」，也不是死。是**魂魄已经和肉身长在一起了**，
 * 于是肉身可以随时呈现出灵魂的性质：</p>
 * <ul>
 *   <li><b>穿墙</b> —— `noPhysics`</li>
 *   <li><b>飞行</b> —— `mayfly`</li>
 *   <li><b>不可被观测</b> —— 隐身 + 不参与索敌（连「能看见灵魂」的生物也看不见）</li>
 * </ul>
 *
 * <h3>解锁条件：肉身与灵魂完全融合</h3>
 * <p>两条都要：</p>
 * <ol>
 *   <li><b>自身契合度满 100%</b> —— 魂魄和这具身体不再互相排斥</li>
 *   <li><b>灵魂游离过并成功回归过至少一次</b> —— 得先分开过，才谈得上"融合"</li>
 * </ol>
 *
 * <h3>和灵魂游离的区别</h3>
 * <table border="1">
 *   <tr><th></th><th>灵魂游离</th><th>灵化</th></tr>
 *   <tr><td>身体</td><td>躺在原地变成尸体</td><td><b>还在身上</b>，只是呈现魂的性质</td></tr>
 *   <tr><td>限时</td><td>20 分钟</td><td><b>不限时</b>，随时开关</td></tr>
 *   <tr><td>被看见</td><td>「能察觉灵魂的生物」看得见</td><td><b>谁也看不见</b></td></tr>
 * </table>
 */
public final class LingHua {
    private LingHua() {
    }

    /** 已经解锁了吗。 */
    public static boolean isUnlocked(ServerPlayer player) {
        return player.getPersistentData().getBoolean(SoulConstants.KEY_LINGHUA_UNLOCKED);
    }

    /** 现在开着吗。 */
    public static boolean isActive(ServerPlayer player) {
        return player.getPersistentData().getBoolean(SoulConstants.KEY_LINGHUA_ACTIVE);
    }

    /** 灵魂成功回归过几次。 */
    public static int soulWalks(ServerPlayer player) {
        return player.getPersistentData().getInt(SoulConstants.KEY_SOUL_WALKS);
    }

    /** 灵魂成功回归时调一次。 */
    public static void recordSoulWalk(ServerPlayer player) {
        player.getPersistentData().putInt(SoulConstants.KEY_SOUL_WALKS, soulWalks(player) + 1);
        // 顺手检查一次能不能解锁 —— 回归的那一刻正是"融合"的瞬间
        tryUnlock(player, true);
    }

    /** 肉身与灵魂是否已经完全融合。 */
    public static boolean isFused(ServerPlayer player) {
        return SoulData.getSelfAttunement(player) >= 1.0F && soulWalks(player) > 0;
    }

    /** 检查并解锁（{@code announce} = 要不要提示）。 */
    public static boolean tryUnlock(ServerPlayer player, boolean announce) {
        if (isUnlocked(player) || !isFused(player)) {
            return false;
        }
        player.getPersistentData().putBoolean(SoulConstants.KEY_LINGHUA_UNLOCKED, true);
        if (announce) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.linghua_unlocked",
                    com.habaiwa.goetyrevenant.common.util.KeyNames.key("key.goety_revenant.ling_hua")), false);
        }
        return true;
    }

    /** 开关灵化。 */
    public static void toggle(ServerPlayer player) {
        // 灵化只有进入灵体的时候才有 —— 肉身和灵魂离了体，这身体才有魂的性质
        if (!SoulData.isSoul(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.goety_revenant.linghua_soul_only"), true);
            return;
        }
        if (!isUnlocked(player)) {
            player.displayClientMessage(Component.translatable(
                    isFused(player)
                            ? "message.goety_revenant.linghua_ready"
                            : "message.goety_revenant.linghua_locked",
                    com.habaiwa.goetyrevenant.common.util.KeyNames.key("key.goety_revenant.ling_hua")), true);
            return;
        }
        setActive(player, !isActive(player));
    }

    /** 设置灵化状态（会立刻改变身体的性质）。 */
    public static void setActive(ServerPlayer player, boolean active) {
        if (SoulData.isSoul(player)) {
            // 灵魂游离的时候谈不上"灵化" —— 那本来就是魂
            player.displayClientMessage(Component.translatable("message.goety_revenant.linghua_in_soul"), true);
            return;
        }
        player.getPersistentData().putBoolean(SoulConstants.KEY_LINGHUA_ACTIVE, active);
        apply(player, active);
        // 注意：灵化**不是**灵魂 —— 不能广播"灵魂外观"。
        // 之前广播了，客户端会把活人当灵魂渲染（半透明 + 不写深度），
        // 于是灵化的人走到方块边就被方块"吃掉"。
        // 隐身交给 apply() 里的 setInvisible；HUD 同步保留。
        RevenantNetwork.sendSoulState(player);
        player.displayClientMessage(Component.translatable(active
                ? "message.goety_revenant.linghua_on"
                : "message.goety_revenant.linghua_off"), true);
    }

    /** 把灵化的性质作用到（或撤销出）身上。 */
    private static void apply(ServerPlayer player, boolean active) {
        if (active) {
            player.noPhysics = true;
            player.setInvisible(true);
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
        } else {
            player.noPhysics = false;
            player.setInvisible(false);
            if (!player.isCreative() && !player.isSpectator()) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
            }
        }
        player.onUpdateAbilities();
    }

    /** 每 tick 兜底：状态掉了就补回来（别的模组可能重置 abilities）。 */
    public static void tick(ServerPlayer player) {
        if (!isActive(player)) {
            return;
        }
        if (player.isSpectator() || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            return;
        }
        if (!player.noPhysics || !player.isInvisible()) {
            apply(player, true);
        }
    }

    /** 退出灵化（掉线 / 变灵魂 / 死亡时清掉，免得状态残留）。 */
    public static void forceOff(ServerPlayer player) {
        if (isActive(player)) {
            setActive(player, false);
        }
    }
}
