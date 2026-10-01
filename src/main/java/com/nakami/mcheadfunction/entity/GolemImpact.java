package com.nakami.mcheadfunction.entity;

import com.nakami.mcheadfunction.head.HeadCrushAccess;
import com.nakami.mcheadfunction.head.HeadEffects;
import com.nakami.mcheadfunction.net.GolemQuakeS2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** One landing impulse, bounded crater and distance-attenuated earthquake feedback. */
public final class GolemImpact {
	private GolemImpact() { }

	public static void land(ThrownHeadEntity head, ServerWorld world, double fall) {
		double radius = Math.min(7, 3 + Math.sqrt(fall) * 0.6);
		double depth = Math.min(4, 1 + fall * 0.15);
		var center = head.getPos();
		for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(center.add(-radius, -depth, -radius)),
			BlockPos.ofFloored(center.add(radius, 0, radius)))) {
			double dx = pos.getX() + 0.5 - center.x, dz = pos.getZ() + 0.5 - center.z;
			double normalized = (dx * dx + dz * dz) / (radius * radius);
			if (normalized > 1 || pos.getY() + 1 <= center.y - depth * (1 - normalized)) continue;
			var state = world.getBlockState(pos);
			if (!state.isAir() && !(state.getBlock() instanceof FluidBlock) && state.getHardness(world, pos) >= 0)
				world.breakBlock(pos, true, head);
		}
		for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class, new Box(center, center).expand(radius, 3, radius),
			entity -> entity.isAlive() && !entity.isSpectator() && entity != head.getOwner())) {
			if (living.getPos().subtract(center).horizontalLength() > radius) continue;
			living.damage(world.getDamageSources().thrown(head, head.getOwner()), (float) Math.min(16, 4 + fall * 0.5));
			((HeadCrushAccess) living).mhf$crush(HeadCrushAccess.DURATION_TICKS);
		}
		world.spawnParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.3, center.z, 5, 2, 0.2, 2, 0);
		world.spawnParticles(ParticleTypes.CLOUD, center.x, center.y + 0.2, center.z, 100, radius * 0.6, 0.3, radius * 0.6, 0.13);
		HeadEffects.ring(world, center, radius);
		world.playSound(null, head.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), head.getSoundCategory(), 3F, 0.5F);
		world.playSound(null, head.getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, head.getSoundCategory(), 2F, 0.5F);
		for (var player : world.getPlayers()) {
			double distance = player.getPos().distanceTo(center);
			if (distance < 32 && ServerPlayNetworking.canSend(player, GolemQuakeS2CPayload.ID)) {
				float strength = (float) (Math.min(1, fall / 12) * (1 - distance / 32));
				ServerPlayNetworking.send(player, new GolemQuakeS2CPayload(strength));
			}
		}
	}
}
