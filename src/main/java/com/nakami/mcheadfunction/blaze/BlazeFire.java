package com.nakami.mcheadfunction.blaze;

import com.nakami.mcheadfunction.McHeadFunction;
import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.progression.HeadMastery;
import com.nakami.mcheadfunction.wear.GoatCharge;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
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
	private static final int TRAIL_LIFETIME = 120;
	private static final Map<ServerWorld, Field> FIELDS = new IdentityHashMap<>();
	private static final DustParticleEffect EMBER = new DustParticleEffect(new Vector3f(0.65F, 0.12F, 0.02F), 0.9F);
	private static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1, 0.65F, 0.08F), 1.1F);
	private record Tile(UUID owner, BlockPos pos) { }
	private record Ground(Vec3d center, long expires) { }
	private record Front(Vec3d center, long at) { }
	private static final class Wave {
		final UUID owner;
		final List<Front> fronts = new ArrayList<>();
		final Set<UUID> hit = new HashSet<>();
		Wave(UUID owner) { this.owner = owner; }
	}
	private static final class Field {
		final Map<Tile, Ground> ground = new LinkedHashMap<>();
		final List<Wave> waves = new ArrayList<>();
	}
	private BlazeFire() { }
	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(BlazeFire::tickField);
		ServerWorldEvents.UNLOAD.register((server, world) -> FIELDS.remove(world));
	}

	public static void tickPlayer(ServerPlayerEntity p) {
		var s = PlayerHeadAccess.state(p);
		if (s.blazeCooldown > 0) s.blazeCooldown--;
		if (!canUse(p)) {
			s.blazeHeat = 0; s.blazeSpraying = false;
			if (s.skillHeld) s.blazeNeedsRelease = true;
			return;
		}
		if (!s.skillHeld) s.blazeNeedsRelease = false;
		s.blazeSpraying = s.skillHeld && !s.blazeNeedsRelease && s.blazeCooldown == 0;
		if (!s.blazeSpraying) {
			s.blazeHeat = Math.max(0, s.blazeHeat - 2);
			return;
		}
		s.blazeHeat++;
		if (s.blazeHeat == HIGH_HEAT) HeadSounds.play(p, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.65F, 1.5F);
		if (s.blazeHeat >= MAX_HEAT) {
			s.blazeHeat = 0; s.blazeCooldown = COOLDOWN; s.blazeNeedsRelease = true; s.blazeSpraying = false;
			HeadSounds.play(p, SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.8F, 0.7F);
			p.getServerWorld().spawnParticles(ParticleTypes.SMOKE, p.getX(), p.getEyeY(), p.getZ(), 14, 0.4, 0.3, 0.4, 0.03);
			return;
		}
		spray(p);
	}

	public static void release(ServerPlayerEntity p) {
		var s = PlayerHeadAccess.state(p);
		if (canUse(p) && s.blazeSpraying && s.blazeCooldown == 0 && s.blazeHeat >= HIGH_HEAT) {
			launchWave(p); s.blazeHeat = 0;
		}
		s.blazeSpraying = false; s.blazeNeedsRelease = false;
	}
	private static boolean canUse(ServerPlayerEntity p) {
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
		if (p.age % 2 == 0) {
			Vec3d side = look.crossProduct(new Vec3d(0, 1, 0)).normalize();
			if (side.lengthSquared() < 0.01) side = new Vec3d(1, 0, 0);
			// Start below eye height and beyond the camera, keeping the reticle readable.
			Vec3d mouth = eye.add(0, -0.45, 0).add(look.multiply(1.35));
			if (!visible(w, p, eye, mouth)) return;
			for (int lane = -1; lane <= 1; lane++) {
				Vec3d end = eye.add(look.multiply(range(p))).add(side.multiply(lane * range(p) * 0.16));
				var hit = ray(w, p, mouth, end);
				HeadEffects.line(w, mouth, hit.getPos(), ParticleTypes.SMALL_FLAME);
			}
		}
		if (p.age % 15 == 0) HeadSounds.play(p, SoundEvents.ENTITY_BLAZE_SHOOT, 0.25F, 0.8F);
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
		Wave wave = new Wave(p.getUuid());
		Vec3d eye = p.getEyePos(), look = p.getRotationVec(1);
		Vec3d side = look.crossProduct(new Vec3d(0, 1, 0)).normalize();
		if (side.lengthSquared() < 0.01) side = new Vec3d(1, 0, 0);
		for (int step = 1; step <= 6; step++) {
			for (int lane = -3; lane <= 3; lane++) {
				Vec3d center = eye.add(look.multiply(step)).add(side.multiply(lane * step * 0.12)).add(0, -0.7, 0);
				if (visible(w, p, eye, center)) wave.fronts.add(new Front(center, w.getTime() + step));
			}
		}
		field.waves.add(wave);
		HeadSounds.play(p, SoundEvents.ITEM_FIRECHARGE_USE, 0.9F, 0.6F);
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
				for (int y = 0; y < 5; y++) w.spawnParticles(ParticleTypes.FLAME, c.x, c.y + y * 0.28, c.z, 3, 0.26, 0.08, 0.26, 0.015);
				for (var target : w.getEntitiesByClass(LivingEntity.class, new Box(c.x - 0.65, c.y - 0.4, c.z - 0.65, c.x + 0.65, c.y + 1.5, c.z + 0.65), e -> e != owner && e.isAlive())) {
					if (!wave.hit.contains(target.getUuid()) && visible(w, owner, c.add(0, 0.1, 0), target.getBoundingBox().getCenter())) {
						wave.hit.add(target.getUuid());
						if (burn(w, owner, target, 6) && !HeadLookups.isBoss(target)) {
							Vec3d push = target.getPos().subtract(owner.getPos()).multiply(1, 0, 1).normalize().multiply(0.35);
							target.addVelocity(push.x, 0.12, push.z); target.velocityModified = true;
						}
					}
				}
				// Consume only this owner's touching tiles; each consumed tile advances the wall next tick.
				for (var ground = field.ground.entrySet().iterator(); ground.hasNext();) {
					var e = ground.next();
					Vec3d next = e.getValue().center;
					if (e.getKey().owner.equals(wave.owner) && Math.abs(next.y - c.y) < 1.6
						&& Math.abs(next.x - c.x) <= 1.05 && Math.abs(next.z - c.z) <= 1.05
						&& visible(w, owner, c.add(0, 0.15, 0), next.add(0, 0.15, 0))) {
						wave.fronts.add(new Front(next, now + 2)); ground.remove();
					}
				}
			}
			if (wave.fronts.isEmpty()) iterator.remove();
		}
		if (field.ground.isEmpty() && field.waves.isEmpty()) FIELDS.remove(w);
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
