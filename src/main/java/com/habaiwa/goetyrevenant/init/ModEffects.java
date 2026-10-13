package com.habaiwa.goetyrevenant.init;

import com.habaiwa.goetyrevenant.GoetyRevenant;
import com.habaiwa.goetyrevenant.common.body.OrganType;
import com.habaiwa.goetyrevenant.common.effect.ElementOverloadEffect;
import com.habaiwa.goetyrevenant.common.effect.SoulWardEffect;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 回魂系状态效果。 */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, GoetyRevenant.MODID);

    /** 魂佑：回魂机制的核心 buff，死亡时消耗层数抵消一次致命伤。 */
    public static final RegistryObject<MobEffect> SOUL_WARD = EFFECTS.register("soul_ward", SoulWardEffect::new);

    /** 灵魂溃散：灵魂能量被抽干之后的代价（持续掉血 + 抽灵魂能量，但**不触发伤害**）。 */
    public static final RegistryObject<MobEffect> SOUL_COLLAPSE =
            EFFECTS.register("soul_collapse", com.habaiwa.goetyrevenant.common.effect.SoulCollapseEffect::new);

    // ===== 元素超载：一个元素一个（注册名 = 元素id + "_overload"）=====
    private static final Map<OrganType, RegistryObject<MobEffect>> OVERLOADS = new EnumMap<>(OrganType.class);

    static {
        for (OrganType organ : OrganType.values()) {
            OVERLOADS.put(organ, EFFECTS.register(organ.getElement() + "_overload",
                    () -> new ElementOverloadEffect(organ)));
        }
    }

    /** 取某个元素的超载效果。 */
    public static RegistryObject<MobEffect> overload(OrganType organ) {
        return OVERLOADS.get(organ);
    }

    /** 全部超载效果（客户端注册颜色等地方可能要用）。 */
    public static Map<OrganType, RegistryObject<MobEffect>> overloads() {
        return OVERLOADS;
    }

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        EFFECTS.register(bus);
    }
}
