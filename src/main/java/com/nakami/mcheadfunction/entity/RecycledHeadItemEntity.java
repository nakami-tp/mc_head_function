package com.nakami.mcheadfunction.entity;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.world.World;

/**
 * Recycled head on the ground. Explosions cannot destroy it; lava and void still can.
 */
public class RecycledHeadItemEntity extends ItemEntity {
	public RecycledHeadItemEntity(World world, double x, double y, double z, ItemStack stack) {
		super(world, x, y, z, stack);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
			return false;
		}
		return super.damage(source, amount);
	}
}
