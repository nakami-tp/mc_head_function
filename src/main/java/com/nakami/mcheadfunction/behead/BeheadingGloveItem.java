package com.nakami.mcheadfunction.behead;

import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** A collecting tool: three non-damaging punches, regardless of hit height. */
public final class BeheadingGloveItem extends Item {
    public BeheadingGloveItem() { super(new Item.Settings().maxDamage(256)); }

    @Override public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.mc_head_function.beheading_glove.hint").formatted(Formatting.AQUA));
    }
}
