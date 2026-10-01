package com.nakami.mcheadfunction.head;

import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Both vanilla and custom heads use the same single-item swap. */
public final class HeadEquipment {
	private HeadEquipment() { }

	public static TypedActionResult<ItemStack> equip(World world, PlayerEntity player, Hand hand) {
		ItemStack held = player.getStackInHand(hand);
		ItemStack worn = player.getEquippedStack(EquipmentSlot.HEAD);
		if (!player.canUseSlot(EquipmentSlot.HEAD) || (!player.isCreative()
			&& EnchantmentHelper.hasAnyEnchantmentsWith(worn, EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE))) {
			return TypedActionResult.fail(held);
		}
		if (ItemStack.areItemsAndComponentsEqual(held, worn) && worn.getCount() == 1) {
			return TypedActionResult.fail(held);
		}
		ItemStack previous = worn.copy();
		ItemStack equipped = held.copyWithCount(1);
		if (!player.isCreative()) held.decrement(1);
		player.equipStack(EquipmentSlot.HEAD, equipped);
		if (!world.isClient()) player.incrementStat(Stats.USED.getOrCreateStat(equipped.getItem()));
		if (held.isEmpty()) return TypedActionResult.success(previous, world.isClient());
		if (!previous.isEmpty() && !world.isClient()) player.getInventory().offerOrDrop(previous);
		return TypedActionResult.success(held, world.isClient());
	}
}
