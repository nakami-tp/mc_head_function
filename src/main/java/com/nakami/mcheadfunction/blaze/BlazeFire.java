package com.nakami.mcheadfunction.blaze;

import com.nakami.mcheadfunction.McHeadFunction;
import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.progression.HeadMastery;
import com.nakami.mcheadfunction.wear.GoatCharge;
import com.nakami.mcheadfunction.net.BlazeFlameS2CPayload;
import com.nakami.mcheadfunction.net.BlazeFlameS2CPayload.Kind;
import com.nakami.mcheadfunction.net.BlazeLookS2CPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

/** Owns temporary fire ground, heat and waves. All combat and lifetime decisions stay on the server. */
public final class BlazeFire {
	public static final int HIGH_HEAT = 40, MAX_HEAT = 80, COOLDOWN = 40;
	private static final int TRAIL_LIFETIME = 240;
	private static final Map<ServerWorld, Field> FIELDS = new IdentityHashMap<>();
	private static final DustParticleEffect EMBER = new DustParticleEffect(new Vector3f(0.65F, 0.12F, 0.02F), 0.9F);
	private static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1, 0.65F, 0.08F), 1.1F);
	private record Tile(UUID owner, BlockPos pos) { }
	private record Ground(Vec3d center, long expires) { }
	private record Front(Vec3d center, long at, boolean pillar) { }
	private static final class Wave {
		final UUID owner;
		final Vec3d origin, forward;
		final float charge;
		final double range;
		final List<Front> fronts = new ArrayList<>();
		final Set<UUID> hit = new HashSet<>();
		long lastRoar = Long.MIN_VALUE;
		Wave(ServerPlayerEntity player) {
			owner = player.getUuid(); origin = player.getEyePos(); forward = player.getRotationVec(1);
			charge = highHeatCharge(PlayerHeadAccess.state(player).blazeHeat);
			range = 10 + charge * 2;
		}
		Wave(ServerPlayerEntity player, Vec3d blast, Vec3d direction) {
			owner = player.getUuid(); origin = blast; forward = direction; charge = 1; range = 0;
		}
	}
	private static final class Field {
		final Map<Tile, Ground> ground = new LinkedHashMap<>();
		final List<Wave> waves = new ArrayList<>();
	}
	private BlazeFire() { }
	public static void register() {
		BlazeCore.register();
		ServerTickEvents.END_WORLD_TICK.register(BlazeFire::tickField);
		ServerWorldEvents.UNLOAD.register((server, world) -> FIELDS.remove(world));
	}

	public static void tickPlayer(ServerPlayerEntity p) {
		var s = PlayerHeadAccess.state(p);
		if (BlazeCore.tickPlayer(p)) { syncLook(p); return; }
		if (s.blazeCooldown > 0) s.blazeCooldown--;
		if (!canUse(p)) {
			s.blazeHeat = 0; s.blazeSpraying = false;
			if (s.skillHeld) s.blazeNeedsRelease = true;
			syncLook(p);
			return;
		}
		if (!s.skillHeld) s.blazeNeedsRelease = false;
		s.blazeSpraying = s.skillHeld && !s.blazeNeedsRelease && s.blazeCooldown == 0;
		if (!s.blazeSpraying) {
			s.blazeHeat = Math.max(0, s.blazeHeat - 2);
			syncLook(p);
			return;
		}
		s.blazeHeat++;
		if (s.blazeHeat == HIGH_HEAT) HeadSounds.play(p, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.65F, 1.5F);
		if (s.blazeHeat >= BlazeCore.START_HEAT) {
			BlazeCore.begin(p); syncLook(p); return;
		}
		spray(p);
		syncLook(p);
	}
	static void overheat(ServerPlayerEntity player, int duration) {
		var state = PlayerHeadAccess.state(player);
		state.blazeHeat = 0; state.blazeCooldown = duration; state.blazeNeedsRelease = true; state.blazeSpraying = false;
		HeadSounds.play(player, SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.8F, 0.7F);
		player.getServerWorld().spawnParticles(ParticleTypes.SMOKE, player.getX(), player.getEyeY(), player.getZ(), 14, 0.4, 0.3, 0.4, 0.03);
	}
	public static float highHeatCharge(int heat) {
		return MathHelper.clamp((heat - HIGH_HEAT) / (float) (BlazeCore.START_HEAT - HIGH_HEAT - 1), 0, 1);
	}

	private static void syncLook(ServerPlayerEntity p) {
		if (HeadLookups.worn(p) != HeadType.BLAZE || p.age % 2 != 0) return;
		var s = PlayerHeadAccess.state(p);
		var look = new BlazeLookS2CPayload(p.getId(), s.blazeHeat, s.blazeCooldown);
		for (var viewer : PlayerLookup.tracking(p)) ServerPlayNetworking.send(viewer, look);
	}

	public static void release(ServerPlayerEntity p) {
		if (BlazeCore.release(p)) return;
		var s = PlayerHeadAccess.state(p);
		if (canUse(p) && s.blazeSpraying && s.blazeCooldown == 0 && s.blazeHeat >= HIGH_HEAT) {
			launchWave(p); s.blazeHeat = 0;
		}
		s.blazeSpraying = false; s.blazeNeedsRelease = false;
	}
	static boolean canUse(ServerPlayerEntity p) {
		return HeadLookups.worn(p) == HeadType.BLAZE && p.isAlive() && !p.isSpectator()
			&& !p.isTouchingWater() && !GoatCharge.isStunned(p);
	}
	private static double range(ServerPlayerEntity p) { return HeadMastery.mastered(p, HeadType.BLAZE) ? 8 : 6; }

	private static void spray(ServerPlayerEntity p) {
		ServerWorld w = p.getServerWorld();
		Vec3d eye = p.getEyePos(), look = p.getRotationVec(1);
		if (p.age % 10 == 0 || PlayerHeadAccess.state(p).blazeHeat == 1) {
			for (var target : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(range(p)), e -> e != p && e.isAlive())) {
				Vec3d center = target.getBoundingBox().getCenter();
				if (inCone(eye, look, center, range(p), 0.22) && visible(w, p, eye, center)) burn(w, p, target, 2);
			}
		}
		igniteSpray(p, eye, look);
		emit(w, new BlazeFlameS2CPayload(p.getId(), eye, look, (float) range(p), PlayerHeadAccess.state(p).blazeHeat, Kind.BREATH));
		if (p.age % 15 == 0) HeadSounds.play(p, SoundEvents.ENTITY_BLAZE_SHOOT, 0.25F, 0.8F);
	}
	static void emit(ServerWorld world, BlazeFlameS2CPayload pulse) {
		for (var viewer : PlayerLookup.around(world, pulse.origin(), 48)) ServerPlayNetworking.send(viewer, pulse);
	}
	/** Called with the swept projectile segment, clamped to the first wall and 16-block range. */
	public static void trail(ServerWorld w, ServerPlayerEntity owner, Vec3d start, Vec3d end) {
		Field field = FIELDS.computeIfAbsent(w, key -> new Field());
		Vec3d delta = end.subtract(start);
		Vec3d side = new Vec3d(-delta.z, 0, delta.x).normalize().multiply(0.5);
		int steps = Math.max(1, (int) Math.ceil(delta.length() * 3));
		for (int i = 0; i <= steps; i++) {
			Vec3d point = start.lerp(end, (double) i / steps);
			w.spawnParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 3, 0.08, 0.08, 0.08, 0.005);
			for (int lane = -1; lane <= 1; lane++) {
				Vec3d above = point.add(side.multiply(lane));
				if (!visible(w, owner, point, above)) continue;
				var hit = ray(w, owner, above, above.add(0, -3, 0));
				if (hit.getType() != HitResult.Type.BLOCK || hit.getSide() != Direction.UP) continue;
				Vec3d center = hit.getPos().add(0, 0.03, 0);
				BlockPos cell = BlockPos.ofFloored(center);
				if (!w.getFluidState(cell).isEmpty()) continue;
				// Quantized cells merge repeated throws and give a stable, continuous two-block footprint.
				Vec3d tileCenter = new Vec3d(cell.getX() + 0.5, center.y, cell.getZ() + 0.5);
				field.ground.put(new Tile(owner.getUuid(), cell), new Ground(tileCenter, w.getTime() + TRAIL_LIFETIME));
				ignite(w, owner, cell);
				ignite(w, owner, cell.down());
			}
		}
		for (var target : w.getEntitiesByClass(LivingEntity.class, new Box(start, end).expand(0.45), e -> e != owner && e.isAlive())) {
			if (target.getBoundingBox().expand(0.25).raycast(start, end).isPresent()
				&& visible(w, owner, start, target.getBoundingBox().getCenter())) burn(w, owner, target, 2);
		}
	}

	private static void launchWave(ServerPlayerEntity p) {
		ServerWorld w = p.getServerWorld();
		Field field = FIELDS.computeIfAbsent(w, key -> new Field());
		Wave wave = new Wave(p);
		Vec3d eye = wave.origin, look = wave.forward;
		Vec3d side = look.crossProduct(new Vec3d(0, 1, 0)).normalize();
		if (side.lengthSquared() < 0.01) side = new Vec3d(1, 0, 0);
		for (int step = 1; step <= Math.ceil(wave.range); step++) {
			double distance = Math.min(step, wave.range);
			double width = 0.6 + (2.4 + wave.charge * 0.4) * distance / wave.range;
			for (int lane = -3; lane <= 3; lane++) {
				Vec3d center = eye.add(look.multiply(distance)).add(side.multiply(lane * width / 3)).add(0, -0.55, 0);
				if (visible(w, p, eye, center)) wave.fronts.add(new Front(center, w.getTime() + Math.max(1, (int) Math.ceil(distance / wave.range * 6)), false));
			}
		}
		field.waves.add(wave);
		emit(w, new BlazeFlameS2CPayload(p.getId(), eye, look, (float) wave.range, PlayerHeadAccess.state(p).blazeHeat, Kind.BURST));
		HeadSounds.play(p, SoundEvents.ITEM_FIRECHARGE_USE, 1.2F, 0.55F);
		HeadSounds.play(p, SoundEvents.ENTITY_BLAZE_SHOOT, 0.85F, 0.65F);
	}

	/** One authoritative blast and the existing owned-trail wave share a target hit set. */
	static void coreBlast(ServerPlayerEntity owner, Vec3d center, Vec3d direction) {
		ServerWorld world = owner.getServerWorld();
		Field field = FIELDS.computeIfAbsent(world, key -> new Field());
		Wave wave = new Wave(owner, center, direction);
		double radius = BlazeCore.BLAST_RADIUS;
		for (var target : world.getEntitiesByClass(LivingEntity.class, new Box(center, center).expand(radius), e -> e != owner && e.isAlive())) {
			Vec3d targetCenter = target.getBoundingBox().getCenter();
			double distance = targetCenter.distanceTo(center);
			if (distance > radius || !visible(world, owner, center, targetCenter)) continue;
			wave.hit.add(target.getUuid());
			float damage = distance <= BlazeCore.FULL_DAMAGE_RADIUS ? BlazeCore.CENTER_DAMAGE
				: (float) MathHelper.lerp((distance - BlazeCore.FULL_DAMAGE_RADIUS) / (radius - BlazeCore.FULL_DAMAGE_RADIUS), BlazeCore.CENTER_DAMAGE, BlazeCore.EDGE_DAMAGE);
			if (burn(world, owner, target, damage) && !HeadLookups.isBoss(target)) {
				Vec3d push = targetCenter.subtract(center).multiply(1, 0, 1).normalize().multiply(1.1);
				target.addVelocity(push.x, 0.55, push.z); target.velocityModified = true;
			}
		}
		for (var ground = field.ground.entrySet().iterator(); ground.hasNext();) {
			var entry = ground.next(); Vec3d next = entry.getValue().center;
			if (!entry.getKey().owner.equals(owner.getUuid()) || next.squaredDistanceTo(center) > radius * radius
				|| !visible(world, owner, center, next.add(0, 0.15, 0))) continue;
			emit(world, new BlazeFlameS2CPayload(owner.getId(), next, direction, 0.7F, MAX_HEAT, Kind.FUSE));
			wave.fronts.add(new Front(next, world.getTime() + 1, true)); ground.remove();
		}
		if (!wave.fronts.isEmpty()) field.waves.add(wave);
		for (int i = 0; i < 64; i++) {
			double angle = i * Math.PI / 32, reach = (i % 4 + 1) * radius / 5;
			Vec3d next = center.add(Math.cos(angle) * reach, -0.5, Math.sin(angle) * reach);
			if (visible(world, owner, center, next)) ignite(world, owner, BlockPos.ofFloored(next));
		}
		emit(world, new BlazeFlameS2CPayload(owner.getId(), center, direction, (float) radius, MAX_HEAT, Kind.CORE_BLAST));
		world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
			net.minecraft.sound.SoundCategory.PLAYERS, 1.8F, 0.65F);
		world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_BLAZE_SHOOT,
			net.minecraft.sound.SoundCategory.PLAYERS, 1.1F, 0.55F);
	}

	private static void tickField(ServerWorld w) {
		Field field = FIELDS.get(w);
		if (field == null) return;
		long now = w.getTime();
		field.ground.entrySet().removeIf(e -> e.getValue().expires <= now || !w.isChunkLoaded(e.getKey().pos)
			|| !w.getFluidState(e.getKey().pos).isEmpty());
		Set<UUID> damaged = new HashSet<>();
		for (var entry : field.ground.entrySet()) {
			Ground g = entry.getValue(); Vec3d c = g.center;
			if (now % 4 == 0) {
				boolean fading = g.expires - now < 20;
				w.spawnParticles(fading ? EMBER : ParticleTypes.FLAME, c.x, c.y + 0.12, c.z, fading ? 2 : 5, 0.38, 0.10, 0.38, 0.006);
				if (!fading) w.spawnParticles(GOLD, c.x, c.y, c.z, 2, 0.38, 0, 0.38, 0);
			}
			if (now % 10 != 0) continue;
			ServerPlayerEntity owner = (ServerPlayerEntity) w.getPlayerByUuid(entry.getKey().owner);
			if (owner == null || owner.getWorld() != w) continue;
			for (var target : w.getEntitiesByClass(LivingEntity.class, new Box(c.x - 0.5, c.y - 0.05, c.z - 0.5, c.x + 0.5, c.y + 0.7, c.z + 0.5), e -> e != owner && e.isAlive())) {
				if (!damaged.contains(target.getUuid()) && visible(w, owner, c.add(0, 0.1, 0), target.getBoundingBox().getCenter())) {
					if (burn(w, owner, target, 2)) damaged.add(target.getUuid());
				}
			}
		}
		for (var iterator = field.waves.iterator(); iterator.hasNext();) {
			Wave wave = iterator.next();
			ServerPlayerEntity owner = (ServerPlayerEntity) w.getPlayerByUuid(wave.owner);
			if (owner == null || owner.getWorld() != w) { iterator.remove(); continue; }
			List<Front> due = wave.fronts.stream().filter(f -> f.at <= now).toList();
			wave.fronts.removeAll(due);
			for (Front front : due) {
				Vec3d c = front.center;
				if (!w.isChunkLoaded(BlockPos.ofFloored(c)) || !w.getFluidState(BlockPos.ofFloored(c)).isEmpty()) continue;
				// Recheck the launch ray at arrival: a newly placed wall also stops the blast.
				if (!front.pillar && !visible(w, owner, wave.origin, c)) continue;
				ignite(w, owner, BlockPos.ofFloored(c));
				float height = front.pillar ? 5.0F : 1.8F;
				var ceiling = ray(w, owner, c, c.add(0, height, 0));
				if (ceiling.getType() != HitResult.Type.MISS) height = (float) ceiling.getPos().distanceTo(c);
				if (front.pillar) {
					emit(w, new BlazeFlameS2CPayload(owner.getId(), c, new Vec3d(0, 1, 0), height, MAX_HEAT, Kind.PILLAR));
					for (int y = 0; y <= (int) height; y++) ignite(w, owner, BlockPos.ofFloored(c.add(0, y, 0)));
					if (wave.lastRoar == Long.MIN_VALUE || now - wave.lastRoar >= 4) {
						wave.lastRoar = now;
						w.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_BLAZE_SHOOT,
							net.minecraft.sound.SoundCategory.PLAYERS, 0.6F, 0.65F);
					}
				}
				for (var target : w.getEntitiesByClass(LivingEntity.class, new Box(c.x - 0.65, c.y - 0.4, c.z - 0.65, c.x + 0.65, c.y + height, c.z + 0.65), e -> e != owner && e.isAlive())) {
					double along = target.getBoundingBox().getCenter().subtract(wave.origin).dotProduct(wave.forward);
					if (!front.pillar && (along < 0 || along > wave.range)) continue;
					if (!wave.hit.contains(target.getUuid()) && visible(w, owner, c.add(0, 0.1, 0), target.getBoundingBox().getCenter())) {
						wave.hit.add(target.getUuid());
						if (burn(w, owner, target, 6) && !HeadLookups.isBoss(target)) {
							Vec3d push = wave.forward.multiply(1, 0, 1).normalize().multiply(0.65 + wave.charge * 0.25);
							target.addVelocity(push.x, front.pillar ? 0.55 : 0.16, push.z); target.velocityModified = true;
							emit(w, new BlazeFlameS2CPayload(owner.getId(), target.getBoundingBox().getCenter(), wave.forward, 1.2F, MAX_HEAT, Kind.IMPACT));
						}
					}
				}
				// A brief ground flash leads the short eruption; only the advancing front stays tall.
				for (var ground = field.ground.entrySet().iterator(); ground.hasNext();) {
					var e = ground.next();
					Vec3d next = e.getValue().center;
					if (e.getKey().owner.equals(wave.owner) && Math.abs(next.y - c.y) < 1.6
						&& Math.abs(next.x - c.x) <= 1.05 && Math.abs(next.z - c.z) <= 1.05
						&& visible(w, owner, c.add(0, 0.15, 0), next.add(0, 0.15, 0))) {
						emit(w, new BlazeFlameS2CPayload(owner.getId(), next, wave.forward, 0.7F, MAX_HEAT, Kind.FUSE));
						wave.fronts.add(new Front(next, now + 1, true)); ground.remove();
					}
				}
			}
			if (wave.fronts.isEmpty()) iterator.remove();
		}
		if (field.ground.isEmpty() && field.waves.isEmpty()) FIELDS.remove(w);
	}
	private static void igniteSpray(ServerPlayerEntity p, Vec3d eye, Vec3d look) {
		ServerWorld w = p.getServerWorld();
		double range = range(p);
		var hit = ray(w, p, eye, eye.add(look.multiply(range)));
		Vec3d end = hit.getType() == HitResult.Type.MISS ? eye.add(look.multiply(range)) : hit.getPos();
		int steps = Math.max(1, (int) Math.ceil(end.distanceTo(eye) * 2));
		Vec3d side = look.crossProduct(new Vec3d(0, 1, 0)).normalize();
		if (side.lengthSquared() < 0.01) side = new Vec3d(1, 0, 0);
		for (int i = 0; i <= steps; i++) {
			Vec3d point = eye.lerp(end, (double) i / steps);
			double width = 0.65 + point.distanceTo(eye) * 0.22;
			ignite(w, p, BlockPos.ofFloored(point));
			ignite(w, p, BlockPos.ofFloored(point.add(side.multiply(width * 0.4))));
			ignite(w, p, BlockPos.ofFloored(point.add(side.multiply(-width * 0.4))));
		}
		if (hit.getType() == HitResult.Type.BLOCK) {
			ignite(w, p, hit.getBlockPos());
			ignite(w, p, hit.getBlockPos().offset(hit.getSide()));
		}
	}

	/** Places vanilla fire only beside burnable blocks, never on bare stone. */
	private static void ignite(ServerWorld w, ServerPlayerEntity owner, BlockPos pos) {
		if (!w.isChunkLoaded(pos) || !w.canPlayerModifyAt(owner, pos) || !w.getFluidState(pos).isEmpty()) return;
		BlockState state = w.getBlockState(pos);
		if (state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)) return;
		if (state.isAir() || state.isReplaceable()) {
			placeFire(w, pos);
			return;
		}
		if (!state.isBurnable()) return;
		for (Direction side : Direction.values()) placeFire(w, pos.offset(side));
	}

	private static void placeFire(ServerWorld w, BlockPos pos) {
		if (!w.isChunkLoaded(pos) || !w.getFluidState(pos).isEmpty()) return;
		BlockState current = w.getBlockState(pos);
		if (!(current.isAir() || current.isReplaceable())) return;
		boolean nearBurnable = false;
		for (Direction side : Direction.values()) {
			if (w.getBlockState(pos.offset(side)).isBurnable()) {
				nearBurnable = true;
				break;
			}
		}
		if (!nearBurnable) return;
		BlockState fire = AbstractFireBlock.getState(w, pos);
		if (!fire.canPlaceAt(w, pos)) return;
		w.setBlockState(pos, fire);
	}

	private static boolean burn(ServerWorld w, ServerPlayerEntity owner, LivingEntity target, float amount) {
		if (target.isFireImmune() || target.isSpectator()) return false;
		var key = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, McHeadFunction.id("blaze_fire"));
		boolean hit = target.damage(new DamageSource(w.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(key), owner), amount);
		if (hit) { target.setOnFireFor(4); HeadMastery.practice(owner, HeadType.BLAZE); }
		return hit;
	}
	private static boolean inCone(Vec3d origin, Vec3d direction, Vec3d point, double range, double spread) {
		Vec3d offset = point.subtract(origin); double along = offset.dotProduct(direction);
		return along >= 0 && along <= range && offset.subtract(direction.multiply(along)).length() <= 0.65 + along * spread;
	}
	private static net.minecraft.util.hit.BlockHitResult ray(ServerWorld w, ServerPlayerEntity owner, Vec3d start, Vec3d end) {
		return w.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, owner));
	}
	private static boolean visible(ServerWorld w, ServerPlayerEntity owner, Vec3d start, Vec3d end) {
		return ray(w, owner, start, end).getType() == HitResult.Type.MISS;
	}
}
