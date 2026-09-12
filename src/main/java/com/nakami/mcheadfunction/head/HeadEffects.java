package com.nakami.mcheadfunction.head;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/** Bounded visual cues shared by thrown heads and worn skills. No gameplay mutations. */
public final class HeadEffects {
	private HeadEffects() {}
	public static ParticleEffect particle(HeadType type) {
		return switch (type) {
			case ENDERMAN -> ParticleTypes.PORTAL;
			case CHARGED_CREEPER -> ParticleTypes.ELECTRIC_SPARK;
			case BLAZE -> ParticleTypes.FLAME;
			case CREEPER -> ParticleTypes.SMOKE;
			case BEE -> ParticleTypes.FALLING_NECTAR;
			case ZOMBIE -> ParticleTypes.SPORE_BLOSSOM_AIR;
			case BAT -> ParticleTypes.END_ROD;
			default -> ParticleTypes.CLOUD;
		};
	}
	public static void burst(ServerWorld world, Vec3d pos, HeadType type) {
		world.spawnParticles(particle(type), pos.x, pos.y + 0.2, pos.z, 12, 0.25, 0.2, 0.25, 0.04);
	}
	public static void line(ServerWorld world, Vec3d from, Vec3d to, ParticleEffect particle) {
		int steps = Math.min(32, Math.max(1, (int) Math.ceil(from.distanceTo(to) * 3)));
		for (int i = 0; i <= steps; i++) {
			Vec3d p = from.lerp(to, (double) i / steps);
			world.spawnParticles(particle, p.x, p.y, p.z, 1, 0.015, 0.015, 0.015, 0);
		}
	}
	public static void ring(ServerWorld world, Vec3d center, double radius) {
		for (int i = 0; i < 40; i++) {
			double angle = i * Math.PI * 2 / 40;
			world.spawnParticles(ParticleTypes.CLOUD, center.x + Math.cos(angle) * radius,
				center.y + 0.15, center.z + Math.sin(angle) * radius, 1, 0, 0.05, 0, 0.015);
		}
	}
}
