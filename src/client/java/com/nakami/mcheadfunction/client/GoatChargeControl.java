package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.head.HeadLookups;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.net.GoatChargeS2CPayload;
import com.nakami.mcheadfunction.net.GoatSteerC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;

public final class GoatChargeControl {
	private static int remaining;
	private static ClientWorld world;
	private static final long VIEW_BLEND_NANOS = 50_000_000L;
	private static long viewStarted;
	private static float viewFrom, viewTo, viewPitch;
	private GoatChargeControl() { }

	public static boolean active() {
		var client = MinecraftClient.getInstance();
		return remaining > 0 && world == client.world && client.player != null && client.player.isAlive()
			&& !client.player.isSpectator() && HeadLookups.worn(client.player) == HeadType.GOAT;
	}
	public static void receive(GoatChargeS2CPayload payload) {
		var client = MinecraftClient.getInstance();
		boolean continuing = active();
		long now = System.nanoTime();
		float visible = continuing ? viewYaw(now) : payload.yaw();
		remaining = payload.active() ? 10 : 0;
		world = client.world;
		if (!active()) {
			if (continuing && !payload.active()) {
				// Return free aim at the visible angle, rather than snapping ahead
				// to the server target at the instant a turn is stopped.
				client.player.setYaw(visible);
				client.player.prevYaw = visible;
				client.player.setHeadYaw(visible);
			}
			return;
		}
		viewFrom = visible;
		viewTo = visible + MathHelper.wrapDegrees(payload.yaw() - visible);
		viewPitch = payload.pitch();
		viewStarted = now;
		client.player.setYaw(payload.yaw());
		client.player.setPitch(payload.pitch());
		client.player.setHeadYaw(payload.yaw());
	}
	/** Render clock, independent of packet timing and the entity's tick interpolation. */
	public static float viewYaw() { return viewYaw(System.nanoTime()); }
	private static float viewYaw(long now) {
		float progress = MathHelper.clamp((float) (now - viewStarted) / VIEW_BLEND_NANOS, 0, 1);
		return MathHelper.lerp(progress, viewFrom, viewTo);
	}
	public static float viewPitch() { return viewPitch; }
	public static void tick() {
		if (!active()) { remaining = 0; world = null; }
		else remaining--;
	}
	public static void reset() { remaining = 0; world = null; }

	public static void input(Input input) {
		if (!active()) return;
		var client = MinecraftClient.getInstance();
		int direction = client.currentScreen == null && client.isWindowFocused()
			? (input.pressingRight ? 1 : 0) - (input.pressingLeft ? 1 : 0) : 0;
		boolean drift = client.currentScreen == null && client.isWindowFocused() && input.sneaking && direction != 0;
		ClientPlayNetworking.send(new GoatSteerC2SPayload(direction, drift));
		// A/D steer the charge instead of adding vanilla sideways acceleration.
		input.movementSideways = 0;
		input.movementForward = 0;
		input.sneaking = false;
	}
}
