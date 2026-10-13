package com.habaiwa.goetyrevenant.common.item;

import com.Polarice3.Goety.api.items.magic.IFocus;
import com.Polarice3.Goety.api.magic.ISpell;
import com.habaiwa.goetyrevenant.common.spell.CorpseStoreSpell;
import net.minecraft.world.item.Item;

/**
 * 纳尸聚晶。
 *
 * <p>把尸体收进聚晶里带着走，再放出来 —— 搬运尸体的第二种手段
 * （第一种是 Carry On，第三种是让队友用回魂聚晶把尸体拉过去）。</p>
 */
public class CorpseStoreFocusItem extends Item implements IFocus {
    private static final ISpell SPELL = new CorpseStoreSpell();

    public CorpseStoreFocusItem(Properties properties) {
        super(properties);
    }

    @Override
    public ISpell getSpell() {
        return SPELL;
    }
}
