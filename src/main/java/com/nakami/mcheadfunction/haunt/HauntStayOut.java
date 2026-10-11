package com.nakami.mcheadfunction.haunt;

import com.nakami.mcheadfunction.rule.HauntRules;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** A haunting keeps nearby villagers out, so possession can still reach them. */
public final class HauntStayOut {
	private HauntStayOut() { }

	public static boolean active(VillagerEntity villager) {
		if (!(villager.getWorld() instanceof ServerWorld world)) return false;
		for (BlockPos tree : HauntIndex.trees(world)) {
			if (!(world.getBlockEntity(tree) instanceof HauntedTreeBlockEntity core)) continue;
			if (HauntRules.villagersStayOut(core.haunting(),
				villager.getX() - tree.getX(), villager.getY() - tree.getY(), villager.getZ() - tree.getZ())) {
				return true;
			}
		}
		return false;
	}

	public static void tick(VillagerEntity villager) {
		if (!active(villager)) return;
		var brain = villager.getBrain();
		if (!villager.isSleeping() && !brain.hasActivity(Activity.REST)) return;
		if (villager.isSleeping()) villager.wakeUp();
		brain.doExclusively(Activity.IDLE);
		brain.forget(MemoryModuleType.WALK_TARGET);
		brain.forget(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
	}
}
