package com.nakami.mcheadfunction;

import com.nakami.mcheadfunction.haunt.HauntTown;
import com.nakami.mcheadfunction.haunt.HauntedTreeBlockEntity;
import com.nakami.mcheadfunction.rule.HauntRules;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** Large scenery fixtures are isolated from the shared-time haunting behavior tests. */
public class HauntTownGameplayTest implements FabricGameTest {
	@GameTest(templateName = "mc_head_function:town_arena", batchId = "haunt_town", tickLimit = 10)
	public void townBuildPlacesEveryHostKind(TestContext c) {
		var origin = c.getAbsolutePos(new BlockPos(60, 5, 60));
		HauntTown.build(c.getWorld(), origin);
		c.assertTrue(c.getWorld().getBlockEntity(origin) instanceof HauntedTreeBlockEntity, "Town must plant a haunted tree");
		var counts = new java.util.EnumMap<HauntRules.HostKind, Integer>(HauntRules.HostKind.class);
		for (var pos : BlockPos.iterate(origin.add(-32, 0, -32), origin.add(31, 16, 31))) {
			var state = c.getWorld().getBlockState(pos);
			for (var kind : HauntRules.HostKind.values()) {
				if (com.nakami.mcheadfunction.haunt.HauntTargets.matches(kind, state)) counts.merge(kind, 1, Integer::sum);
			}
		}
		var range = com.nakami.mcheadfunction.haunt.HauntTargets.range(origin);
		counts.put(HauntRules.HostKind.MINECART, c.getWorld().getEntitiesByClass(MinecartEntity.class, range, e -> true).size());
		counts.put(HauntRules.HostKind.ARMOR_STAND, c.getWorld().getEntitiesByClass(ArmorStandEntity.class, range, e -> true).size());
		for (var kind : HauntRules.HostKind.values()) c.assertTrue(counts.getOrDefault(kind, 0) > kind.defaultCount,
			"Town must have spare hosts for " + kind + ": " + counts.getOrDefault(kind, 0));
		c.assertTrue(c.getWorld().getBlockState(origin.add(0, 5, 32)).isOf(Blocks.DARK_OAK_LOG), "Main gate must span the entrance");
		c.assertTrue(c.getWorld().getBlockState(origin.add(0, -1, 50)).isOf(Blocks.COBBLESTONE), "Approach must extend beyond the old town");
		var scenery = new Box(origin).expand(56, 20, 56);
		c.assertTrue(c.getWorld().getEntitiesByClass(net.minecraft.entity.passive.VillagerEntity.class, scenery, e -> true).size() == 6, "Town must generate six villagers");
		c.assertTrue(c.getWorld().getEntitiesByClass(net.minecraft.entity.passive.AnimalEntity.class, scenery, e -> true).size() == 14, "Town must generate fourteen animals");
		c.complete();
	}

	@GameTest(templateName = "mc_head_function:town_arena", batchId = "haunt_town_residents", tickLimit = 40)
	public void townResetRejectsSavedResidentsFromAnOlderPopulation(TestContext c) {
		var world = c.getWorld();
		var origin = c.getAbsolutePos(new BlockPos(60, 5, 60));
		HauntTown.build(world, origin);
		var area = new Box(origin).expand(56, 20, 56);
		var resident = world.getEntitiesByClass(net.minecraft.entity.passive.VillagerEntity.class, area, e -> true).getFirst();
		var saved = new net.minecraft.nbt.NbtCompound();
		resident.writeNbt(saved);
		resident.discard();
		c.waitAndRun(3, () -> {
			// Rehydrate the same entity NBT used by chunk saves through the normal entity-load callback.
			var reloaded = EntityType.VILLAGER.create(world);
			reloaded.readNbt(saved);
			world.spawnEntity(reloaded);
			c.assertTrue(!reloaded.isRemoved(), "Current population must survive an entity NBT reload");
			// Move outside the cleanup box: ownership, not just position, must remove it.
			reloaded.setPosition(origin.getX() + 60.5, origin.getY(), origin.getZ() + 0.5);
			HauntTown.reset(world);
			c.assertTrue(reloaded.isRemoved(), "Reset must remove owned residents outside the footprint");
			var state = com.nakami.mcheadfunction.haunt.HauntWorldData.get(world);
			var nbt = state.writeNbt(new net.minecraft.nbt.NbtCompound(), world.getRegistryManager());
			var restoredState = com.nakami.mcheadfunction.haunt.HauntWorldData.fromNbt(nbt, world.getRegistryManager());
			c.assertTrue(nbt.equals(restoredState.writeNbt(new net.minecraft.nbt.NbtCompound(), world.getRegistryManager())), "Population generation must survive world-state serialization");
			c.waitAndRun(3, () -> {
				var stale = EntityType.VILLAGER.create(world);
				stale.readNbt(saved);
				world.spawnEntity(stale);
				c.assertTrue(stale.isRemoved(), "A saved resident from before reset must be rejected when it loads");
				c.assertTrue(world.getEntitiesByClass(net.minecraft.entity.passive.VillagerEntity.class, area, e -> e.isAlive()).size() == 6, "Reset must keep exactly six current villagers");
				c.assertTrue(world.getEntitiesByClass(net.minecraft.entity.passive.AnimalEntity.class, area, e -> e.isAlive()).size() == 14, "Reset must keep exactly fourteen animals");
				c.complete();
			});
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "haunt_town_unloaded", tickLimit = 10)
	public void townBuildLoadsChunksBeyondThePlacementBoundary(TestContext c) {
		var nearby = c.getAbsolutePos(new BlockPos(4096, 3, 4096));
		var origin = new BlockPos((nearby.getX() >> 4) << 4, nearby.getY(), (nearby.getZ() >> 4) << 4);
		c.assertTrue(!c.getWorld().isChunkLoaded(origin.add(0, 0, 32)), "Town regression must start beyond loaded test chunks");
		HauntTown.build(c.getWorld(), origin);
		c.assertTrue(c.getWorld().getBlockEntity(origin) instanceof HauntedTreeBlockEntity, "Unloaded-area build must finish planting the tree");
		c.assertTrue(c.getWorld().getBlockState(origin.add(4, 0, 31)).isOf(Blocks.JACK_O_LANTERN), "Boundary lamp must be placed without a pumpkin pattern lookup crash");
		c.complete();
	}

}
