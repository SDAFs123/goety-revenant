package com.habaiwa.goetyrevenant.init;

import com.habaiwa.goetyrevenant.GoetyRevenant;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 两个**互相独立**的创造模式物品栏。
 *
 * <table border="1">
 *   <tr><th>标签栏</th><th>放什么</th></tr>
 *   <tr><td>诡厄巫法：回魂</td><td>灵魂那一套：法杖、聚晶、长袍、魔药、精魄、染魂钢图腾…</td></tr>
 *   <tr><td>诡厄巫法：血肉</td><td>血肉那一套：血肉祭坛、血肉之笼、吞噬基座、血管、血肉之心、血肉图腾</td></tr>
 * </table>
 *
 * <p><b>标题必须用 {@code Component.literal}，不能用 translatable</b>：
 * 原版 {@code Bootstrap.bootStrap()} → {@code CreativeModeTabs} 静态初始化时会 {@code getString()} 解析标签页名字，
 * 这一步会触发 {@code Language.<clinit>}。而 CustomSkinLoader 会变换 Language / ResourceManager，
 * 如果它在这之前解析翻译，就会抛 {@code NoClassDefFoundError: customskinloader/...} 导致启动即崩。
 * 字面量不走 Language，踩不到这个坑。</p>
 */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GoetyRevenant.MODID);

    /** 回魂：灵魂、附身、七窍、神识、灵化。 */
    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.literal("诡厄巫法：回魂"))
            .icon(() -> new ItemStack(ModItems.REVENANT_STAFF.get()))
            // **专属背景**：一片灵魂空间 —— 深青黑的底、几粒魂火、极淡的魂带
            .withBackgroundLocation(new net.minecraft.resources.ResourceLocation(
                    GoetyRevenant.MODID, "textures/gui/tab_revenant.png"))
            .displayItems((params, output) -> {
                output.accept(ModItems.REVENANT_STAFF.get());
                output.accept(ModItems.SOUL_TALISMAN.get());
                output.accept(ModItems.SOUL_RECALL_FOCUS.get());
                output.accept(ModItems.CORPSE_STORE_FOCUS.get());
                output.accept(ModItems.SOUL_HARVEST_CHARM.get());
                output.accept(ModItems.SOUL_STAINED_STEEL_TOTEM.get());
                output.accept(ModItems.SPIRIT_CONVERTER.get());
                output.accept(ModItems.REVENANT_HOOD.get());
                output.accept(ModItems.REVENANT_ROBE.get());
                output.accept(ModItems.REVENANT_LEGGINGS.get());
                output.accept(ModItems.REVENANT_BOOTS.get());
                output.accept(ModItems.REVENANT_SPAWN_EGG.get());
            })
            .build());

    /** 血肉：仪式设备与血肉能量。 */
    public static final RegistryObject<CreativeModeTab> FLESH = TABS.register("flesh", () -> CreativeModeTab.builder()
            .title(Component.literal("诡厄巫法：血肉"))
            .icon(() -> new ItemStack(ModItems.FLESH_HEART.get()))
            // **专属背景**：一块腹腔 —— 暗红的底、横向肌纤维、几块血斑、两道血管
            .withBackgroundLocation(new net.minecraft.resources.ResourceLocation(
                    GoetyRevenant.MODID, "textures/gui/tab_flesh.png"))
            .displayItems((params, output) -> {
                output.accept(ModItems.FLESH_ALTAR.get());
                output.accept(ModItems.FLESH_CAGE.get());
                output.accept(ModItems.DEVOUR_PEDESTAL.get());
                output.accept(ModItems.BLOOD_VESSEL.get());
                output.accept(ModItems.FLESH_HEART.get());
                output.accept(ModItems.FLESH_TOTEM.get());
            })
            .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
