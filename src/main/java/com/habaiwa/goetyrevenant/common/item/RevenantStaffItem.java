package com.habaiwa.goetyrevenant.common.item;

import com.Polarice3.Goety.api.items.magic.IWand;
import com.Polarice3.Goety.api.magic.SpellType;
import net.minecraft.world.item.Item;

/**
 * 回魂法杖。
 *
 * <p>实现诡厄巫法的 {@code IWand}，因此它天然是一个可用的法杖：
 * 插上对应派系的法器（focus）后就能走本体的施法、耗魂、冷却、蓄力全套逻辑。
 * 目前挂在亡灵派系（NECROMANCY）下，后续做回魂专属派系时改 {@link SpellType} 即可。</p>
 */
public class RevenantStaffItem extends Item implements IWand {
    public RevenantStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public SpellType getSpellType() {
        return SpellType.NECROMANCY;
    }
}
