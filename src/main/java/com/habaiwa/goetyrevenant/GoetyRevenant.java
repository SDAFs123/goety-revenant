package com.habaiwa.goetyrevenant;

import com.habaiwa.goetyrevenant.common.tooltip.RevelationHaloTooltip;

import com.habaiwa.goetyrevenant.common.tooltip.FocusTooltip;

import com.habaiwa.goetyrevenant.common.event.RevenantEvents;
import com.habaiwa.goetyrevenant.init.ModCreativeTabs;
import com.habaiwa.goetyrevenant.init.ModEffects;
import com.habaiwa.goetyrevenant.init.ModEntities;
import com.habaiwa.goetyrevenant.init.ModItems;
import com.habaiwa.goetyrevenant.common.network.RevenantNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 诡厄巫法：回魂 / Goety: Revenant
 *
 * <p>诡厄巫法（Goety - The Dark Arts）的整合包特供附属。
 * mod bus 负责注册内容，game bus 负责回魂机制的事件监听。</p>
 */
@Mod(GoetyRevenant.MODID)
public class GoetyRevenant {
    public static final String MODID = "goety_revenant";

    public GoetyRevenant() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 配置（config/goety_revenant.json，键名与说明全中文）
        com.habaiwa.goetyrevenant.common.config.RevenantConfig.load();
        // 装了 Cloth Config 才挂图形配置界面（没装也能玩，只是改配置要手动开 json）
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()
                && net.minecraftforge.fml.ModList.get().isLoaded("cloth_config")) {
            com.habaiwa.goetyrevenant.client.config.ClothConfigEntry.register();
        }

        ModItems.register(modBus);
        com.habaiwa.goetyrevenant.init.ModBlocks.register(modBus);
        ModEffects.register(modBus);
        ModEntities.register(modBus);
        ModCreativeTabs.register(modBus);

        RevenantNetwork.register();
        com.habaiwa.goetyrevenant.common.compat.RevenantCompat.init();
        com.habaiwa.goetyrevenant.common.compat.EnigmaticLegacyCompat.init();
        MinecraftForge.EVENT_BUS.register(com.habaiwa.goetyrevenant.common.compat.EnigmaticLegacyCompat.class);
        // 调试指令：/grdebug ...
        MinecraftForge.EVENT_BUS.register(com.habaiwa.goetyrevenant.common.command.DebugCommands.class);
        // 连携（元素反应）的触发点
        MinecraftForge.EVENT_BUS.register(com.habaiwa.goetyrevenant.common.reaction.ReactionEvents.class);
        com.habaiwa.goetyrevenant.common.body.FocusPassives.registerDefaults();
        // 血肉仪式：我们自己的仪式种类（挂在本体的仪式系统上）
        com.habaiwa.goetyrevenant.common.ritual.FleshRitual.register();

        // RevenantEvents 用 @Mod.EventBusSubscriber 自动注册 —— 这里不要再手动注册，
        // 否则每个处理器会被调用两次（附身倒计时走双倍、受伤扣双倍灵魂能量）。
        MinecraftForge.EVENT_BUS.register(com.habaiwa.goetyrevenant.common.compat.MalumCompat.class);
        MinecraftForge.EVENT_BUS.register(com.habaiwa.goetyrevenant.common.compat.MalumWardCompat.class);
        MinecraftForge.EVENT_BUS.register(com.habaiwa.goetyrevenant.common.compat.WeepingWellUnlock.class);
        MinecraftForge.EVENT_BUS.register(com.habaiwa.goetyrevenant.common.compat.ScytheCompat.class);
        MinecraftForge.EVENT_BUS.register(com.habaiwa.goetyrevenant.common.compat.MalumHarvestCompat.class);
        MinecraftForge.EVENT_BUS.register(com.habaiwa.goetyrevenant.common.soul.SoulPerception.class);
        // 元素超载的结算（喝魔药之后那一段）
        MinecraftForge.EVENT_BUS.register(com.habaiwa.goetyrevenant.common.body.ElementOverload.class);
        // 灵魂溃散（灵魂能量被抽干之后的代价）
        MinecraftForge.EVENT_BUS.register(com.habaiwa.goetyrevenant.common.soul.SoulCollapse.class);
        // 神识 —— 修仙者那种感知，不用看也知道周围什么情况
        MinecraftForge.EVENT_BUS.register(com.habaiwa.goetyrevenant.common.body.ShenShi.class);
        // 所有聚晶的 tooltip（被动 + 归属窍）
        // 注意：这个类【没有】类级注解，必须在这里手动注册 —— 别再加注解，那会注册两次。
        MinecraftForge.EVENT_BUS.register(com.habaiwa.goetyrevenant.common.tooltip.FocusTooltip.class);
        // 给启示录的光环加上我们的内容（零侵入，走 ItemTooltipEvent）
        MinecraftForge.EVENT_BUS.register(com.habaiwa.goetyrevenant.common.tooltip.RevelationHaloTooltip.class);
    }
}
