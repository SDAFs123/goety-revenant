package com.habaiwa.goetyrevenant.common.item;

import com.Polarice3.Goety.api.items.magic.IFocus;
import com.Polarice3.Goety.api.magic.ISpell;
import com.habaiwa.goetyrevenant.common.spell.SoulRecallSpell;
import net.minecraft.world.item.Item;

/**
 * 回魂聚晶。
 *
 * <p>实现诡厄巫法的 {@code IFocus}，插在回魂法杖上即可施放 —— 消耗 10000 灵魂能量，
 * 把队友的尸体召唤到施法者身边。多人模式专用：一人召唤、一人附身。</p>
 */
public class SoulRecallFocusItem extends Item implements IFocus {
    private static final ISpell SPELL = new SoulRecallSpell();

    public SoulRecallFocusItem(Properties properties) {
        super(properties);
    }

    @Override
    public ISpell getSpell() {
        return SPELL;
    }
}
