package com.nakami.mcheadfunction.blaze;

/** Maps heat and overheat cooldown onto the worn head's visible phase. */
public final class BlazeLook {
	public enum Phase { IDLE, HEATING, HIGH, COOLING }

	private BlazeLook() {
	}

	public static Phase phase(int heat, int cooldown) {
		if (cooldown > 0) {
			return Phase.COOLING;
		}
		if (heat >= BlazeFire.HIGH_HEAT) {
			return Phase.HIGH;
		}
		if (heat > 0) {
			return Phase.HEATING;
		}
		return Phase.IDLE;
	}

	/** 0 = cold head, 1 = fully lit. Cooling stays dark so ash can read. */
	public static float glow(int heat, int cooldown) {
		return switch (phase(heat, cooldown)) {
			case IDLE, COOLING -> 0;
			case HEATING -> heat / (float) BlazeFire.HIGH_HEAT;
			case HIGH -> 1;
		};
	}

	/** 1 = just overheated, 0 = recovered. */
	public static float ash(int heat, int cooldown) {
		return phase(heat, cooldown) == Phase.COOLING ? Math.min(1, cooldown / (float) BlazeFire.COOLDOWN) : 0;
	}

	/** High-heat throb between about 0.58 and 1. Other phases stay fully on. */
	public static float pulse(int heat, int cooldown, float time) {
		if (phase(heat, cooldown) != Phase.HIGH) {
			return 1;
		}
		return 0.58F + 0.42F * (float) Math.sin(time * 0.55);
	}
}
