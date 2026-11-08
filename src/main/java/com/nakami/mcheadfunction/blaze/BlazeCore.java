package com.nakami.mcheadfunction.blaze;

import com.nakami.mcheadfunction.head.HeadSounds;
import com.nakami.mcheadfunction.head.PlayerHeadAccess;
import com.nakami.mcheadfunction.net.BlazeFlameS2CPayload;
import com.nakami.mcheadfunction.net.BlazeFlameS2CPayload.Kind;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

/** Server-owned charging and swept flight. A core is skill ammo, never a recoverable head. */
public final class BlazeCore {
	public static final int START_HEAT = 60, CHARGE_TICKS = 30;
	public static final int POST_FIRE_COOLDOWN = 160;
	public static final double BLAST_RADIUS = 10, FULL_DAMAGE_RADIUS = 4;
	public static final float CENTER_DAMAGE = 30, EDGE_DAMAGE = 16;
	private static final double SPEED = 1.6, RANGE = 28;
	private static final Map<ServerWorld, List<Shot>> SHOTS = new IdentityHashMap<>();
	private BlazeCore() { }
	private static final class Shot {
		final UUID owner;
		final Vec3d direction;
		Vec3d pos;
		double travelled;
		Shot(ServerPlayerEntity player) {
			owner = player.getUuid(); direction = player.getRotationVec(1);
			pos = player.getEyePos().add(0, -0.1, 0);
		}
	}
	static void register() {
		ServerTickEvents.END_WORLD_TICK.register(BlazeCore::tickWorld);
		ServerWorldEvents.UNLOAD.register((server, world) -> SHOTS.remove(world));
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			PlayerHeadAccess.state(newPlayer).blazeCooldown = PlayerHeadAccess.state(oldPlayer).blazeCooldown;
		});
	}
	static void begin(ServerPlayerEntity player) {
		var state = PlayerHeadAccess.state(player);
		if (!BlazeFire.canUse(player) || state.blazeCooldown > 0) return;
		state.blazeNeedsRelease = true;
		state.blazeSpraying = false;
		state.blazeCoreCharging = true; state.blazeCoreCharge = 0;
		HeadSounds.play(player, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7F, 0.6F);
	}
	/** Returns true while the core owns the held skill input, including a cancelled hold. */
	static boolean tickPlayer(ServerPlayerEntity player) {
		var state = PlayerHeadAccess.state(player);
		if (!state.blazeCoreCharging) return false;
		if (!BlazeFire.canUse(player) || !state.skillHeld) {
			cancel(player); return true;
		}
		int previous = state.blazeCoreCharge;
		state.blazeCoreCharge = Math.min(CHARGE_TICKS, previous + 1);
		state.blazeHeat = BlazeFire.HIGH_HEAT + state.blazeCoreCharge * 39 / CHARGE_TICKS;
		Vec3d look = player.getRotationVec(1);
		Vec3d mouth = player.getEyePos().add(look.multiply(1.05)).add(0, -0.12, 0);
		BlazeFire.emit(player.getServerWorld(), new BlazeFlameS2CPayload(player.getId(), mouth, look,
			state.blazeCoreCharge / (float) CHARGE_TICKS, state.blazeCoreCharge, Kind.CORE_CHARGE));
		if (state.blazeCoreCharge % 10 == 0 && state.blazeCoreCharge < CHARGE_TICKS)
			HeadSounds.play(player, SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value(), 0.6F, 0.65F + state.blazeCoreCharge / 40F);
		if (previous < CHARGE_TICKS && state.blazeCoreCharge == CHARGE_TICKS)
			HeadSounds.play(player, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.9F, 0.75F);
		if (state.blazeCoreCharge == CHARGE_TICKS && player.age % 10 == 0)
			HeadSounds.play(player, SoundEvents.BLOCK_BEACON_AMBIENT, 0.18F, 1.5F);
		return true;
	}
	static boolean release(ServerPlayerEntity player) {
		var state = PlayerHeadAccess.state(player);
		if (!state.blazeCoreCharging) return false;
		boolean usable = BlazeFire.canUse(player);
		boolean fire = usable && state.blazeCoreCharge >= CHARGE_TICKS;
		cancel(player);
		if (!fire) {
			// A longer hold must not turn a ready surge into an empty release.
			if (usable) { state.blazeHeat = START_HEAT - 1; state.blazeSpraying = true; return false; }
			return true;
		}
		Shot shot = new Shot(player);
		SHOTS.computeIfAbsent(player.getServerWorld(), key -> new ArrayList<>()).add(shot);
		BlazeFire.emit(player.getServerWorld(), new BlazeFlameS2CPayload(player.getId(), shot.pos, shot.direction, 1, 80, Kind.CORE_LAUNCH));
		HeadSounds.play(player, SoundEvents.ITEM_FIRECHARGE_USE, 1.3F, 0.5F);
		HeadSounds.play(player, SoundEvents.ENTITY_BLAZE_SHOOT, 1.3F, 0.65F);
		BlazeFire.overheat(player, POST_FIRE_COOLDOWN);
		return true;
	}
	private static void cancel(ServerPlayerEntity player) {
		var state = PlayerHeadAccess.state(player);
		state.blazeCoreCharging = false; state.blazeCoreCharge = 0;
		state.blazeHeat = 0; state.blazeSpraying = false; state.blazeNeedsRelease = true;
		BlazeFire.emit(player.getServerWorld(), new BlazeFlameS2CPayload(player.getId(), player.getEyePos(), player.getRotationVec(1), 0, 0, Kind.CORE_FIZZLE));
	}
	private static void tickWorld(ServerWorld world) {
		var shots = SHOTS.get(world);
		if (shots == null) return;
		for (var iterator = shots.iterator(); iterator.hasNext();) {
			Shot shot = iterator.next();
			var player = world.getServer().getPlayerManager().getPlayer(shot.owner);
			if (player == null || player.getWorld() != world || !player.isAlive()) { iterator.remove(); continue; }
			Vec3d end = shot.pos.add(shot.direction.multiply(Math.min(SPEED, RANGE - shot.travelled)));
			if (!world.isChunkLoaded(BlockPos.ofFloored(end))) {
				fizzle(world, player, shot); iterator.remove(); continue;
			}
			var block = world.raycast(new RaycastContext(shot.pos, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, player));
			Vec3d contact = block.getType() == HitResult.Type.MISS ? end : block.getPos();
			boolean entityHit = false;
			double nearest = contact.squaredDistanceTo(shot.pos);
			for (var target : world.getEntitiesByClass(LivingEntity.class, new Box(shot.pos, end).expand(0.7), e -> e != player && e.isAlive() && !e.isSpectator())) {
				var hit = target.getBoundingBox().expand(0.35).raycast(shot.pos, contact);
				if (target.getBoundingBox().expand(0.35).contains(shot.pos)) hit = Optional.of(shot.pos);
				if (hit.isPresent() && hit.get().squaredDistanceTo(shot.pos) < nearest) {
					nearest = hit.get().squaredDistanceTo(shot.pos); contact = hit.get(); entityHit = true;
				}
			}
			if (entityHit || block.getType() != HitResult.Type.MISS) {
				if (!entityHit && !world.getFluidState(block.getBlockPos()).isEmpty()) fizzle(world, player, shot);
				else BlazeFire.coreBlast(player, entityHit ? contact : contact.add(Vec3d.of(block.getSide().getVector()).multiply(0.06)), shot.direction);
				iterator.remove(); continue;
			}
			shot.travelled += end.distanceTo(shot.pos); shot.pos = end;
			BlazeFire.emit(world, new BlazeFlameS2CPayload(player.getId(), shot.pos, shot.direction, (float) SPEED, 80, Kind.CORE_FLY));
			if (shot.travelled >= RANGE - 0.001) { BlazeFire.coreBlast(player, shot.pos, shot.direction); iterator.remove(); }
		}
		if (shots.isEmpty()) SHOTS.remove(world);
	}
	private static void fizzle(ServerWorld world, ServerPlayerEntity player, Shot shot) {
		BlazeFire.emit(world, new BlazeFlameS2CPayload(player.getId(), shot.pos, shot.direction, 0, 0, Kind.CORE_FIZZLE));
		world.spawnParticles(ParticleTypes.SMOKE, shot.pos.x, shot.pos.y, shot.pos.z, 12, 0.25, 0.25, 0.25, 0.02);
	}
}
