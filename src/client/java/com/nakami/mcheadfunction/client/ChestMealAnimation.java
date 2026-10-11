package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.haunt.HauntTongueEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public final class ChestMealAnimation {
	private ChestMealAnimation() { }
	public static HauntTongueEntity tongue(World world, BlockPos chest) {
		if (world == null) return null;
		return world.getEntitiesByClass(HauntTongueEntity.class, new Box(chest).expand(24), e -> e.chest().equals(chest)).stream().findFirst().orElse(null);
	}
	public static HauntTongueEntity tongue(LivingEntity prey) {
		for (var tongue : prey.getWorld().getEntitiesByClass(HauntTongueEntity.class, prey.getBoundingBox().expand(3), e -> e.targetId() == prey.getId() && e.mealTime() >= 0))
			return tongue;
		return null;
	}
}
