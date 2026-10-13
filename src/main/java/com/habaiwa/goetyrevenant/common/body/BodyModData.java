package com.habaiwa.goetyrevenant.common.body;

import com.habaiwa.goetyrevenant.GoetyRevenant;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 改造身体的数据（存在玩家 persistent data 上）。
 *
 * <p>三件事：有没有解锁改造、每个窍开没开、每个窍里嵌的是什么聚晶。</p>
 *
 * <p><b>解锁</b>：跳哭泣之井（Malum 的结构）拿到「改造身体」的资格。<br>
 * <b>开窍</b>：每个窍都要**单独喝下对应元素的「开窍魔药」**——
 * 喝下去会点燃**元素超载**，去克它的地方压下去，那个窍才开（见 {@link ElementOverload}）。<br>
 * <b>首次嵌入</b>：窍开了之后，第一次把聚晶刻进去要走**扣血 + 极速抽灵魂力**的仪式
 * （见 {@link Embedding}）。<br>
 * <b>之后换聚晶</b>：只花灵魂能量，瞬间完成。</p>
 */
public final class BodyModData {
    private static final String KEY_UNLOCKED = "GRBodyUnlocked";
    private static final String KEY_SLOT_UNLOCKED_PREFIX = "GRBodySlotUnlocked_";
    private static final String KEY_OPENED_PREFIX = "GRBodyOpened_";
    private static final String KEY_FOCUS_PREFIX = "GRBodyFocus_";
    /** 已经被献祭掉的窍 —— 永久封锁，且和"没开窍"不是一回事。 */
    private static final String KEY_SACRIFICED_PREFIX = "GRBodySacrificed_";

    private BodyModData() {
    }

    private static CompoundTag tag(Player player) {
        return player.getPersistentData();
    }

    public static boolean isUnlocked(Player player) {
        return tag(player).getBoolean(KEY_UNLOCKED);
    }

    public static void setUnlocked(Player player, boolean unlocked) {
        tag(player).putBoolean(KEY_UNLOCKED, unlocked);
    }

    /**
     * 这个窍是否被**封锁**（封锁了就不能嵌入，也不参与轮盘）。
     *
     * <p><b>默认全部封锁</b>，开启的唯一途径是**觉醒**（跳进哭泣之井、答完七问答）。
     * 开窍魔药那套已经取消 —— 身体是那七问答改造的，答完六窍直接全开。</p>
     *
     * <p>（这里原本写的是"要喝开窍魔药"，魔药删掉之后那句话成了误导：
     * 窍永远开不了，于是嵌入位永远是锁的。）</p>
     */
    public static boolean isSlotLocked(Player player, OrganType organ) {
        // 献祭掉的窍也算锁 —— 而且是**永久的**（不能再开）
        return isSacrificed(player, organ) || !isSlotUnlocked(player, organ);
    }

    /** 这个窍是不是已经被献祭掉了（成神仪式之后）。 */
    public static boolean isSacrificed(Player player, OrganType organ) {
        return tag(player).getBoolean(KEY_SACRIFICED_PREFIX + organ.getId());
    }

    public static void setSacrificed(Player player, OrganType organ, boolean value) {
        tag(player).putBoolean(KEY_SACRIFICED_PREFIX + organ.getId(), value);
    }

    /** 这个窍开没开（= 喝过对应的开窍魔药）。 */
    public static boolean isSlotUnlocked(Player player, OrganType organ) {
        return tag(player).getBoolean(KEY_SLOT_UNLOCKED_PREFIX + organ.getId());
    }

    public static void setSlotUnlocked(Player player, OrganType organ, boolean unlocked) {
        tag(player).putBoolean(KEY_SLOT_UNLOCKED_PREFIX + organ.getId(), unlocked);
    }

    /** 反向写法，给调试指令用。 */
    public static void setSlotLocked(Player player, OrganType organ, boolean locked) {
        setSlotUnlocked(player, organ, !locked);
    }

    /**
     * 这个窍**有没有行过首次嵌入的仪式**。
     *
     * <p>和 {@link #isSlotUnlocked 开窍} 是**两件事**，别混：</p>
     * <ul>
     *   <li><b>开窍</b>（`isSlotUnlocked`）—— 喝魔药 + 元素超载 + 去克制地压下去。
     *       这是「这个窍能不能用」的资格。</li>
     *   <li><b>仪式</b>（`isOpened`）—— 第一次把聚晶真正刻进去时那段
     *       极速抽灵魂力 + 扣血到 1 点的过程。走过一次之后，这个窍再换聚晶就只花灵魂能量。</li>
     * </ul>
     */
    public static boolean isOpened(Player player, OrganType organ) {
        return tag(player).getBoolean(KEY_OPENED_PREFIX + organ.getId());
    }

    public static void setOpened(Player player, OrganType organ, boolean opened) {
        tag(player).putBoolean(KEY_OPENED_PREFIX + organ.getId(), opened);
    }

    /** 取这个器官里嵌的聚晶（没嵌就返回 null）。 */
    @Nullable
    public static ItemStack getFocus(Player player, OrganType organ) {
        CompoundTag t = tag(player);
        String key = KEY_FOCUS_PREFIX + organ.getId();
        return t.contains(key) ? ItemStack.of(t.getCompound(key)) : null;
    }

    public static void setFocus(Player player, OrganType organ, ItemStack stack) {
        CompoundTag t = tag(player);
        String key = KEY_FOCUS_PREFIX + organ.getId();
        if (stack.isEmpty()) {
            t.remove(key);
        } else {
            t.put(key, stack.save(new CompoundTag()));
        }
    }

    /** 全身已嵌入的聚晶（按器官顺序）。 */
    public static Map<OrganType, ItemStack> allFocuses(Player player) {
        Map<OrganType, ItemStack> map = new EnumMap<>(OrganType.class);
        for (OrganType organ : OrganType.values()) {
            ItemStack stack = getFocus(player, organ);
            if (stack != null && !stack.isEmpty()) {
                map.put(organ, stack);
            }
        }
        return map;
    }

    public static int embeddedCount(Player player) {
        return allFocuses(player).size();
    }

    public static void clear(Player player) {
        CompoundTag t = tag(player);
        for (OrganType organ : OrganType.values()) {
            t.remove(KEY_FOCUS_PREFIX + organ.getId());
            t.remove(KEY_SLOT_UNLOCKED_PREFIX + organ.getId());
            t.remove(KEY_OPENED_PREFIX + organ.getId());
            t.remove(KEY_SACRIFICED_PREFIX + organ.getId());
        }
    }

    public static String modId() {
        return GoetyRevenant.MODID;
    }
}
