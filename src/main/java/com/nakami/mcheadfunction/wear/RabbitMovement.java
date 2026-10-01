package com.nakami.mcheadfunction.wear;

import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.progression.HeadMastery;
import com.nakami.mcheadfunction.rule.HeadDepthRules;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import java.util.Set;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

public final class RabbitMovement {
	public static final Identifier SPEED = Identifier.of("mc_head_function", "rabbit_momentum");
	private RabbitMovement() { }
	public static void requestJump(ServerPlayerEntity player) {
		// Jump input precedes that tick's movement packet; consume it after the server sees landing.
		if (HeadLookups.worn(player) == HeadType.RABBIT && !GoatCharge.isStunned(player)) PlayerHeadAccess.state(player).rabbitJumpRequestTicks = 3;
	}
	public static void jump(ServerPlayerEntity player) {
		if (HeadLookups.worn(player) != HeadType.RABBIT || !player.isOnGround() || player.hasVehicle()
			|| player.isTouchingWater() || player.getAbilities().flying || !player.isAlive() || GoatCharge.isStunned(player)) return;
		var state = PlayerHeadAccess.state(player);
		long now = player.getWorld().getTime();
		if (state.rabbitAirborne) {
			state.rabbitAirborne = false;
			state.rabbitLandedAt = now;
		}
		state.rabbitJumpRequestTicks = 0;
		int window = HeadMastery.mastered(player, HeadType.RABBIT) ? 8 : HeadDepthRules.RABBIT_WINDOW;
		state.rabbitChain = now - state.rabbitLandedAt <= window ? Math.min(HeadDepthRules.RABBIT_MAX_CHAIN, state.rabbitChain + 1) : 1;
		state.rabbitAirborne = true;
		state.rabbitFallProtected = true;
		state.rabbitLaunchY = player.getY();
		player.setVelocity(player.getVelocity().x, HeadDepthRules.rabbitJumpVelocity(state.rabbitChain), player.getVelocity().z);
		player.setOnGround(false);
		player.velocityModified = false;
		var impulse = player.getVelocity();
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new com.nakami.mcheadfunction.net.RabbitLeapS2CPayload(impulse.y));
		player.fallDistance = 0;
		updateSpeed(player);
		HeadSounds.play(player, SoundEvents.ENTITY_RABBIT_JUMP, 0.6F, 0.8F + state.rabbitChain * 0.05F);
		player.getServerWorld().spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 12, 0.35, 0.05, 0.35, 0.04);
		HeadMastery.practice(player, HeadType.RABBIT);
		if (state.rabbitChain == HeadDepthRules.RABBIT_MAX_CHAIN) HeadMastery.challenge(player, "rabbit_sky");
	}
	public static void tick(ServerPlayerEntity player) {
		var state = PlayerHeadAccess.state(player);
		long now = player.getWorld().getTime();
		if (state.rabbitFallProtected) player.fallDistance = 0;
		if (state.rabbitAirborne && player.isOnGround()) {
			state.rabbitAirborne = false;
			state.rabbitLandedAt = now;
			state.rabbitFallProtected = false;
		}
		if (state.rabbitAirborne && player.getY() > state.rabbitLaunchY + HeadDepthRules.RABBIT_MAX_HEIGHT) {
			// Correct only height: absolute X/Z teleports also erase client momentum.
			// This API takes absolute targets and computes relative packet offsets itself.
			player.networkHandler.requestTeleport(player.getX(), state.rabbitLaunchY + HeadDepthRules.RABBIT_MAX_HEIGHT, player.getZ(), player.getYaw(), player.getPitch(),
				Set.of(PositionFlag.X, PositionFlag.Z, PositionFlag.X_ROT, PositionFlag.Y_ROT));
			player.setVelocity(player.getVelocity().multiply(1, 0, 1));
			player.velocityModified = false;
		}
		int window = HeadMastery.mastered(player, HeadType.RABBIT) ? 8 : HeadDepthRules.RABBIT_WINDOW;
		if (HeadLookups.worn(player) != HeadType.RABBIT || player.hasVehicle() || player.isTouchingWater()
			|| player.getAbilities().flying || (!state.rabbitAirborne && now - state.rabbitLandedAt > window)) state.rabbitChain = 0;
		if (state.rabbitJumpRequestTicks > 0) {
			state.rabbitJumpRequestTicks--;
			if (player.isOnGround()) jump(player);
		}
		updateSpeed(player);
	}
	public static void reset(ServerPlayerEntity player) {
		PlayerHeadAccess.state(player).rabbitChain = 0;
		PlayerHeadAccess.state(player).rabbitLandedAt = -100;
		updateSpeed(player);
	}
	private static void updateSpeed(ServerPlayerEntity player) {
		var speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed == null) return;
		int chain = PlayerHeadAccess.state(player).rabbitChain;
		double bonus = HeadDepthRules.rabbitSpeedMultiplier(chain) - 1;
		var existing = speed.getModifier(SPEED);
		if (existing != null && existing.value() == bonus) return;
		speed.removeModifier(SPEED);
		if (bonus > 0) speed.addTemporaryModifier(new EntityAttributeModifier(SPEED, bonus, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}
}
