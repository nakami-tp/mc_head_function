package com.nakami.mcheadfunction.wear;

import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.progression.HeadMastery;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;

public final class BeeCommand {
	private BeeCommand() { }
	public static void command(ServerPlayerEntity player, LivingEntity target) {
		var state = PlayerHeadAccess.state(player);
		if (target != null && owned(target, player)) target = null;
		state.beeTarget = target == null ? null : target.getUuid();
		state.beeTargetTicks = target == null ? 0 : 300;
		final LivingEntity chosen = target;
		for (BeeEntity bee : bees(player)) assign(bee, chosen);
		HeadSounds.play(player, SoundEvents.BLOCK_BEEHIVE_WORK, 0.8F, chosen == null ? 0.7F : 1.5F);
	}
	public static void tick(ServerPlayerEntity player, HeadType worn) {
		var state = PlayerHeadAccess.state(player);
		if (worn != HeadType.BEE) {
			if (state.beeTarget != null) command(player, null);
			return;
		}
		var world = player.getServerWorld();
		LivingEntity chosen = state.beeTarget == null ? null : world.getEntity(state.beeTarget) instanceof LivingEntity living ? living : null;
		if (state.beeTarget != null && (chosen == null || !chosen.isAlive() || chosen.isSpectator()
			|| state.beeTargetTicks <= 0 || chosen.squaredDistanceTo(player) > 48 * 48)) {
			command(player, null);
			chosen = null;
		}
		if (chosen != null && player.age % 4 == 0) {
			// Small nectar droplets and warm pollen orbit the body, with no outline or box.
			double angle = player.age * 0.18;
			for (int i = 0; i < 3; i++) {
				double a = angle + i * Math.PI * 2 / 3;
				world.spawnParticles(ParticleTypes.FALLING_NECTAR, chosen.getX() + Math.cos(a) * (chosen.getWidth() * 0.5 + 0.2),
					chosen.getBodyY(0.7) + 0.2, chosen.getZ() + Math.sin(a) * (chosen.getWidth() * 0.5 + 0.2), 1, 0, 0, 0, 0);
			}
			world.spawnParticles(ParticleTypes.WAX_ON, chosen.getX(), chosen.getBoundingBox().maxY + 0.15, chosen.getZ(), 2, 0.15, 0.1, 0.15, 0);
		}
		LivingEntity attacker = player.getAttacker();
		boolean recentAttack = player.age - player.getLastAttackedTime() < 100;
		for (BeeEntity bee : bees(player)) {
			LivingEntity target = chosen != null ? chosen : recentAttack && attacker != null && attacker.isAlive() && !owned(attacker, player) ? attacker : null;
			assign(bee, target);
			if (target != null) bee.getNavigation().startMovingTo(target, 1.35);
			else if (bee.squaredDistanceTo(player) > 9) bee.getNavigation().startMovingTo(player, 1.15);
		}
	}
	private static boolean owned(LivingEntity entity, ServerPlayerEntity player) {
		return entity == player || entity.getCommandTags().contains("mhf_owner:" + player.getUuid());
	}
	private static java.util.List<BeeEntity> bees(ServerPlayerEntity player) {
		return player.getServerWorld().getEntitiesByClass(BeeEntity.class, player.getBoundingBox().expand(48), b -> b.isAlive() && owned(b, player));
	}
	private static void assign(BeeEntity bee, LivingEntity target) {
		bee.setTarget(target);
		bee.setAngryAt(target == null ? null : target.getUuid());
		bee.setAngerTime(target == null ? 0 : 100);
	}
	public static void onDamage(BeeEntity bee, LivingEntity victim, float damage) {
		if (damage <= 0 || !(bee.getWorld() instanceof net.minecraft.server.world.ServerWorld world)) return;
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!owned(bee, player)) continue;
			HeadMastery.practice(player, HeadType.BEE);
			if (victim.getUuid().equals(PlayerHeadAccess.state(player).beeTarget)) HeadMastery.challenge(player, "bee_commander");
		}
	}
}
