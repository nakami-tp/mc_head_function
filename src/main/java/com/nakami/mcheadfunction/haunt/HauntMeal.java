package com.nakami.mcheadfunction.haunt;

import com.nakami.mcheadfunction.mixin.LivingEntityDropInvoker;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;

/** Captures only this victim's vanilla drops, without spawning experience or a corpse. */
public final class HauntMeal {
	private record Capture(LivingEntity victim, List<ItemStack> drops) { }
	private static final ThreadLocal<Capture> CURRENT = new ThreadLocal<>();
	private HauntMeal() { }

	public static boolean capturing(Entity entity) {
		Capture capture = CURRENT.get();
		return capture != null && capture.victim() == entity;
	}

	public static void collect(ItemStack stack) {
		if (!stack.isEmpty()) CURRENT.get().drops().add(stack.copy());
	}

	public static void bite(LivingEntity victim) {
		var access = (LivingEntityDropInvoker) victim;
		var sound = access.mhf$deathSound();
		if (sound != null) victim.playSound(sound, access.mhf$soundVolume(), victim.getSoundPitch());
	}

	public static List<ItemStack> consume(ServerWorld world, LivingEntity victim, Entity tongue) {
		var drops = new ArrayList<ItemStack>();
		Capture previous = CURRENT.get();
		CURRENT.set(new Capture(victim, drops));
		try {
			((LivingEntityDropInvoker) victim).mhf$drop(world, world.getDamageSources().mobProjectile(tongue, null));
		} finally {
			if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
		}
		// Use vanilla death smoke at the visible mouth, including with minimal particles.
		for (var player : world.getPlayers()) {
			if (player.squaredDistanceTo(tongue) <= 32 * 32)
				world.spawnParticles(player, ParticleTypes.POOF, true, tongue.getX(), tongue.getY(), tongue.getZ(), 20, 0.25, 0.2, 0.25, 0.02);
		}
		victim.discard();
		return drops;
	}
}
