package com.nakami.mcheadfunction.client;

/** Render-only earthquake; never changes player aim or movement. */
public final class GolemQuake {
	private static int ticks;
	private static float strength;
	private GolemQuake() { }
	public static void start(float value) { strength = Math.max(strength, value); ticks = 32; }
	public static void tick(boolean inWorld) {
		if (!inWorld) ticks = 0;
		else if (ticks > 0) ticks--;
		if (ticks == 0) strength = 0;
	}
	public static float amplitude(float delta) { return strength * Math.max(0, ticks - delta) / 32F; }
	public static float pitch(float delta) { return (float) Math.sin((ticks - delta) * 2.3) * amplitude(delta) * 2.5F; }
	public static float yaw(float delta) { return (float) Math.cos((ticks - delta) * 1.7) * amplitude(delta) * 1.6F; }
	public static double lift(float delta) { return Math.sin((ticks - delta) * 2.8) * amplitude(delta) * 0.12; }
}
