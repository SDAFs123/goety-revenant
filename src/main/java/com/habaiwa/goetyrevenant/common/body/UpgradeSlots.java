package com.habaiwa.goetyrevenant.common.body;

import com.habaiwa.goetyrevenant.common.config.RevenantConfig;
import com.habaiwa.goetyrevenant.common.soul.SoulTemperature;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * **枝干上的强化槽** —— 一级枝 6 个 + 二级枝 18 个 = **24 个可用槽**。
 *
 * <p>每个槽给出**一个族**的三个方向（见 {@link UpgradeType}），三选一。
 * 四个族轮流分配，所以想堆同一种是堆不满的 —— 这就是"没有全能选项"。</p>
 *
 * <h3>切换有冷却</h3>
 * <p>换一个槽的方向要等一会儿（配置「强化切换冷却」），免得打怪时来回刷。</p>
 */
public final class UpgradeSlots {
    /** 可用槽数。 */
    public static final int COUNT = 24;
    /** 0-5 = 一级枝，6-23 = 二级枝。 */
    public static final int FIRST_LEVEL = 6;

    private static final String KEY_PREFIX = "GRUpgrade_";
    private static final String KEY_COOLDOWN = "GRUpgradeCd";

    private static final UUID FORCE_ID = UUID.nameUUIDFromBytes("goety_revenant:upgrade_force".getBytes());
    private static final UUID WARD_ID = UUID.nameUUIDFromBytes("goety_revenant:upgrade_ward".getBytes());

    private UpgradeSlots() {
    }

    /* ------------------------------------------------------------------ */
    /*  存取                                                                */
    /* ------------------------------------------------------------------ */

    /** 这个槽现在选的什么（null = 空着）。 */
    public static UpgradeType.Type get(ServerPlayer player, int index) {
        String id = player.getPersistentData().getString(KEY_PREFIX + index);
        return id.isEmpty() ? null : UpgradeType.byId(id);
    }

    /** 全部 24 个槽的状态（给客户端界面用）：存的是族内序号 + 1，0 = 空。 */
    public static int[] snapshot(ServerPlayer player) {
        int[] out = new int[COUNT];
        for (int i = 0; i < COUNT; i++) {
            UpgradeType.Type t = get(player, i);
            if (t == null) {
                out[i] = 0;
                continue;
            }
            UpgradeType.Type[] group = UpgradeType.ofGroup(UpgradeType.groupOfSlot(i));
            for (int k = 0; k < group.length; k++) {
                if (group[k].id().equals(t.id())) {
                    out[i] = k + 1;
                    break;
                }
            }
        }
        return out;
    }

    /** 某个方向选了几个槽。 */
    public static int countOf(ServerPlayer player, String id) {
        int n = 0;
        for (int i = 0; i < COUNT; i++) {
            UpgradeType.Type t = get(player, i);
            if (t != null && t.id().equals(id)) {
                n++;
            }
        }
        return n;
    }

    /** 第 index 个槽给出的三个选项。 */
    public static UpgradeType.Type[] candidates(int index) {
        return UpgradeType.ofGroup(UpgradeType.groupOfSlot(index));
    }

    public static int cooldown(ServerPlayer player) {
        return Math.max(0, player.getPersistentData().getInt(KEY_COOLDOWN));
    }

    /**
     * **切换某个槽的方向**。
     *
     * @return 换成功返回 true；冷却中 / 不是这个槽的族 返回 false
     */
    public static boolean set(ServerPlayer player, int index, UpgradeType.Type type) {
        if (index < 0 || index >= COUNT) {
            return false;
        }
        if (cooldown(player) > 0) {
            return false;
        }
        if (type != null && UpgradeType.groupOfSlot(index) != type.group()) {
            return false;   // 只能从该槽给出的那一族里选
        }
        CompoundTag tag = player.getPersistentData();
        if (type == null) {
            tag.remove(KEY_PREFIX + index);
        } else {
            tag.putString(KEY_PREFIX + index, type.id());
        }
        tag.putInt(KEY_COOLDOWN, Math.max(0, RevenantConfig.upgradeSwitchCooldown));
        apply(player);
        return true;
    }

    public static void tick(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        int cd = tag.getInt(KEY_COOLDOWN);
        if (cd > 0) {
            tag.putInt(KEY_COOLDOWN, cd - 1);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  属性（锐爪 / 铁骨 这两个走属性，其余走事件里算）                      */
    /* ------------------------------------------------------------------ */

    public static void apply(ServerPlayer player) {
        int force = countOf(player, "force");
        int ward = countOf(player, "ward");

        AttributeInstance atk = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) {
            atk.removeModifier(FORCE_ID);
            if (force > 0) {
                atk.addTransientModifier(new AttributeModifier(FORCE_ID, "goety_revenant.upgrade.force",
                        force * RevenantConfig.upgradeForcePerSlot / 100.0D,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.removeModifier(WARD_ID);
            if (ward > 0) {
                armor.addTransientModifier(new AttributeModifier(WARD_ID, "goety_revenant.upgrade.ward",
                        ward * RevenantConfig.upgradeWardPerSlot, AttributeModifier.Operation.ADDITION));
            }
        }
    }

    public static void clear(ServerPlayer player) {
        AttributeInstance atk = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) {
            atk.removeModifier(FORCE_ID);
        }
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.removeModifier(WARD_ID);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  各方向的系数（给别的系统调）                                         */
    /* ------------------------------------------------------------------ */

    /** 打出去的伤害倍率：迅羽、凝魂、幽步 的代价。 */
    public static float damageDealtFactor(ServerPlayer player) {
        float f = 1.0F
                - countOf(player, "swift") * RevenantConfig.upgradeSwiftCostPerSlot / 100.0F
                - countOf(player, "soul_condense") * 2 / 100.0F
                - countOf(player, "ghost_step") * 2 / 100.0F;
        return Math.max(0.35F, f);
    }

    /** 挨打的伤害倍率：锐爪 / 魂涌 / 灼魂 / 深识 的代价，加熬火在低温时的代价。 */
    public static float damageTakenFactor(ServerPlayer player) {
        float up = countOf(player, "force") * RevenantConfig.upgradeForceCostPerSlot / 100.0F
                + countOf(player, "soul_surge") * 0.01F
                + countOf(player, "kindle") * 0.01F
                + countOf(player, "deep_sense") * 0.01F;
        if (SoulTemperature.band(player) == SoulTemperature.Band.LOW) {
            up += countOf(player, "endure") * 0.02F;
        }
        return 1.0F + up;
    }

    /** 受伤减免：铁骨 + 熬火（高温时）。 */
    public static float damageReduceFactor(ServerPlayer player) {
        float down = countOf(player, "ward") * RevenantConfig.upgradeWardPerSlot / 100.0F;
        if (SoulTemperature.band(player) == SoulTemperature.Band.HIGH) {
            down += countOf(player, "endure") * 0.03F;
        }
        return Math.max(0.15F, 1.0F - down);
    }

    /** 冷却倍率：迅羽的收益、共鸣的代价。 */
    public static float cooldownFactor(ServerPlayer player) {
        float f = 1.0F
                - countOf(player, "swift") * RevenantConfig.upgradeSwiftPerSlot / 100.0F
                + countOf(player, "resonance") * 0.02F;
        return Math.max(0.25F, f);
    }

    /** 灵魂能量上限倍率：魂涌的收益、噬灵 / 链雷的代价。 */
    public static float soulMaxFactor(ServerPlayer player) {
        float f = 1.0F
                + countOf(player, "soul_surge") * 0.08F
                - countOf(player, "soul_eater") * 0.02F
                - countOf(player, "chain_lightning") * 0.04F;
        return Math.max(0.3F, f);
    }

    /** 灵魂能量回复倍率：凝魂。 */
    public static float soulRegenFactor(ServerPlayer player) {
        return 1.0F + countOf(player, "soul_condense") * 0.10F;
    }

    /** 连携伤害倍率：灼魂。 */
    public static float comboDamageFactor(ServerPlayer player) {
        return 1.0F + countOf(player, "kindle") * 0.10F;
    }

    /** 同元素反应倍率：共鸣。 */
    public static float resonanceFactor(ServerPlayer player) {
        return 1.0F + countOf(player, "resonance") * 0.15F;
    }

    /** 连携能多串几个目标：链雷（每 2 槽 +1）。 */
    public static int chainExtra(ServerPlayer player) {
        return countOf(player, "chain_lightning") / 2;
    }

    /** 神识半径倍率：深识。 */
    public static float senseFactor(ServerPlayer player) {
        return 1.0F + countOf(player, "deep_sense") * 0.12F;
    }

    /** 灵化 / 灵魂状态的移速加成：幽步。 */
    public static float ghostSpeedBonus(ServerPlayer player) {
        return countOf(player, "ghost_step") * 0.08F;
    }

    /** 有没有点「噬灵」。 */
    public static boolean hasSoulEater(ServerPlayer player) {
        return countOf(player, "soul_eater") > 0;
    }
}
