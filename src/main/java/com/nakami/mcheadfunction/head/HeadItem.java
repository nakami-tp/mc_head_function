package com.nakami.mcheadfunction.head;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Equipment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Table head that is not a vanilla skull. Wearable in the helmet slot.
 */
public class HeadItem extends Item implements Equipment {
	public final HeadType headType;

	public HeadItem(HeadType headType, Settings settings) {
		super(settings);
		this.headType = headType;
	}

	public static Settings wearableSettings() {
		return new Settings().maxCount(64);
	}

	@Override
	public EquipmentSlot getSlotType() {
		return EquipmentSlot.HEAD;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		return this.equipAndSwap(this, world, user, hand);
	}
}
