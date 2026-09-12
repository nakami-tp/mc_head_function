package com.nakami.mcheadfunction.behead;

import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;

/**
 * Headless bodies keep only aimless walking. Targeted goals and brain memories are stripped.
 */
public final class HeadlessAi {
	private HeadlessAi() {
	}

	public static void suppress(MobEntity mob, GoalSelector goals, GoalSelector targets) {
		mob.setTarget(null);
		mob.setAttacking(false);
		targets.clear(goal -> true);
		goals.clear(goal -> !isAimless(goal));
		ensureWander(mob, goals);
		forgetTargets(mob.getBrain());
	}

	public static boolean isAimless(Goal goal) {
		return goal instanceof WanderAroundGoal
			|| goal instanceof SwimGoal
			|| goal instanceof LookAroundGoal;
	}

	private static void ensureWander(MobEntity mob, GoalSelector goals) {
		boolean hasWander = goals.getGoals().stream().anyMatch(prioritized -> prioritized.getGoal() instanceof WanderAroundGoal);
		if (!hasWander && mob instanceof PathAwareEntity path) {
			goals.add(8, new WanderAroundFarGoal(path, 1.0));
			goals.add(9, new LookAroundGoal(mob));
			if (goals.getGoals().stream().noneMatch(prioritized -> prioritized.getGoal() instanceof SwimGoal)) {
				goals.add(0, new SwimGoal(path));
			}
		}
	}

	private static void forgetTargets(Brain<?> brain) {
		brain.forget(MemoryModuleType.ATTACK_TARGET);
		brain.forget(MemoryModuleType.ANGRY_AT);
		brain.forget(MemoryModuleType.ATTACK_COOLING_DOWN);
		brain.forget(MemoryModuleType.BREED_TARGET);
		brain.forget(MemoryModuleType.TEMPTING_PLAYER);
		brain.forget(MemoryModuleType.IS_TEMPTED);
		brain.forget(MemoryModuleType.AVOID_TARGET);
		brain.forget(MemoryModuleType.NEAREST_ATTACKABLE);
		brain.forget(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
		brain.forget(MemoryModuleType.LIKED_PLAYER);
		brain.forget(MemoryModuleType.NEAREST_PLAYER_HOLDING_WANTED_ITEM);
		brain.doExclusively(Activity.IDLE);
	}
}
