package com.nakami.mcheadfunction.client;

/** Render-only impulses: never change aim, movement, or the server's charge heading. */
public final class GoatImpact {
	public static int ticks;
	private GoatImpact() { }
	public static float pitch(float delta) { return (float) Math.sin((ticks - delta) * 2.4) * Math.max(0, ticks - delta) * 0.13F; }
	public static float yaw(float delta) { return (float) Math.cos((ticks - delta) * 2.1) * Math.max(0, ticks - delta) * 0.09F; }
}
