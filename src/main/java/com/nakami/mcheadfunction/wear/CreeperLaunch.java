package com.nakami.mcheadfunction.wear;

import com.nakami.mcheadfunction.head.PlayerHeadAccess;
import net.minecraft.server.network.ServerPlayerEntity;

/** Protect only the flight caused by the owner's bomb, even after changing helmets. */
public final class CreeperLaunch {
	private CreeperLaunch() { }

	public static void protect(ServerPlayerEntity player) {
		var state = PlayerHeadAccess.state(player);
		state.creeperFallProtected = true;
		state.creeperLaunchedAt = player.getWorld().getTime();
		player.fallDistance = 0;
	}

	public static void tick(ServerPlayerEntity player) {
		var state = PlayerHeadAccess.state(player);
		if (!state.creeperFallProtected) return;
		player.fallDistance = 0;
		// Allow movement packets to report takeoff before testing for landing.
		if (player.getWorld().getTime() - state.creeperLaunchedAt > 5
			&& (player.isOnGround() || player.isTouchingWater() || player.hasVehicle()
				|| player.getAbilities().flying || !player.isAlive())) state.creeperFallProtected = false;
	}
}
