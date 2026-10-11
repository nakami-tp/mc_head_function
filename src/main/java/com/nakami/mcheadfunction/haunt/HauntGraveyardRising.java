package com.nakami.mcheadfunction.haunt;

import com.nakami.mcheadfunction.rule.HauntRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;

/** Town-only graveyard extras: the tree only says start and stop. */
public final class HauntGraveyardRising {
	private HauntGraveyardRising() { }

	private static final String TAG = "haunt_rising_";
	private static final String GATE = "haunt_gate_";

	public static boolean isExtra(Entity entity) {
		for (String tag : entity.getCommandTags()) if (tag.startsWith(TAG)) return true;
		return false;
	}

	public static void tryStart(ServerWorld world, BlockPos tree) {
		tryStop(world, tree);
		if (!isTownTree(world, tree)) return;
		for (int i = 0; i < HauntRules.RISING; i++) spawn(world, tree, i);
	}

	public static void tryStop(ServerWorld world, BlockPos tree) {
		for (MobEntity mob : extras(world, tree)) mob.discard();
	}

	public static void tick(ServerWorld world, BlockPos tree) {
		if (!isTownTree(world, tree)) return;
		for (MobEntity mob : extras(world, tree)) {
			var access = (HauntRisenAccess) mob;
			int rise = access.mhf$hauntRiseTicks();
			if (rise > 0) {
				access.mhf$setHauntRiseTicks(rise - 1);
				dig(world, mob, rise);
				if (rise == 1) released(world, tree, mob);
				continue;
			}
			mob.extinguish();
			if (access.mhf$hauntDancing()) holdDance(world, tree, mob);
			else hunt(world, tree, mob);
		}
	}

	private static void spawn(ServerWorld world, BlockPos tree, int index) {
		var spot = HauntTownLandmarks.RISES.get(index);
		EntityType<? extends MobEntity> type = HauntRules.risingZombieVillager(index)
			? EntityType.ZOMBIE_VILLAGER : EntityType.ZOMBIE;
		MobEntity mob = type.create(world);
		if (mob == null) return;
		BlockPos at = tree.add(spot.x(), 0, spot.z());
		mob.refreshPositionAndAngles(at.getX() + 0.5, tree.getY(), at.getZ() + 0.5, 0, 0);
		mob.initialize(world, world.getLocalDifficulty(at), SpawnReason.EVENT, null);
		mob.setPersistent();
		mob.setCanPickUpLoot(false);
		mob.addCommandTag(tag(tree));
		if (HauntRules.risingDancer(index)) mob.addCommandTag(GATE + (index - HauntRules.HUNTERS));
		mob.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, -1, 0, true, false));
		var follow = mob.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
		if (follow != null) follow.setBaseValue(64);
		if (mob instanceof ZombieVillagerEntity villager) {
			villager.setVillagerData(villager.getVillagerData().withType(VillagerType.PLAINS)
				.withProfession(index % 4 == 1 ? VillagerProfession.CLERIC : VillagerProfession.NITWIT));
		}
		var access = (HauntRisenAccess) mob;
		access.mhf$setHauntDancing(HauntRules.risingDancer(index));
		access.mhf$setHauntRiseTicks(HauntRules.CLIMB_TICKS + HauntRules.riseStagger(index));
		mob.setAiDisabled(true);
		mob.setInvulnerable(true);
		mob.setNoGravity(true);
		world.spawnEntity(mob);
	}

	private static void released(ServerWorld world, BlockPos tree, MobEntity mob) {
		mob.setInvulnerable(false);
		mob.setNoGravity(false);
		if (((HauntRisenAccess) mob).mhf$hauntDancing()) {
			holdDance(world, tree, mob);
			return;
		}
		mob.setAiDisabled(false);
		hunt(world, tree, mob);
	}

	private static void holdDance(ServerWorld world, BlockPos tree, MobEntity mob) {
		var spot = HauntTownLandmarks.GATE.get(Math.floorMod(gateSlot(mob), HauntTownLandmarks.GATE.size()));
		BlockPos at = tree.add(spot.x(), 0, spot.z());
		double sway = HauntRules.danceSway(world.getTime());
		mob.refreshPositionAndAngles(at.getX() + 0.5 + sway, tree.getY(), at.getZ() + 0.5, 0, 0);
		mob.setHeadYaw(0);
		mob.setBodyYaw(0);
		mob.setVelocity(0, mob.getVelocity().y, 0);
		mob.setAiDisabled(true);
	}

	private static void hunt(ServerWorld world, BlockPos tree, MobEntity mob) {
		if (mob.getTarget() instanceof VillagerEntity && mob.getTarget().isAlive()) return;
		VillagerEntity nearest = null;
		double best = Double.MAX_VALUE;
		for (VillagerEntity villager : world.getEntitiesByClass(VillagerEntity.class,
			mob.getBoundingBox().expand(64), HauntVillagers::linger)) {
			double dist = mob.squaredDistanceTo(villager);
			if (dist < best) { best = dist; nearest = villager; }
		}
		if (nearest != null) mob.setTarget(nearest);
		else mob.getNavigation().startMovingTo(tree.getX() + 0.5, tree.getY(), tree.getZ() + 0.5, 1);
	}

	private static void dig(ServerWorld world, MobEntity mob, int rise) {
		if (rise % 4 != 0) return;
		var state = world.getBlockState(mob.getBlockPos().down());
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
			mob.getX(), mob.getY() + 0.1, mob.getZ(), 8, 0.25, 0.1, 0.25, 0.02);
		world.spawnParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + 0.2, mob.getZ(), 2, 0.15, 0.1, 0.15, 0.01);
		if (rise == HauntRules.CLIMB_TICKS) {
			world.playSound(null, mob.getBlockPos(), SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.HOSTILE, 0.7F, 0.6F);
		}
	}

	private static boolean isTownTree(ServerWorld world, BlockPos tree) {
		var town = HauntWorldData.get(world).town();
		return town != null && town.equals(tree);
	}

	private static String tag(BlockPos tree) { return TAG + tree.asLong(); }

	private static int gateSlot(MobEntity mob) {
		for (String tag : mob.getCommandTags()) {
			if (tag.startsWith(GATE)) {
				try { return Integer.parseInt(tag.substring(GATE.length())); }
				catch (NumberFormatException ignored) { return 0; }
			}
		}
		return 0;
	}

	private static List<MobEntity> extras(ServerWorld world, BlockPos tree) {
		var found = new ArrayList<MobEntity>();
		String tag = tag(tree);
		for (Entity entity : world.iterateEntities()) {
			if (entity instanceof MobEntity mob && mob.getCommandTags().contains(tag)) found.add(mob);
		}
		return found;
	}
}
