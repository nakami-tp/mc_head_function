package com.nakami.mcheadfunction.rule;

/**
 * Numbers and predicates that do not need a live Minecraft world.
 */
public final class HeadRules {
	public static final int SHAKE_HITS = 3;
	public static final long SHAKE_WINDOW_MS = 2000L;
	public static final int ACTIVE_MAX_TICKS = 200;
	public static final int FROG_RANGE = 16;
	public static final int FROG_COOLDOWN_TICKS = 20;
	public static final int BEE_SUMMON_COUNT = 3;
	public static final int SONAR_RANGE = 16;
	public static final int SONAR_TICKS = 100;
	public static final int LLAMA_COOLDOWN_TICKS = 3;
	public static final int LIGHTNING_CHARGE_TICKS = 100;
	public static final int CREEPER_RECHARGE_TICKS = 100;
	public static final int CREEPER_MAX_CHARGES = 2;
	public static final int ENDERMAN_DODGE_COOLDOWN_TICKS = 100;
	public static final int ENDERMAN_EAT_MAX = 16;
	public static final int ZOMBIE_BITE_DAMAGE = 2;
	public static final int DEFAULT_THROW_DAMAGE = 1;
	public static final double KNOCK_OFF_DISTANCE = 2.5;
	public static final double HEAD_REGION_FRACTION = 2.0 / 3.0;

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
}
