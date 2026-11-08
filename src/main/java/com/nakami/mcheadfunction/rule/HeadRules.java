package com.nakami.mcheadfunction.rule;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Numbers and predicates that do not need a live Minecraft world.
 */
public final class HeadRules {
	public static final int FROG_PARRY_COOLDOWN = 10, FROG_PARRY_MASTER_COOLDOWN = 4;
	public static final int FROG_AMMO_HURT_INTERVAL = 10, FROG_AMMO_HOLD_TICKS = 60;
	public static final double FROG_AMMO_SPEED_MULTIPLIER = 1.5, FROG_INTERCEPT_RADIUS = 0.5;
	public static final int SHAKE_HITS = 3;
	public static final long SHAKE_WINDOW_MS = 2000L;
	public static final int ACTIVE_MAX_TICKS = 200;
	public static final int FROG_RANGE = 16;
	public static final int FROG_COOLDOWN_TICKS = 20;
	public static final int FROG_MOUTH_BITE_TICKS = 10;
	public static final float FROG_MOUTH_DAMAGE = 2.0F;
	public static final int FROG_CREEPER_FUSE_TICKS = 60;
	public static final int FROG_CREEPER_FLIGHT_TICKS = 60;
	public static final double FROG_SPIT_SPEED = 1.6;
	public static final double FROG_SPIT_LIFT = 0.25;
	public static final int FROG_SPIT_COOLDOWN_TICKS = 10;
	public static final int BEE_SUMMON_COUNT = 3;
	public static final int SONAR_RANGE = 16;
	public static final int SONAR_TICKS = 100;
	public static final int LLAMA_COOLDOWN_TICKS = 3;
	public static final int LIGHTNING_CHARGE_TICKS = 100;
	public static final int CREEPER_RECHARGE_TICKS = 100;
	public static final int CREEPER_MAX_CHARGES = 2;
	public static final int CREEPER_FUSE_TICKS = 100;
	public static final int CREEPER_PRIME_TICKS = 10;
	public static final int ENDERMAN_DODGE_COOLDOWN_TICKS = 40;
	public static final int ENDERMAN_MASTERED_DODGE_COOLDOWN_TICKS = 20;
	public static final double ENDERMAN_DODGE_DISTANCE = 8.0;
	public static final int ENDERMAN_EAT_MAX = 16;
	public static final int ENDERMAN_EAT_INTERVAL_TICKS = 5;
	public static final int ZOMBIE_BITE_DAMAGE = 2;
	public static final int DEFAULT_THROW_DAMAGE = 1;
	public static final double KNOCK_OFF_DISTANCE = 2.5;
	public static final double HEAD_REGION_FRACTION = 2.0 / 3.0;
	public static final double GOAT_DASH_DISTANCE = 6.0;
	public static final int WOLF_FLEE_TICKS = 100;
	public static final double SHEEP_QUIET_RANGE = 2.0;
	public static final double PIERCE_DISTANCE = 8.0;
	public static final double PIERCE_STEP = 0.25;
	public static final float PIERCE_DAMAGE = 4.0F;
	public static final int PIG_STUFFED_TICKS = 200;
	public static final int PIG_SWALLOW_TICKS = 20;
	public static final int PIG_SPIT_WINDUP = 6;
	public static final float PIG_SPEED_FACTOR = -0.72F;
	public static final float PIG_JUMP_FACTOR = -0.4F;

	private HeadRules() {
	}

	public static boolean isHeadRegion(double hitY, double boxMinY, double boxMaxY) {
		double height = boxMaxY - boxMinY;
		if (height <= 0) {
			return false;
		}
		return hitY >= boxMinY + height * HEAD_REGION_FRACTION;
	}

	public static boolean canEndermanEat(boolean unbreakable, boolean hasBlockEntity, boolean chestLike) {
		return !unbreakable && !hasBlockEntity && !chestLike;
	}

	public static int lightningChargeNeeded(boolean thunderstorm) {
		return thunderstorm ? LIGHTNING_CHARGE_TICKS / 2 : LIGHTNING_CHARGE_TICKS;
	}

	public static boolean isThrower(java.util.UUID throwerId, java.util.UUID livingId) {
		return throwerId != null && throwerId.equals(livingId);
	}

	public static float extraPigSaturation(float foodSaturation) {
		return Math.max(0.0F, foodSaturation);
	}

	/** 0 on press, 1 once the two ignition shakes finish. Thrown heads are already armed. */
	public static float creeperPrimeProgress(float remainingFuse, boolean cooking) {
		if (!cooking) return 1;
		float elapsed = CREEPER_FUSE_TICKS - remainingFuse;
		if (elapsed <= 0) return 0;
		if (elapsed >= CREEPER_PRIME_TICKS) return 1;
		return elapsed / CREEPER_PRIME_TICKS;
	}

	public static boolean creeperFuseLit(float remainingFuse, boolean cooking) {
		return creeperPrimeProgress(remainingFuse, cooking) >= 1;
	}

	public static float pigSwallowProgress(int stuffedTicks, float delta) {
		if (stuffedTicks <= 0) return 1;
		float eaten = PIG_STUFFED_TICKS - (stuffedTicks - delta);
		if (eaten <= 0) return 0;
		if (eaten >= PIG_SWALLOW_TICKS) return 1;
		return eaten / PIG_SWALLOW_TICKS;
	}

	public static float pigFatness(int stuffedTicks, float delta) {
		if (stuffedTicks <= 0) return 0;
		float remaining = stuffedTicks - delta;
		if (remaining > PIG_STUFFED_TICKS - PIG_SWALLOW_TICKS) {
			return 1 - (remaining - (PIG_STUFFED_TICKS - PIG_SWALLOW_TICKS)) / PIG_SWALLOW_TICKS;
		}
		if (remaining < PIG_SPIT_WINDUP) {
			return Math.max(0, remaining / PIG_SPIT_WINDUP);
		}
		return 1;
	}

	public static boolean sheepQuiets(double distanceToSensorSq) {
		return distanceToSensorSq > SHEEP_QUIET_RANGE * SHEEP_QUIET_RANGE;
	}

	public static boolean isMeleeHit(boolean direct, boolean projectile) {
		return direct && !projectile;
	}

	public static boolean shouldEatThisTick(int activeTicks) {
		return activeTicks > 0 && activeTicks % ENDERMAN_EAT_INTERVAL_TICKS == 0;
	}

	public static boolean frogChewsThisTick(int mouthTicks) {
		return mouthTicks > 0 && mouthTicks % FROG_MOUTH_BITE_TICKS == 0;
	}

	/** Worn frog head scale while a mouthful is held. Shrinks as the meal is digested. */
	public static float frogMouthSize(float width, float height, float healthFraction) {
		float bulk = 1.35F + 0.25F * Math.min(1F, width * height / 2.0F);
		return bulk * (0.78F + 0.22F * Math.max(0F, Math.min(1F, healthFraction)));
	}

	/** Idle jaw wobble plus a bite snap. hurtTime is the vanilla 10-tick flash after a chew. */
	public static float frogChew(float age, float hurtTime, boolean urgent) {
		float speed = urgent ? 1.6F : 0.6F;
		float idle = (float) Math.sin(age * speed) * (urgent ? 0.09F : 0.05F);
		float bite = Math.max(0F, Math.min(1F, hurtTime / 10.0F));
		return idle + bite * 0.12F;
	}

	/** First-person camera nod while digesting. Degrees, never large enough to fight aim. */
	public static float frogChewPitch(float age, float hurtTime) {
		float idle = (float) Math.sin(age * 0.8F) * 0.9F;
		return idle + Math.max(0F, hurtTime) * 0.22F;
	}

	public static float frogChewYaw(float age, float hurtTime) {
		return (float) Math.cos(age * 0.55F) * 0.35F + Math.max(0F, hurtTime) * 0.08F;
	}

	/**
	 * Breadth-first collect of connected nodes. Only nodes that pass {@code include}
	 * enter the result and the frontier, so air/other neighbors do not consume the cap.
	 */
	public static <T> List<T> collectConnected(T origin, int max, Predicate<T> include, Function<T, Iterable<T>> neighbors) {
		List<T> found = new ArrayList<>();
		if (max <= 0 || !include.test(origin)) {
			return found;
		}
		Set<T> seen = new HashSet<>();
		ArrayDeque<T> frontier = new ArrayDeque<>();
		frontier.add(origin);
		seen.add(origin);
		while (!frontier.isEmpty() && found.size() < max) {
			T current = frontier.removeFirst();
			found.add(current);
			for (T next : neighbors.apply(current)) {
				if (seen.add(next) && include.test(next)) {
					frontier.add(next);
				}
			}
		}
		return found;
	}
}
