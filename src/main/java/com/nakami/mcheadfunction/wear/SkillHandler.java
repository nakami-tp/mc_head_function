package com.nakami.mcheadfunction.wear;

import com.nakami.mcheadfunction.entity.HeadAmmoEntity;
import com.nakami.mcheadfunction.entity.FrogTongueEntity;
import com.nakami.mcheadfunction.head.HeadLookups;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.head.HeadlessAccess;
import com.nakami.mcheadfunction.head.PlayerHeadAccess;
import com.nakami.mcheadfunction.head.PlayerHeadState;
import com.nakami.mcheadfunction.rule.HeadRules;
import java.util.List;
import com.nakami.mcheadfunction.progression.HeadMastery;
import com.nakami.mcheadfunction.head.HeadEffects;
import com.nakami.mcheadfunction.head.HeadSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class SkillHandler {
	private SkillHandler() {
	}

	public static void onSkill(ServerPlayerEntity player, boolean pressed) {
		PlayerHeadState state = PlayerHeadAccess.state(player);
		boolean wasHeld = state.skillHeld;
		if (!pressed && wasHeld) com.nakami.mcheadfunction.blaze.BlazeFire.release(player);
		state.skillHeld = pressed && !GoatCharge.isStunned(player) && player.isAlive() && !player.isSpectator();
		if (!pressed || wasHeld || GoatCharge.isStunned(player) || !player.isAlive() || player.isSpectator()) {
			return;
		}
		HeadType worn = HeadLookups.worn(player);
		if (worn == null) {
			return;
		}
		switch (worn.wearStyle) {
			case BLAZE -> {
				if (!wasHeld) HeadSounds.play(player, SoundEvents.ITEM_FIRECHARGE_USE, 0.4F, 0.95F);
			}
			case CHARGED_CREEPER -> lightning(player, state);
			case CREEPER -> creeperAmmo(player, state);
			case GOAT -> GoatCharge.toggle(player);
			case RABBIT -> RabbitMovement.reset(player);
			case BEE -> BeeCommand.command(player, rayEntity(player, 24));
			case FROG -> grab(player, state);
			case LLAMA -> spit(player, state);
			default -> {
			}
		}
	}

	private static void lightning(ServerPlayerEntity player, PlayerHeadState state) {
		int needed = HeadRules.lightningChargeNeeded(player.getWorld().isThundering());
		if (state.lightningCharge < needed) {
			return;
		}
		LivingEntity target = rayEntity(player, 16);
		if (target == null || target == player) {
			return;
		}
		HeadEffects.line(player.getServerWorld(), player.getEyePos(), target.getEyePos(), net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK);
		HeadSounds.play(player, SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), 0.35F, 1.6F);
		state.lightningCharge = 0;
		HeadMastery.practice(player, HeadType.CHARGED_CREEPER);
		ServerWorld world = player.getServerWorld();
		var bolt = net.minecraft.entity.EntityType.LIGHTNING_BOLT.create(world);
		if (bolt != null) {
			bolt.refreshPositionAfterTeleport(target.getX(), target.getY(), target.getZ());
			world.spawnEntity(bolt);
		}
	}

	private static void creeperAmmo(ServerPlayerEntity player, PlayerHeadState state) {
		ServerWorld world = player.getServerWorld();
		List<HeadAmmoEntity> live = world.getEntitiesByClass(
			HeadAmmoEntity.class,
			player.getBoundingBox().expand(64),
			ammo -> player.getUuid().equals(ammo.ownerId())
		);
		if (!live.isEmpty()) {
			live.forEach(HeadAmmoEntity::detonate);
			return;
		}
		if (state.creeperCharges <= 0) return;
		HeadAmmoEntity ammo = new HeadAmmoEntity(world, player);
		Vec3d look = player.getRotationVec(1.0F);
		ammo.setPosition(player.getX(), player.getEyeY() - 0.1, player.getZ());
		ammo.setVelocity(look.x, look.y, look.z, 1.2F, 1.0F);
		world.spawnEntity(ammo);
		HeadSounds.play(player, SoundEvents.ENTITY_SNOWBALL_THROW, 0.35F, 0.65F);
		state.creeperCharges--;
		state.lastAmmo = ammo.getUuid();
	}

	private static void spit(ServerPlayerEntity player, PlayerHeadState state) {
		if (state.llamaCooldown > 0) {
			return;
		}
		state.llamaCooldown = HeadRules.LLAMA_COOLDOWN_TICKS;
		ServerWorld world = player.getServerWorld();
		var spit = net.minecraft.entity.EntityType.LLAMA_SPIT.create(world);
		if (spit == null) {
			return;
		}
		spit.setOwner(player);
		Vec3d look = player.getRotationVec(1.0F);
		spit.setPosition(player.getX(), player.getEyeY() - 0.1, player.getZ());
		spit.setVelocity(look.x, look.y, look.z, 1.5F, 1.0F);
		world.spawnEntity(spit);
		HeadSounds.play(player, SoundEvents.ENTITY_LLAMA_SPIT, 0.35F, 1.15F);
	}

	private static void grab(ServerPlayerEntity player, PlayerHeadState state) {
		if (state.frogCooldown > 0 || !player.getServerWorld().getEntitiesByClass(
			FrogTongueEntity.class, player.getBoundingBox().expand(32),
			tongue -> tongue.owner() == player).isEmpty()) return;
		HitResult hit = rayAnything(player, HeadRules.FROG_RANGE);
		if (hit.getType() == HitResult.Type.MISS) return;
		state.frogCooldown = FrogTongueEntity.DURATION
			+ (HeadMastery.mastered(player, HeadType.FROG) ? 10 : HeadRules.FROG_COOLDOWN_TICKS);
		player.getServerWorld().spawnEntity(new FrogTongueEntity(player, hit));
		HeadSounds.play(player, SoundEvents.ENTITY_FROG_TONGUE, 1.6F, 1.1F);
		HeadSounds.play(player, SoundEvents.ENTITY_SNOWBALL_THROW, 0.3F, 1.35F);
	}

	private static LivingEntity rayEntity(PlayerEntity player, double range) {
		HitResult hit = rayAnything(player, range);
		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living) {
			return living;
		}
		return null;
	}

	private static HitResult rayAnything(PlayerEntity player, double range) {
		Vec3d start = player.getEyePos();
		Vec3d end = start.add(player.getRotationVec(1.0F).multiply(range));
		HitResult block = player.getWorld().raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		EntityHitResult entity = rayEntities(player, start, block.getType() == HitResult.Type.MISS ? end : block.getPos());
		if (entity != null) {
			double entityDist = entity.getPos().squaredDistanceTo(start);
			double blockDist = block.getPos().squaredDistanceTo(start);
			if (block.getType() == HitResult.Type.MISS || entityDist < blockDist) {
				return entity;
			}
		}
		return block;
	}

	private static EntityHitResult rayEntities(PlayerEntity player, Vec3d start, Vec3d end) {
		Box box = player.getBoundingBox().stretch(end.subtract(start)).expand(1.0);
		EntityHitResult best = null;
		double bestDist = Double.MAX_VALUE;
		for (Entity entity : player.getWorld().getOtherEntities(player, box)) {
			if (entity.isSpectator() || entity instanceof FrogTongueEntity) continue;
			if (HeadLookups.worn(player) == HeadType.BEE && entity instanceof net.minecraft.entity.passive.BeeEntity
				&& entity.getCommandTags().contains("mhf_owner:" + player.getUuid())) continue;
			if (entity instanceof LivingEntity living && HeadlessAccess.isHeadless(living)) {
				// still grabbable
			}
			var optional = entity.getBoundingBox().expand(0.15).raycast(start, end);
			if (optional.isEmpty()) {
				continue;
			}
			double dist = optional.get().squaredDistanceTo(start);
			if (dist < bestDist) {
				bestDist = dist;
				best = new EntityHitResult(entity, optional.get());
			}
		}
		return best;
	}

}
