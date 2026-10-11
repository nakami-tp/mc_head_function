package com.nakami.mcheadfunction.haunt;

import com.nakami.mcheadfunction.rule.HauntRules;
import com.nakami.mcheadfunction.rule.HauntRules.HostKind;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

/** Per-world possession counts and the filmed town origin. */
public final class HauntWorldData extends PersistentState {
	// Full generated footprint, including the 24-block countryside ring around the 64-block village.
	public static final int TOWN = 112;
	private final EnumMap<HostKind, Integer> counts = new EnumMap<>(HostKind.class);
	private BlockPos town;
	private final Map<Long, UUID> populations = new HashMap<>();
	private boolean off;

	public static HauntWorldData get(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(
			new PersistentState.Type<>(HauntWorldData::new, HauntWorldData::fromNbt, null),
			"mc_head_function_haunt"
		);
	}

	public static HauntWorldData fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		var data = new HauntWorldData();
		for (HostKind kind : HostKind.values()) {
			if (nbt.contains(kind.name())) data.counts.put(kind, HauntRules.clampCount(nbt.getInt(kind.name())));
		}
		if (nbt.contains("town")) data.town = BlockPos.fromLong(nbt.getLong("town"));
		var populations = nbt.getCompound("populations");
		for (String key : populations.getKeys()) data.populations.put(Long.parseLong(key), populations.getUuid(key));
		data.off = nbt.getBoolean("off");
		return data;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		for (var entry : counts.entrySet()) nbt.putInt(entry.getKey().name(), entry.getValue());
		if (town != null) nbt.putLong("town", town.asLong());
		var savedPopulations = new NbtCompound();
		populations.forEach((pos, generation) -> savedPopulations.putUuid(Long.toString(pos), generation));
		nbt.put("populations", savedPopulations);
		if (off) nbt.putBoolean("off", true);
		return nbt;
	}

	public int count(HostKind kind) {
		return counts.getOrDefault(kind, kind.defaultCount);
	}

	public void setCount(HostKind kind, int value) {
		counts.put(kind, HauntRules.clampCount(value));
		markDirty();
	}

	UUID renewPopulation(BlockPos center) {
		UUID generation = UUID.randomUUID();
		populations.put(center.asLong(), generation);
		markDirty();
		return generation;
	}

	UUID population(BlockPos center) { return populations.get(center.asLong()); }

	public BlockPos town() { return town; }

	public void setTown(BlockPos pos) {
		town = pos == null ? null : pos.toImmutable();
		markDirty();
	}

	public boolean off() { return off; }

	public void setOff(boolean value) {
		off = value;
		markDirty();
	}
}
