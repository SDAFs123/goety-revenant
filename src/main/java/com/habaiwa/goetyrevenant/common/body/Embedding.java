package com.habaiwa.goetyrevenant.common.body;

import com.Polarice3.Goety.common.capabilities.soulenergy.ISoulEnergy;
import com.Polarice3.Goety.utils.SEHelper;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;

/**
 * 把聚晶嵌进身体。
 *
 * <p><b>两条路</b>（开窍是前提，见 {@link ElementOverload}）：</p>
 *
 * <h3>1. 首次嵌入 —— 3 秒仪式（扣血 + 极速抽灵魂力）</h3>
 * <p>这个窍第一次真正刻进一枚聚晶时：</p>
 * <ol>
 *   <li>极速抽干灵魂力（{@value #SE_DRAIN_PER_TICK}/tick）</li>
 *   <li><b>灵魂护盾先替你扛</b>，扛不住了才扣血</li>
 *   <li>血一路扣到只剩 {@value #HEALTH_FLOOR} 点，撑满 {@value #DURATION} tick 才算刻好</li>
 * </ol>
 * <p>撑不住（灵魂力先见底）→ 中断，这次白费，但**聚晶不会被吞**。</p>
 *
 * <h3>2. 之后换聚晶 —— 只花 {@value #EMBED_COST} 灵魂能量</h3>
 * <p>仪式走过一次就够了，之后同一个窍换聚晶是瞬间完成的。</p>
 *
 * <h3>聚晶只会在**成功那一刻**被拿走</h3>
 * <p>整个流程里都只记「哪一格」（{@link FocusSlots} 槽位编码），**从不提前从背包里拿东西**。
 * 只有走到 {@link #finish} 并且**那一格里还是同一枚聚晶**时，才真正 `take` 掉它。
 * 所以：失败不吞聚晶、玩家中途挪走也吞不掉、服务端崩了也不丢东西。</p>
 */
public final class Embedding {
    private static final String KEY_ORGAN = "GREmbedOrgan";
    private static final String KEY_TICKS = "GREmbedTicks";
    private static final String KEY_STACK = "GREmbedStack";
    private static final String KEY_SLOT = "GREmbedSlot";

    /** 首次仪式时长：3 秒。 */
    /** 嵌入时长（tick）—— 读配置。 */
    public static int duration() {
        return Math.max(0, com.habaiwa.goetyrevenant.common.config.RevenantConfig.embedSeconds) * 20;
    }

    /** 每 tick 抽走的灵魂能量 —— 读配置。 */
    public static int drainPerTick() {
        return Math.max(0, com.habaiwa.goetyrevenant.common.config.RevenantConfig.embedDrainPerTick);
    }

    /** 血量下限 —— 读配置。 */
    public static float healthFloor() {
        return Math.max(0.5F, com.habaiwa.goetyrevenant.common.config.RevenantConfig.embedHealthFloor);
    }

    /** 换聚晶（已开窍）的消耗 —— 读配置。 */
    public static int embedCost() {
        return Math.max(0, com.habaiwa.goetyrevenant.common.config.RevenantConfig.embedCost);
    }

    private static final int DURATION = 60;
    /** 仪式每 tick 抽的灵魂力。 */
    public static final int SE_DRAIN_PER_TICK = 120;
    /** 仪式期间的血量下限。 */
    public static final float HEALTH_FLOOR = 1.0F;
    /** 换聚晶（仪式已走过）要花的灵魂能量。 */
    public static final int EMBED_COST = 2000;

    private Embedding() {
    }

    /** 首次仪式总共要烧掉多少灵魂力。 */
    public static int firstEmbedCost() {
        return drainPerTick() * duration();
    }

    /** 这个窍这次嵌入要花多少灵魂能量（界面显示用）。 */
    public static int costFor(boolean opened) {
        return opened ? embedCost() : firstEmbedCost();
    }

    // ===== 流程控制 =====

    public static boolean isRunning(ServerPlayer player) {
        return player.getPersistentData().contains(KEY_ORGAN);
    }

    /** 正在嵌入的是哪个窍（没在跑就返回 null）。 */
    public static OrganType runningOrgan(ServerPlayer player) {
        return isRunning(player) ? organOf(player.getPersistentData().getString(KEY_ORGAN)) : null;
    }

    /**
     * 开始嵌入。返回是否成功启动。
     *
     * @param focus 聚晶的拷贝（真正扣除在 {@link #finish} / 快路径里按 `slot` 重新取）
     * @param slot  {@link FocusSlots} 槽位编码，-1 = 不从背包扣（调试用）
     */
    public static boolean start(ServerPlayer player, OrganType organ, ItemStack focus, int slot) {
        if (isRunning(player)) {
            return false;
        }
        if (!BodyModData.isSlotUnlocked(player, organ)) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.embed_slot_locked",
                    organ.displayName()), true);
            return false;
        }

        if (BodyModData.isOpened(player, organ)) {
            // 仪式走过一次了：只花灵魂能量，瞬间完成。
            //
            // 顺序很关键：**先核验聚晶还在不在，再扣灵魂能量**。
            // 反过来的话，玩家中途把聚晶挪走就会被白扣 2000 灵魂能量 ——
            // 提示还写着「聚晶没被消耗」，玩家会以为没损失。
            if (!focusStillThere(player, focus, slot)) {
                player.displayClientMessage(Component.translatable("message.goety_revenant.embed_focus_missing",
                        organ.displayName()), false);
                return false;
            }
            if (!SEHelper.decreaseSESouls(player, embedCost())) {
                player.displayClientMessage(Component.translatable("message.goety_revenant.embed_failed"), true);
                return false;
            }
            return place(player, organ, focus, slot);
        }

        // 首次：走 3 秒仪式。这里只**记下**是哪一格，不碰背包
        CompoundTag tag = player.getPersistentData();
        tag.putString(KEY_ORGAN, organ.getId());
        tag.put(KEY_STACK, focus.save(new CompoundTag()));
        tag.putInt(KEY_SLOT, slot);
        tag.putInt(KEY_TICKS, DURATION);
        player.displayClientMessage(Component.translatable("message.goety_revenant.embed_start",
                organ.displayName()), false);
        return true;
    }

    /** 每 tick 推进一次仪式。 */
    public static void tick(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        OrganType organ = organOf(tag.getString(KEY_ORGAN));
        if (organ == null) {
            tag.remove(KEY_ORGAN);
            return;
        }
        ItemStack focus = tag.contains(KEY_STACK) ? ItemStack.of(tag.getCompound(KEY_STACK)) : ItemStack.EMPTY;
        int slot = tag.contains(KEY_SLOT) ? tag.getInt(KEY_SLOT) : FocusSlots.NONE;

        // 1) 极速抽灵魂力
        if (!SEHelper.decreaseSESouls(player, drainPerTick())) {
            abort(player);
            player.displayClientMessage(Component.translatable("message.goety_revenant.embed_failed"), false);
            return;
        }

        // 2) 灵魂护盾先扛
        ISoulEnergy energy = SEHelper.getCapability(player);
        if (energy != null && energy.wardingLeft() > 0) {
            energy.setWarding(Math.max(0, energy.wardingLeft() - 4));
        } else {
            // 3) 护盾破了才扣血，一路扣到只剩 1 点
            player.setHealth(Math.max(healthFloor(), player.getHealth() - 1.0F));
        }

        int left = tag.getInt(KEY_TICKS) - 1;
        tag.putInt(KEY_TICKS, left);
        if (left <= 0) {
            if (place(player, organ, focus, slot)) {
                BodyModData.setOpened(player, organ, true);
                player.displayClientMessage(Component.translatable("message.goety_revenant.embed_done",
                        organ.displayName()), false);
            }
            abort(player);
        }
    }

    /**
     * 真正把聚晶装上去。
     *
     * <p>走到这里说明**前面该验的、该扣的都已经完成**，最后一步才是动背包：
     * 再确认那一格里确实还是同一枚聚晶，是的话才拿走。不是就不装、也不再扣任何东西。</p>
     *
     * @return 是否真的装上了
     */
    private static boolean place(ServerPlayer player, OrganType organ, ItemStack focus, int slot) {
        if (slot != FocusSlots.NONE) {
            if (!focusStillThere(player, focus, slot)) {
                player.displayClientMessage(Component.translatable("message.goety_revenant.embed_focus_missing",
                        organ.displayName()), false);
                return false;
            }
            FocusSlots.take(player, slot);
        }
        BodyModData.setFocus(player, organ, focus);
        applyAll(player);
        return true;
    }

    /** 那一格里是不是还是同一枚聚晶（slot = NONE 表示调试用，直接算通过）。 */
    private static boolean focusStillThere(ServerPlayer player, ItemStack focus, int slot) {
        if (slot == FocusSlots.NONE) {
            return true;
        }
        ItemStack current = FocusSlots.peek(player, slot);
        return !current.isEmpty() && ItemStack.isSameItemSameTags(current, focus);
    }

    private static void abort(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        tag.remove(KEY_ORGAN);
        tag.remove(KEY_TICKS);
        tag.remove(KEY_STACK);
        tag.remove(KEY_SLOT);
    }

    private static OrganType organOf(String id) {
        for (OrganType organ : OrganType.values()) {
            if (organ.getId().equals(id)) {
                return organ;
            }
        }
        return null;
    }

    // ===== 被动加成 =====

    /**
     * 把全部嵌入式聚晶的被动重新算一遍（换聚晶 / 上线 / 复活时调）。
     *
     * <p>一枚聚晶现在会给**两条**被动（元素扭曲后的主属性 + 聚晶本职的次级），
     * 所以这里对每个方向都查一遍 {@link FocusPassive#grantedAt}。</p>
     */
    public static void applyAll(ServerPlayer player) {
        Map<OrganType, ItemStack> embedded = BodyModData.allFocuses(player);
        for (OrganType organ : OrganType.values()) {
            ItemStack stack = embedded.get(organ);
            for (FocusPassive.Category category : FocusPassive.Category.values()) {
                AttributeInstance instance = player.getAttribute(category.attribute());
                if (instance == null) {
                    continue;
                }
                instance.removeModifier(FocusPassive.modifierId(organ, category.attribute()));
                if (stack == null) {
                    continue;
                }
                double value = FocusPassive.grantedAt(stack, organ, category);
                if (value != 0.0D) {
                    instance.addTransientModifier(FocusPassive.modifier(organ, category, value));
                }
            }
        }
    }
}
