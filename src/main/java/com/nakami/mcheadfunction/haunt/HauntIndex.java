package com.nakami.mcheadfunction.haunt;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Live haunted-tree cores, so commands do not scan the whole map. */
public final class HauntIndex {
	private static final Map<RegistryKey<World>, Set<BlockPos>> TREES = new ConcurrentHashMap<>();
	private HauntIndex() { }

	public static void add(World world, BlockPos pos) {
		TREES.computeIfAbsent(world.getRegistryKey(), key -> ConcurrentHashMap.newKeySet()).add(pos.toImmutable());
	}

	public static void remove(World world, BlockPos pos) {
		var set = TREES.get(world.getRegistryKey());
		if (set != null) set.remove(pos);
	}

	public static Set<BlockPos> trees(World world) {
		return TREES.getOrDefault(world.getRegistryKey(), Set.of());
	}
}
