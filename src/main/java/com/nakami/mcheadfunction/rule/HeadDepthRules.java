package com.nakami.mcheadfunction.rule;

/** Tuning shared by movement, progression and their observable-rule tests. */
public final class HeadDepthRules {
	public static final int MASTERY_MAX = 1000;
	public static final int RABBIT_WINDOW = 4;
	public static final int RABBIT_MAX_CHAIN = 16;
	public static final int RABBIT_MAX_HEIGHT = 80;
	public static final int GOAT_DURATION = 100;
	public static final int GOAT_COOLDOWN = 160;
	private static final double[] JUMP_HEIGHTS = {1.25, 1.75, 2.5, 3.5, 5, 7, 10, 14, 20, 28, 40, 50, 60, 68, 74, RABBIT_MAX_HEIGHT};
	private HeadDepthRules() { }

	public static double rabbitHeight(int chain) {
		return JUMP_HEIGHTS[Math.clamp(chain - 1, 0, JUMP_HEIGHTS.length - 1)];
	}
	public static double rabbitSpeedMultiplier(int chain) {
		return chain <= 0 ? 1 : 1.15 + Math.clamp(chain - 1, 0, RABBIT_MAX_CHAIN - 1) * 0.12;
	}
	public static double rabbitJumpVelocity(int chain) {
		double lo = 0, hi = 8, target = rabbitHeight(chain);
		for (int i = 0; i < 40; i++) {
			double mid = (lo + hi) / 2, height = 0;
			for (double v = mid; v > 0; v = (v - 0.08) * 0.98) height += v;
			if (height > target) hi = mid; else lo = mid;
		}
		return lo;
	}
	public static float steer(float heading, float desired, float maxDegrees) {
		float delta = (desired - heading) % 360;
		if (delta >= 180) delta -= 360;
		if (delta < -180) delta += 360;
		return heading + Math.clamp(delta, -maxDegrees, maxDegrees);
	}
}
