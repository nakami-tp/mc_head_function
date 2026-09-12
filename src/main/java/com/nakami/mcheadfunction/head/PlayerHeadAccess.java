package com.nakami.mcheadfunction.head;

import net.minecraft.entity.player.PlayerEntity;

public interface PlayerHeadAccess {
	PlayerHeadState mhf$state();

	static PlayerHeadState state(PlayerEntity player) {
		return ((PlayerHeadAccess) player).mhf$state();
	}
}
