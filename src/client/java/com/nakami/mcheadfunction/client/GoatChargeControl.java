package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.head.HeadLookups;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.net.GoatChargeS2CPayload;
import com.nakami.mcheadfunction.net.GoatSteerC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.world.ClientWorld;

public final class GoatChargeControl {
	private static int remaining;
	private static ClientWorld world;
	private GoatChargeControl() { }

	public static boolean active() {
		var client = MinecraftClient.getInstance();
		return remaining > 0 && world == client.world && client.player != null && client.player.isAlive()
			&& !client.player.isSpectator() && HeadLookups.worn(client.player) == HeadType.GOAT;
	}
	public static void receive(GoatChargeS2CPayload payload) {
		var client = MinecraftClient.getInstance();
		remaining = payload.active() ? 10 : 0;
		world = client.world;
		if (!active()) return;
		client.player.setYaw(payload.yaw());
		client.player.setPitch(payload.pitch());
		client.player.setHeadYaw(payload.yaw());
	}
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
		ClientPlayNetworking.send(new GoatSteerC2SPayload(direction));
		// A/D steer the charge instead of adding vanilla sideways acceleration.
		input.movementSideways = 0;
		input.movementForward = 0;
	}
}
