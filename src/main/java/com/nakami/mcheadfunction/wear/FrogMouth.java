package com.nakami.mcheadfunction.wear;

import com.nakami.mcheadfunction.entity.FrogTongueEntity;
import com.nakami.mcheadfunction.haunt.HauntMeal;
import com.nakami.mcheadfunction.head.FrogMouthAccess;
import com.nakami.mcheadfunction.head.HeadLookups;
import com.nakami.mcheadfunction.head.HeadSounds;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.head.PlayerHeadAccess;
import com.nakami.mcheadfunction.mixin.CreeperFuseAccessor;
import com.nakami.mcheadfunction.progression.HeadMastery;
import com.nakami.mcheadfunction.rule.HeadRules;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

/** A tongued mob rides hidden in the frog wearer's swollen head: chewed every half second, digested on death, or spat out with R. */
public final class FrogMouth {
	private record Launch(UUID owner, int ticks) { }
	private static final Map<CreeperEntity, Launch> LAUNCHED = new WeakHashMap<>();

	private FrogMouth() {
	}

	public static LivingEntity held(LivingEntity holder) {
		int id = ((FrogMouthAccess) holder).mhf$mouthHeld();
		// Ids are per world: after a dimension change the same id can name an unrelated mob.
		return id >= 0 && holder.getWorld().getEntityById(id) instanceof LivingEntity held
			&& ((FrogMouthAccess) held).mhf$mouthHolder() == holder.getId() ? held : null;
	}

	public static boolean isHeld(Entity entity) {
		return entity instanceof FrogMouthAccess access && access.mhf$mouthHolder() >= 0;
	}

	/** Players and bosses are only pulled; other mobs fit in the mouth regardless of size. */
	public static boolean canSwallow(ServerPlayerEntity player, Entity target) {
		return target instanceof MobEntity mob && mob.isAlive() && !HeadLookups.isBoss(mob) && !isHeld(mob) && held(player) == null && !FrogProjectile.occupied(player);
	}

	public static void swallow(ServerPlayerEntity player, MobEntity mob) {
		mob.stopRiding();
		mob.removeAllPassengers();
		((FrogMouthAccess) mob).mhf$setMouthHolder(player.getId());
		((FrogMouthAccess) player).mhf$setMouthHeld(mob.getId());
		PlayerHeadAccess.state(player).mouthTicks = 0;
		conceal(player, mob);
		if (mob instanceof CreeperEntity creeper && !creeper.isIgnited()) {
			((CreeperFuseAccessor) creeper).mhf$setFuseTime(HeadRules.FROG_CREEPER_FUSE_TICKS);
			creeper.ignite();
		}
		HeadSounds.play(player, SoundEvents.ENTITY_FROG_EAT, 0.9F, 0.75F);
		HeadSounds.play(player, SoundEvents.ENTITY_GENERIC_EAT, 0.6F, 0.7F);
	}

	public static void tick(ServerPlayerEntity player) {
		FrogProjectile.tick(player);
		FrogMouthAccess access = (FrogMouthAccess) player;
		if (access.mhf$mouthHeld() < 0) return;
		LivingEntity mob = held(player);
		// A creeper that blew up, or a mob left behind in another dimension, simply empties the mouth.
		if (mob == null || !mob.isAlive()) {
			access.mhf$setMouthHeld(-1);
			if (mob != null) clear(player, mob);
			return;
		}
		if (!player.isAlive() || player.isSpectator() || HeadLookups.worn(player) != HeadType.FROG) {
			release(player, mob, Vec3d.ZERO);
			return;
		}
		conceal(player, mob);
		int mouthTicks = ++PlayerHeadAccess.state(player).mouthTicks;
		if (HeadRules.frogChewsThisTick(mouthTicks)) chew(player, mob);
		else if (mouthTicks % 4 == 0) drool(player);
	}

	/** R while holding: spit the mob along the view. Returns false when the mouth is empty. */
	public static boolean spit(ServerPlayerEntity player) {
		if (FrogProjectile.spit(player)) return true;
		LivingEntity mob = held(player);
		if (mob == null) return false;
		Vec3d look = player.getRotationVec(1.0F);
		release(player, mob, look.multiply(HeadRules.FROG_SPIT_SPEED).add(0, HeadRules.FROG_SPIT_LIFT, 0));
		if (mob instanceof CreeperEntity creeper) LAUNCHED.put(creeper, new Launch(player.getUuid(), 0));
		PlayerHeadAccess.state(player).frogCooldown = HeadRules.FROG_SPIT_COOLDOWN_TICKS;
		HeadSounds.play(player, SoundEvents.ENTITY_LLAMA_SPIT, 0.9F, 0.7F);
		HeadSounds.play(player, SoundEvents.ENTITY_FROG_TONGUE, 1.2F, 0.8F);
		return true;
	}

	/** Keep a held mob parked inside the wearer on both sides; drop a stale link; detonate spat creepers. */
	public static void tickMob(LivingEntity mob) {
		FrogMouthAccess access = (FrogMouthAccess) mob;
		int holderId = access.mhf$mouthHolder();
		if (holderId >= 0) {
			Entity holder = mob.getWorld().getEntityById(holderId);
			if (holder instanceof LivingEntity living && ((FrogMouthAccess) living).mhf$mouthHeld() == mob.getId()) {
				conceal(holder, mob);
			} else if (!mob.getWorld().isClient()) {
				access.mhf$setMouthHolder(-1);
				mob.noClip = false;
			}
		}
		if (mob instanceof CreeperEntity creeper && LAUNCHED.containsKey(creeper)) fly(creeper);
	}

	private static void fly(CreeperEntity creeper) {
		Launch launch = LAUNCHED.get(creeper);
		int ticks = launch.ticks() + 1;
		if (!creeper.isAlive() || ticks > HeadRules.FROG_CREEPER_FLIGHT_TICKS) {
			LAUNCHED.remove(creeper);
			return;
		}
		LAUNCHED.put(creeper, new Launch(launch.owner(), ticks));
		boolean hit = creeper.isOnGround() || creeper.horizontalCollision || creeper.isTouchingWater()
			|| !creeper.getWorld().getOtherEntities(creeper, creeper.getBoundingBox().expand(0.2), e -> e instanceof LivingEntity
				&& e.isAlive() && !e.isSpectator() && !e.getUuid().equals(launch.owner())).isEmpty();
		if (ticks > 2 && hit) {
			LAUNCHED.remove(creeper);
			((CreeperFuseAccessor) creeper).mhf$explode();
		}
	}

	/** Park inside the wearer with an empty box so nothing sits in front of the camera or shoves anyone. */
	private static void conceal(Entity holder, LivingEntity mob) {
		mob.noClip = true;
		mob.setVelocity(Vec3d.ZERO);
		mob.fallDistance = 0;
		mob.setPosition(holder.getX(), holder.getY(), holder.getZ());
		Vec3d pos = holder.getPos();
		mob.setBoundingBox(new Box(pos.x, pos.y, pos.z, pos.x, pos.y, pos.z));
	}

	private static void chew(ServerPlayerEntity player, LivingEntity mob) {
		ServerWorld world = player.getServerWorld();
		mob.timeUntilRegen = 0;
		// A real player kill: kill credit and player-only loot apply, but drops are captured instead of spawned.
		var drops = HauntMeal.capture(mob, () -> mob.damage(world.getDamageSources().playerAttack(player), HeadRules.FROG_MOUTH_DAMAGE));
		HeadSounds.play(player, SoundEvents.ENTITY_GENERIC_EAT, 0.55F, 0.75F);
		HeadSounds.play(player, SoundEvents.ENTITY_SLIME_SQUISH_SMALL, 0.3F, 1.15F);
		crumbs(world, player, mob, 10, 0.14);
		if (mob.isAlive()) return;
		drops.forEach(player.getInventory()::offerOrDrop);
		if (mob.shouldDropXp() && world.getGameRules().getBoolean(GameRules.DO_MOB_LOOT)) {
			ExperienceOrbEntity.spawn(world, player.getPos(), mob.getXpToDrop(world, player));
		}
		Vec3d mouth = FrogTongueEntity.mouth(player, 1);
		world.spawnParticles(ParticleTypes.POOF, mouth.x, mouth.y, mouth.z, 8, 0.2, 0.15, 0.2, 0.02);
		HeadSounds.play(player, SoundEvents.ENTITY_PLAYER_BURP, 0.8F, 0.9F);
		HeadMastery.practice(player, HeadType.FROG);
		clear(player, mob);
		mob.discard();
	}

	private static void drool(ServerPlayerEntity player) {
		Vec3d mouth = FrogTongueEntity.mouth(player, 1);
		player.getServerWorld().spawnParticles(ParticleTypes.FALLING_HONEY, mouth.x, mouth.y - 0.05, mouth.z, 1, 0.04, 0.02, 0.04, 0);
	}

	private static void crumbs(ServerWorld world, ServerPlayerEntity player, LivingEntity mob, int count, double speed) {
		Vec3d mouth = FrogTongueEntity.mouth(player, 1);
		world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, crumb(mob)),
			mouth.x, mouth.y, mouth.z, count, 0.12, 0.08, 0.12, speed);
	}

	private static ItemStack crumb(LivingEntity mob) {
		SpawnEggItem egg = SpawnEggItem.forEntity(mob.getType());
		return new ItemStack(egg != null ? egg : Items.SLIME_BALL);
	}

	private static void release(ServerPlayerEntity player, LivingEntity mob, Vec3d velocity) {
		clear(player, mob);
		Vec3d forward = player.getRotationVec(1.0F).multiply(1, 0, 1);
		if (forward.lengthSquared() < 1.0E-4) forward = Vec3d.ZERO; else forward = forward.normalize();
		Vec3d mouth = FrogTongueEntity.mouth(player, 1);
		Vec3d spot = mouth.add(forward.multiply(mob.getWidth() / 2 + 0.3)).subtract(0, mob.getHeight() / 2, 0);
		// Never push a large mob into a wall in front of the wearer; fall back to the wearer's feet.
		if (!player.getWorld().isSpaceEmpty(mob, mob.getDimensions(mob.getPose()).getBoxAt(spot))) spot = player.getPos();
		mob.setPosition(spot);
		mob.setVelocity(velocity);
		mob.velocityModified = true;
		mob.fallDistance = 0;
	}

	private static void clear(ServerPlayerEntity player, LivingEntity mob) {
		((FrogMouthAccess) player).mhf$setMouthHeld(-1);
		((FrogMouthAccess) mob).mhf$setMouthHolder(-1);
		mob.noClip = false;
	}
}
