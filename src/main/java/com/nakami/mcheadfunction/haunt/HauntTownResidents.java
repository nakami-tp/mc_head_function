package com.nakami.mcheadfunction.haunt;

import java.util.ArrayList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;

/** Normal, persistent mobs with repeatable spawn positions; reset owns only this town's residents. */
public final class HauntTownResidents {
	private HauntTownResidents() { }

	private static final String OWNER = "haunt_town_resident_";
	private static final String GENERATION = "haunt_town_population_";
	private static String tag(BlockPos center) { return OWNER + center.asLong(); }

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			for (String tag : entity.getCommandTags()) {
				if (!tag.startsWith(OWNER)) continue;
				try {
					var center = BlockPos.fromLong(Long.parseLong(tag.substring(OWNER.length())));
					var current = HauntWorldData.get(world).population(center);
					if (current != null && !entity.getCommandTags().contains(GENERATION + current)) entity.discard();
				} catch (NumberFormatException ignored) { /* Not one of our generated ownership tags. */ }
			}
		});
	}

	static void clear(ServerWorld world, BlockPos center) {
		// Include loaded residents that have wandered outside the scenery footprint.
		var residents = new ArrayList<Entity>();
		for (var entity : world.iterateEntities()) if (entity.getCommandTags().contains(tag(center))) residents.add(entity);
		residents.forEach(Entity::discard);
	}

	static void spawn(ServerWorld world, BlockPos center) {
		HauntWorldData.get(world).renewPopulation(center);
		villager(world, center, -20, -11, VillagerProfession.CLERIC);
		villager(world, center, -1, -13, VillagerProfession.BUTCHER);
		villager(world, center, 20, -11, VillagerProfession.FARMER);
		villager(world, center, -20, 11, VillagerProfession.FLETCHER);
		villager(world, center, 20, 11, VillagerProfession.SHEPHERD);
		villager(world, center, 12, 0, VillagerProfession.TOOLSMITH);
		for (int i = 0; i < 3; i++) mob(world, center, EntityType.COW, 16 + i, 40);
		for (int i = 0; i < 4; i++) mob(world, center, EntityType.SHEEP, 24 + i % 2 * 2, 40 + i / 2 * 3);
		for (int i = 0; i < 5; i++) mob(world, center, EntityType.CHICKEN, 37 + i % 3 * 2, 38 + i / 3 * 2);
		mob(world, center, EntityType.CAT, -6, 5);
		mob(world, center, EntityType.CAT, 8, 18);
	}

	private static void villager(ServerWorld world, BlockPos center, int x, int z, VillagerProfession profession) {
		VillagerEntity villager = mob(world, center, EntityType.VILLAGER, x, z);
		villager.setVillagerData(villager.getVillagerData().withType(VillagerType.PLAINS).withProfession(profession).withLevel(2));
		villager.setExperience(10); // An established profession should not disappear before a workstation is found.
	}

	private static <T extends MobEntity> T mob(ServerWorld world, BlockPos center, EntityType<T> type, int x, int z) {
		T mob = type.create(world);
		if (mob == null) throw new IllegalStateException("Cannot create town resident " + type);
		var pos = center.add(x, 0, z);
		mob.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180, 0);
		mob.initialize(world, world.getLocalDifficulty(pos), SpawnReason.COMMAND, null);
		mob.setPersistent();
		mob.addCommandTag(tag(center));
		mob.addCommandTag(GENERATION + HauntWorldData.get(world).population(center));
		world.spawnEntity(mob);
		return mob;
	}
}
