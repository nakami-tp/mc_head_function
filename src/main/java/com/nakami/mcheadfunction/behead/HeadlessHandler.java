package com.nakami.mcheadfunction.behead;

import com.nakami.mcheadfunction.head.HeadlessAccess;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.Blocks;

public final class HeadlessHandler {
	private HeadlessHandler() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(HeadlessHandler::tickWorld);
	}

	private static void tickWorld(ServerWorld world) {
		for (var entity : world.iterateEntities()) {
			if (entity instanceof MobEntity mob && HeadlessAccess.isHeadless(mob)) {
				if (world.getTime() % 8 == 0) {
					world.spawnParticles(
						new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState()),
						mob.getX(),
						mob.getEyeY() - 0.15,
						mob.getZ(),
						2,
						0.08,
						0.02,
						0.08,
						0.0
					);
				}
			}
		}
	}
}
