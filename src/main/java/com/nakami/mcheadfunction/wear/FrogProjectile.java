package com.nakami.mcheadfunction.wear;

import com.nakami.mcheadfunction.entity.FrogTongueEntity;
import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.progression.HeadMastery;
import com.nakami.mcheadfunction.rule.HeadRules;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.projectile.*;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;

/** Projectile mouthfuls are suspended as data, so their vanilla collision/timers cannot run inside a player. */
public final class FrogProjectile {
	public record Mouthful(NbtCompound data, double speed) { }
	public static final int EMPTY = 0, HOT = 1, ARROW = 2, OTHER = 3;
	public static final String RETURNED = "mhf_frog_returned";
	private FrogProjectile() { }
	public static void register() {
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (source.getSource() instanceof ProjectileEntity projectile && projectile.getCommandTags().contains(RETURNED)
				&& source.getAttacker() instanceof ServerPlayerEntity player
				&& victim instanceof Monster
				&& (victim instanceof net.minecraft.entity.ai.RangedAttackMob || victim instanceof GhastEntity
					|| victim instanceof BlazeEntity || victim instanceof BreezeEntity || victim instanceof ShulkerEntity)) {
				HeadMastery.challenge(player, "frog_projectile_parry");
			}
			if (victim instanceof ServerPlayerEntity player) spit(player);
		});
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> spit(handler.player));
	}

	public static int appearance(LivingEntity player) { return ((FrogMouthAccess) player).mhf$mouthAmmo(); }
	public static boolean occupied(LivingEntity player) { return appearance(player) != EMPTY; }
	public static int cooldown(ServerPlayerEntity player) {
		return HeadMastery.mastered(player, HeadType.FROG) ? HeadRules.FROG_PARRY_MASTER_COOLDOWN : HeadRules.FROG_PARRY_COOLDOWN;
	}
	public static boolean supported(Entity e) {
		return e instanceof FireballEntity || e instanceof SmallFireballEntity || e instanceof WitherSkullEntity
			|| e instanceof net.minecraft.entity.projectile.AbstractWindChargeEntity || e instanceof ArrowEntity
			|| e instanceof SpectralArrowEntity || e instanceof TridentEntity || e instanceof ShulkerBulletEntity || e instanceof PotionEntity;
	}
	public static boolean capture(ServerPlayerEntity player, ProjectileEntity projectile) {
		if (!supported(projectile) || projectile.isRemoved() || projectile.isOnGround()
			|| projectile.getVelocity().lengthSquared() < 1.0E-6 || projectile.getOwner() == player
			|| occupied(player) || FrogMouth.held(player) != null) return false;
		NbtCompound data = new NbtCompound();
		if (!projectile.saveNbt(data)) return false;
		if (data.getBoolean("inGround")) return false;
		data.remove("UUID"); data.remove("Owner"); data.putBoolean("LeftOwner", false);
		data.remove("Passengers");
		PlayerHeadState state = PlayerHeadAccess.state(player);
		state.frogAmmo = new Mouthful(data, projectile.getVelocity().length());
		state.mouthTicks = 0;
		state.frogCooldown = cooldown(player);
		((FrogMouthAccess) player).mhf$setMouthAmmo(projectile instanceof FireballEntity || projectile instanceof SmallFireballEntity ? HOT
			: projectile instanceof ArrowEntity || projectile instanceof SpectralArrowEntity ? ARROW : OTHER);
		projectile.discard();
		HeadSounds.play(player, SoundEvents.ENTITY_FROG_EAT, 0.9F, 1.2F);
		HeadMastery.practice(player, HeadType.FROG);
		return true;
	}
	public static void tick(ServerPlayerEntity player) {
		PlayerHeadState state = PlayerHeadAccess.state(player);
		if (state.frogAmmo == null) {
			((FrogMouthAccess) player).mhf$setMouthAmmo(EMPTY);
			return;
		}
		if (!player.isAlive() || player.isSpectator() || player.isRemoved() || HeadLookups.worn(player) != HeadType.FROG) {
			// Interrupted mouthfuls are expelled, never converted into collectible ammunition.
			spit(player); return;
		}
		int ticks = ++state.mouthTicks;
		Vec3d mouth = FrogTongueEntity.mouth(player, 1);
		if (appearance(player) == HOT && ticks % 3 == 0) {
			player.getServerWorld().spawnParticles(ParticleTypes.SMOKE, mouth.x, mouth.y, mouth.z, 2, .12, .06, .12, .01);
			player.getServerWorld().spawnParticles(ParticleTypes.FLAME, mouth.x, mouth.y, mouth.z, 1, .12, .06, .12, .01);
		}
		if (ticks % HeadRules.FROG_AMMO_HURT_INTERVAL == 0) {
			// Vanilla hurt immunity lasts 20 ticks; these deliberately spaced mouth pricks happen every 10.
			player.timeUntilRegen = 0;
			player.damage(appearance(player) == HOT ? player.getDamageSources().onFire() : player.getDamageSources().cactus(), 1);
			HeadSounds.play(player, SoundEvents.ENTITY_PLAYER_HURT, .45F, 1.3F);
		}
		if (ticks >= HeadRules.FROG_AMMO_HOLD_TICKS && state.frogAmmo != null) {
			player.sendMessage(Text.translatable("message.mc_head_function.frog_sneeze"), true);
			HeadSounds.play(player, SoundEvents.ENTITY_PANDA_SNEEZE, 1, 1.2F);
			spit(player);
		}
	}
	public static boolean spit(ServerPlayerEntity player) {
		var state = PlayerHeadAccess.state(player);
		Mouthful ammo = state.frogAmmo;
		if (ammo == null) return false;
		Entity loaded = EntityType.loadEntityWithPassengers(ammo.data().copy(), player.getServerWorld(), entity -> entity);
		if (!(loaded instanceof ProjectileEntity projectile)) return false;
		if (projectile instanceof ShulkerBulletEntity) {
			LivingEntity target = player.getServerWorld().getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(32),
				e -> e instanceof Monster && e.isAlive() && player.canSee(e) && player.getRotationVec(1).dotProduct(e.getEyePos().subtract(player.getEyePos()).normalize()) > .5)
				.stream().min(java.util.Comparator.comparingDouble(player::squaredDistanceTo)).orElse(null);
			projectile = new ShulkerBulletEntity(player.getWorld(), player, target, Direction.Axis.Y);
		}
		projectile.setOwner(player);
		projectile.addCommandTag(RETURNED);
		projectile.setPosition(FrogTongueEntity.mouth(player, 1));
		Vec3d direction = player.getRotationVec(1);
		projectile.setVelocity(direction.x, direction.y, direction.z, (float) (ammo.speed() * HeadRules.FROG_AMMO_SPEED_MULTIPLIER), 0);
		projectile.setNoGravity(true);
		if (projectile instanceof PersistentProjectileEntity arrow) {
			arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
			if (!(arrow instanceof TridentEntity)) { arrow.setDamage(8); arrow.setCritical(false); }
		}
		if (!player.getServerWorld().spawnEntity(projectile)) return false;
		state.frogAmmo = null;
		((FrogMouthAccess) player).mhf$setMouthAmmo(EMPTY);
		state.frogCooldown = cooldown(player);
		HeadSounds.play(player, SoundEvents.ENTITY_LLAMA_SPIT, .9F, 1.2F);
		return true;
	}
}
