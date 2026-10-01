package com.nakami.mcheadfunction.wear;

import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.progression.HeadMastery;
import com.nakami.mcheadfunction.rule.HeadDepthRules;
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

public final class GoatCharge {
	public static final RegistryEntry<StatusEffect> STUN = Registry.registerReference(Registries.STATUS_EFFECT,
		Identifier.of("mc_head_function", "stunned"), new StunEffect());
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
	public static void register() { }
	public static boolean isStunned(LivingEntity entity) { return entity.getWorld().isClient() ? ((HeadStunAccess) entity).mhf$isStunned() : entity.hasStatusEffect(STUN); }
	public static void toggle(ServerPlayerEntity player) {
		var state = PlayerHeadAccess.state(player);
		if (state.goatDashTicks > 0) { stop(player); return; }
		if (state.goatCooldown > 0 || player.hasVehicle() || player.isTouchingWater() || !player.isAlive()) return;
		state.startGoatDash(player.getPos());
		state.goatHeading = player.getYaw();
		state.goatCooldown = HeadDepthRules.GOAT_DURATION + HeadDepthRules.GOAT_COOLDOWN;
		HeadSounds.play(player, SoundEvents.ENTITY_GOAT_PREPARE_RAM, 1F, 0.7F);
	}
	public static void stop(ServerPlayerEntity player) {
		var state = PlayerHeadAccess.state(player);
		state.clearGoatDash();
		state.goatCooldown = HeadDepthRules.GOAT_COOLDOWN;
		player.setVelocity(player.getVelocity().multiply(0.2, 1, 0.2));
		player.velocityModified = true;
	}
	public static void tick(ServerPlayerEntity player) {
		var state = PlayerHeadAccess.state(player);
		if (state.goatDashTicks <= 0) return;
		if (HeadLookups.worn(player) != HeadType.GOAT || player.hasVehicle() || player.isTouchingWater() || isStunned(player)) { stop(player); return; }
		if (player.horizontalCollision) {
			player.getServerWorld().spawnParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.7, player.getZ(), 18, 0.4, 0.3, 0.4, 0.08);
			HeadSounds.play(player, SoundEvents.ENTITY_GOAT_RAM_IMPACT, 1F, 0.65F);
			stop(player); return;
		}
		state.goatHeading = HeadDepthRules.steer(state.goatHeading, player.getYaw(), HeadMastery.mastered(player, HeadType.GOAT) ? 3F : 1.8F);
		state.goatSpeed = Math.min(1.05, state.goatSpeed + 0.025);
		double angle = Math.toRadians(state.goatHeading);
		Vec3d direction = new Vec3d(-Math.sin(angle), 0, Math.cos(angle));
		player.setVelocity(direction.x * state.goatSpeed, player.getVelocity().y, direction.z * state.goatSpeed);
		player.velocityModified = true;
		var world = player.getServerWorld();
		if (player.age % 2 == 0) {
			world.spawnParticles(ParticleTypes.CLOUD, player.getX() - direction.x * 0.4, player.getY() + 0.1, player.getZ() - direction.z * 0.4, 5, 0.22, 0.08, 0.22, 0.02);
			var floor = world.getBlockState(player.getBlockPos().down());
			if (!floor.isAir()) world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, floor), player.getX(), player.getY() + 0.1, player.getZ(), 6, 0.25, 0.1, 0.25, 0.08);
		}
		if (player.age % 8 == 0) HeadSounds.play(player, SoundEvents.ENTITY_HORSE_GALLOP, 0.6F, 0.65F + (float) state.goatSpeed * 0.4F);
		// Sweep the imminent movement as well as the current box, so fast charges cannot skip narrow targets.
		var sweep = player.getBoundingBox().stretch(direction.multiply(state.goatSpeed)).expand(0.35);
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, sweep, e -> e != player && e.isAlive() && !e.isSpectator())) {
			if (state.goatHit.contains(target.getUuid()) || !player.canSee(target)) continue;
			if (!target.damage(player.getDamageSources().playerAttack(player), 4)) continue;
			state.goatHit.add(target.getUuid());
			if (!HeadLookups.isBoss(target)) {
				target.setVelocity(direction.multiply(0.65).add(0, 0.8, 0));
				target.setOnGround(false);
				target.velocityModified = true;
				LANDINGS.put(target, new Landing(world.getTime() + 200, 40));
			}
			world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.6), target.getZ(), 20, 0.35, 0.35, 0.35, 0.2);
			world.spawnParticles(ParticleTypes.POOF, target.getX(), target.getY(), target.getZ(), 12, 0.4, 0.2, 0.4, 0.08);
			HeadSounds.play(target, SoundEvents.ENTITY_GOAT_RAM_IMPACT, 1F, 0.8F);
			HeadMastery.practice(player, HeadType.GOAT);
			if (state.goatHit.size() >= 3) HeadMastery.challenge(player, "goat_stampede");
		}
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
