package com.habaiwa.goetyrevenant.common.compat;

import com.habaiwa.goetyrevenant.init.ModTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * **镰刀附魔互通的判定** —— 由 {@code mixin/EnchantmentMixin} 调用。
 *
 * <p>用户 2026-09-27 的要求：「诡厄巫法的噬魂和灵灾的精魂劫掠……
 * 把灵灾的镰刀附魔兼容诡厄巫法镰刀做到一样的效果」「**双方是互通的**」。</p>
 *
 * <p>实测状态（2026-10-14，用户反馈）：</p>
 * <ul>
 *   <li>Goety 附魔 → Malum 镰刀：**能**（走 `EnchantmentCategory.WEAPON`）</li>
 *   <li>Malum 附魔 → Goety 镰刀：**不能**（走 `#malum:scythe` 标签，跨命名空间追加没生效）</li>
 * </ul>
 *
 * <p>所以这里把判定改成**读我们自己的标签** {@code #goety_revenant:scythes}，
 * 它由我们自己的数据包文件定义，不涉及合并。</p>
 */
public final class ScytheInterop {
    private ScytheInterop() {
    }

    /**
     * 这条附魔能不能附到这个物品上（两方都算"能"）。
     *
     * @return true = 放行（Mixin 会把判定直接置为 true）
     */
    public static boolean allows(Enchantment enchantment, ItemStack stack) {
        if (enchantment == null || stack == null || stack.isEmpty()) {
            return false;
        }
        // 物品必须是镰刀（我们的统一标签）
        if (!stack.is(ModTags.SCYTHES)) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ENCHANTMENT.getKey(enchantment);
        if (id == null) {
            return false;
        }
        String ns = id.getNamespace();
        if (!ns.equals(MalumCompat.MALUM) && !ns.equals("goety")) {
            return false;
        }
        // 用户点名排除：精魂劫掠不允许附魔出来
        return !id.getPath().equals("spirit_plunder");
    }
}
