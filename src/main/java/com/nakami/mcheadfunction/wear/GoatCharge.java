package com.nakami.mcheadfunction.wear;

import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.progression.HeadMastery;
import com.nakami.mcheadfunction.rule.HeadDepthRules;
import com.nakami.mcheadfunction.net.GoatEffectsS2CPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import java.util.WeakHashMap;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

public final class GoatCharge {
	/** Seconds, saved with the world; read once when starting each charge. */
	public static final GameRules.Key<GameRules.IntRule> DURATION_SECONDS = GameRuleRegistry.register(
		"goatChargeDurationSeconds", GameRules.Category.PLAYER, GameRuleFactory.createIntRule(HeadDepthRules.GOAT_DURATION / 20, 1, 3600));
	public static final RegistryEntry<StatusEffect> STUN = Registry.registerReference(Registries.STATUS_EFFECT,
		Identifier.of("mc_head_function", "stunned"), new StunEffect());
	private static final Identifier STEP = Identifier.of("mc_head_function", "goat_charge_step");
	private static final WeakHashMap<LivingEntity, Landing> LANDINGS = new WeakHashMap<>();
	private record Landing(long expires, int duration) { }
	private static final class StunEffect extends StatusEffect {
		StunEffect() {
			super(StatusEffectCategory.HARMFUL, 0xF9D66E);
			addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED, Identifier.of("mc_head_function", "stun_speed"), -1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			addAttributeModifier(EntityAttributes.GENERIC_ATTACK_SPEED, Identifier.of("mc_head_function", "stun_attack"), -1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
	}
	private GoatCharge() { }
	public static void register() {
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			PlayerHeadAccess.state(handler.player).goatMining.clear();
			syncStepHeight(handler.player, false);
		});
	}
	public static boolean isStunned(LivingEntity entity) { return entity.getWorld().isClient() ? ((HeadStunAccess) entity).mhf$isStunned() : entity.hasStatusEffect(STUN); }
	public static void toggle(ServerPlayerEntity player) {
		var state = PlayerHeadAccess.state(player);
		if (state.goatDashTicks > 0) return;
		if (state.goatCooldown > 0 || player.hasVehicle() || player.isTouchingWater() || !player.isAlive()) return;
		int durationTicks = player.getServerWorld().getGameRules().getInt(DURATION_SECONDS) * 20;
		state.startGoatDash(player.getPos(), durationTicks);
		state.goatHeading = player.getYaw();
		state.goatTravelHeading = state.goatHeading;
		state.goatPitch = player.getPitch();
		syncStepHeight(player, true);
		syncView(player, true);
		state.goatCooldown = durationTicks + HeadDepthRules.GOAT_COOLDOWN;
		HeadSounds.play(player, SoundEvents.ENTITY_GOAT_PREPARE_RAM, 1F, 0.7F);
	}
	public static void stop(ServerPlayerEntity player) {
		var state = PlayerHeadAccess.state(player);
		state.clearGoatDash();
		syncStepHeight(player, false);
		syncView(player, false);
		state.goatCooldown = HeadDepthRules.GOAT_COOLDOWN;
		setChargeVelocity(player, player.getVelocity().multiply(0.2, 1, 0.2));
	}
	public static void tick(ServerPlayerEntity player) {
		var state = PlayerHeadAccess.state(player);
		if (state.goatDashTicks <= 0) return;
		if (HeadLookups.worn(player) != HeadType.GOAT || player.hasVehicle() || player.isTouchingWater() || isStunned(player) || !player.isAlive() || player.isSpectator()) { stop(player); return; }
		player.setSneaking(false);
		if (player.getServerWorld().getTime() >= state.goatSteeringExpires) {
			state.goatSteering = 0;
			state.goatDrifting = false;
		}
		boolean mastered = HeadMastery.mastered(player, HeadType.GOAT);
		float turn = state.goatDrifting
			? (mastered ? HeadDepthRules.GOAT_DRIFT_TURN_MASTER : HeadDepthRules.GOAT_DRIFT_TURN)
			: (mastered ? HeadDepthRules.GOAT_TURN_MASTER : HeadDepthRules.GOAT_TURN);
		state.goatHeading += state.goatSteering * turn;
		player.setYaw(state.goatHeading);
		player.setPitch(state.goatPitch);
		player.setHeadYaw(state.goatHeading);
		syncStepHeight(player, true);
		syncView(player, true);
		state.goatSpeed = Math.min(1.05, state.goatSpeed + 0.025);
		double speed = state.goatDrifting ? state.goatSpeed * HeadDepthRules.GOAT_DRIFT_SPEED : state.goatSpeed;
		float slip = net.minecraft.util.math.MathHelper.wrapDegrees(state.goatHeading - state.goatTravelHeading);
		state.goatTravelHeading += slip * (state.goatDrifting ? HeadDepthRules.GOAT_DRIFT_GRIP : HeadDepthRules.GOAT_RECOVER_GRIP);
		// Small normal turns stay precise; only a drift leaves stored sideways momentum.
		if (!state.goatDrifting && Math.abs(slip) < 4) state.goatTravelHeading = state.goatHeading;
		double angle = Math.toRadians(state.goatTravelHeading);
		Vec3d direction = new Vec3d(-Math.sin(angle), 0, Math.cos(angle));
		if (state.goatMining.tick(player, direction)) return;
		setChargeVelocity(player, new Vec3d(direction.x * speed, player.getVelocity().y, direction.z * speed));
		var world = player.getServerWorld();
		if (player.isOnGround()) {
			effects(player, state.goatDrifting ? GoatEffectsS2CPayload.DRIFT : GoatEffectsS2CPayload.RUN,
				player.getPos(), direction, state.goatSteering);
			if (state.goatDrifting && player.age % 5 == 0) HeadSounds.play(player, SoundEvents.ENTITY_MINECART_RIDING, 0.35F, 1.55F);
			var floor = world.getBlockState(player.getBlockPos().down());
			if (player.age % 3 == 0 && !floor.isAir()) world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, floor), player.getX(), player.getY() + 0.08, player.getZ(), 4, 0.22, 0.06, 0.22, 0.06);
		}
		if (player.age % 8 == 0) HeadSounds.play(player, SoundEvents.ENTITY_HORSE_GALLOP, 0.6F, 0.65F + (float) state.goatSpeed * 0.4F);
		// Sweep the imminent movement as well as the current box, so fast charges cannot skip narrow targets.
		var sweep = player.getBoundingBox().stretch(direction.multiply(speed)).expand(0.35);
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, sweep, e -> e != player && e.isAlive() && !e.isSpectator())) {
			if (state.goatHit.contains(target.getUuid()) || !player.canSee(target)) continue;
			if (!target.damage(player.getDamageSources().playerAttack(player), 4)) continue;
			state.goatHit.add(target.getUuid());
			state.goatDashTicks += HeadDepthRules.GOAT_HIT_EXTENSION;
			state.goatCooldown += HeadDepthRules.GOAT_HIT_EXTENSION;
			if (!HeadLookups.isBoss(target)) {
				target.setVelocity(direction.multiply(0.65).add(0, 0.8, 0));
				target.setOnGround(false);
				target.velocityModified = true;
				LANDINGS.put(target, new Landing(world.getTime() + 200, 40));
			}
			effects(player, GoatEffectsS2CPayload.HIT, target.getPos(), direction, 0);
			ServerPlayNetworking.send(player, new com.nakami.mcheadfunction.net.GoatImpactS2CPayload(6));
			HeadSounds.play(target, SoundEvents.ENTITY_GOAT_RAM_IMPACT, 1F, 0.8F);
			HeadMastery.practice(player, HeadType.GOAT);
			if (state.goatHit.size() >= 3) HeadMastery.challenge(player, "goat_stampede");
		}
	}
	private static void effects(ServerPlayerEntity player, int kind, Vec3d origin, Vec3d direction, int steering) {
		var payload = new GoatEffectsS2CPayload(player.getId(), kind, origin, direction, steering);
		ServerPlayNetworking.send(player, payload);
		for (var observer : PlayerLookup.tracking(player)) ServerPlayNetworking.send(observer, payload);
	}
	public static void steer(ServerPlayerEntity player, int direction) {
		steer(player, direction, false);
	}
	public static void steer(ServerPlayerEntity player, int direction, boolean drift) {
		var state = PlayerHeadAccess.state(player);
		if (state.goatDashTicks <= 0 || direction < -1 || direction > 1) return;
		state.goatSteering = direction;
		state.goatDrifting = drift && direction != 0;
		// Lost focus / input packets must not leave a permanent held turn.
		state.goatSteeringExpires = player.getServerWorld().getTime() + 5;
	}
	private static void syncStepHeight(ServerPlayerEntity player, boolean charging) {
		var instance = player.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT);
		if (instance == null) return;
		instance.removeModifier(STEP);
		if (!charging) return;
		double extra = HeadDepthRules.GOAT_STEP_HEIGHT - instance.getValue();
		if (extra > 0) instance.addTemporaryModifier(new EntityAttributeModifier(STEP, extra, EntityAttributeModifier.Operation.ADD_VALUE));
	}
	private static void syncView(ServerPlayerEntity player, boolean active) {
		var state = PlayerHeadAccess.state(player);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
			new com.nakami.mcheadfunction.net.GoatChargeS2CPayload(active, state.goatHeading, state.goatPitch));
	}
	/** Send before the next entity tick can erase this impulse against the old collision surface. */
	static void setChargeVelocity(ServerPlayerEntity player, Vec3d velocity) {
		player.setVelocity(velocity);
		player.velocityModified = true;
		player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(player));
	}
	/** Called for every living entity, including entities with disabled AI. */
	public static void tickTarget(LivingEntity target) {
		if (!(target.getWorld() instanceof ServerWorld world)) return;
		Landing landing = LANDINGS.get(target);
		if (landing != null) {
			if (!target.isAlive() || world.getTime() >= landing.expires()) LANDINGS.remove(target);
			else if (target.isOnGround() || target.isTouchingWater()) {
				LANDINGS.remove(target);
				target.addStatusEffect(new StatusEffectInstance(STUN, landing.duration(), 0, false, false, true));
				HeadSounds.play(target, SoundEvents.BLOCK_BELL_RESONATE, 0.6F, 1.8F);
			}
		}
		((HeadStunAccess) target).mhf$setStunned(target.hasStatusEffect(STUN));
		if (!isStunned(target)) return;
		target.setVelocity(0, Math.min(0, target.getVelocity().y), 0);
		target.setSprinting(false);
		if (target instanceof MobEntity mob) { mob.getNavigation().stop(); mob.setTarget(null); }
		if (target.age % 3 == 0) for (int i = 0; i < 3; i++) {
			double angle = target.age * 0.25 + i * Math.PI * 2 / 3;
			world.spawnParticles(ParticleTypes.END_ROD, target.getX() + Math.cos(angle) * 0.4,
				target.getBoundingBox().maxY + 0.2, target.getZ() + Math.sin(angle) * 0.4, 1, 0, 0, 0, 0);
		}
	}
}
