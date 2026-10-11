package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.haunt.*;
import com.nakami.mcheadfunction.rule.HauntRules.HostKind;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

/** Command-to-world checks and fixed cameras in an isolated, disposable world. */
public final class HauntTownValidation implements ClientModInitializer {
	private static final BlockPos CENTER = new BlockPos(0, -60, 0);
	private boolean opening, setup, finished;
	private volatile boolean ready, verified;
	private volatile String failure;
	private int ticks;
	private Map<BlockPos, BlockState> beforeReset;
	private final long started = System.nanoTime();
	@Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

	private void tick(MinecraftClient client) {
		if (finished) return;
		try {
			check((System.nanoTime() - started) / 1_000_000_000 < 300, "Timeout at " + ticks);
			check(failure == null, failure);
			if (!opening && client.currentScreen != null && client.getOverlay() == null) {
				opening = true;
				client.options.pauseOnLostFocus = false;
				client.options.hudHidden = true;
				client.options.getViewDistance().setValue(10);
				client.options.getMaxFps().setValue(60);
				client.options.getFov().setValue(65);
				client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
				client.createIntegratedServerLoader().createAndStart("town-" + System.currentTimeMillis(),
					new LevelInfo("Halloween town validation", GameMode.SPECTATOR, false, Difficulty.PEACEFUL, true, new GameRules(), DataConfiguration.SAFE_MODE),
					new GeneratorOptions(42, false, false), registries -> registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder(), client.currentScreen);
			}
			if (client.player == null || client.world == null || client.getServer() == null) return;
			if (!setup) {
				setup = true;
				onServer(client, () -> {
					var world = client.getServer().getOverworld();
					world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, client.getServer());
					world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, client.getServer());
					world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, client.getServer());
					world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(0, client.getServer());
					world.setTimeOfDay(1000);
					world.setWeather(0, 100000, false, false);
					command(client, "haunt town build");
					camera(client, 43, 34, 47, 137, 32);
					ready = true;
				});
			}
			if (!ready) return;
			ticks++;
			if (ticks == 120) {
				capture(client, "01-day-overview.png");
				onServer(client, () -> { validate(client.getServer().getOverworld()); camera(client, 4, 2, 12, 165, -3); });
			}
			if (ticks == 180) {
				capture(client, "02-square.png");
				onServer(client, () -> { client.getServer().getOverworld().setTimeOfDay(13000); camera(client, -11, 2, 7, -145, -4); });
			}
			if (ticks == 240) {
				capture(client, "03-halloween-evening.png");
				onServer(client, () -> camera(client, -1, 0, -19, 180, 5));
			}
			if (ticks == 290) {
				capture(client, "04-tavern-interior.png");
				onServer(client, () -> camera(client, -42, 29, 44, -136, 28));
			}
			if (ticks == 360) {
				capture(client, "05-evening-overview.png");
				onServer(client, () -> {
					var world = client.getServer().getOverworld();
					beforeReset = snapshot(world);
					command(client, "haunt tree fill");
					command(client, "haunt start");
					var tree = (HauntedTreeBlockEntity) world.getBlockEntity(CENTER);
					check(tree.haunting(), "Generated town must support haunting");
				});
			}
			if (ticks == 420) {
				capture(client, "06-haunting.png");
				onServer(client, () -> {
					var world = client.getServer().getOverworld();
					check(!world.getEntitiesByClass(PossessedPropEntity.class, HauntTargets.range(CENTER), e -> true).isEmpty(), "Must reach actual possession before reset");
					command(client, "haunt town reset");
				});
			}
			if (ticks == 500) onServer(client, () -> {
				var world = client.getServer().getOverworld();
				var after = snapshot(world);
				var differences = beforeReset.entrySet().stream().filter(e -> !e.getValue().equals(after.get(e.getKey()))).limit(12).map(e -> e.getKey() + ": " + e.getValue() + " -> " + after.get(e.getKey())).toList();
				check(differences.isEmpty(), "Reset must restore generated blocks: " + differences);
				validate(world);
				check(world.getEntitiesByClass(GhostHeadEntity.class, HauntTargets.range(CENTER), e -> true).isEmpty(), "Reset left ghosts behind");
				check(world.getEntitiesByClass(PossessedPropEntity.class, HauntTargets.range(CENTER), e -> true).isEmpty(), "Reset left props behind");
				var drops = world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, HauntTargets.range(CENTER), e -> true);
				check(drops.isEmpty(), "Reset left drops behind: " + drops.stream().map(e -> e.getStack() + " at " + e.getPos()).toList());
				verified = true;
			});
			if (ticks >= 520 && verified) finish(client, "PASS town build, accessible furnished houses, all nine host budgets, rendered day/evening/interior, actual possession and settled deterministic reset");
		} catch (Throwable e) { finish(client, "FAIL " + e); }
	}

	private static void validate(ServerWorld world) {
		check(world.getBlockEntity(CENTER) instanceof HauntedTreeBlockEntity, "Missing central tree");
		var counts = new EnumMap<HostKind, Integer>(HostKind.class);
		var doors = new ArrayList<BlockPos>();
		for (var pos : BlockPos.iterate(CENTER.add(-32, 0, -32), CENTER.add(31, 16, 31))) {
			var state = world.getBlockState(pos);
			for (HostKind kind : HostKind.values()) if (HauntTargets.matches(kind, state)) counts.merge(kind, 1, Integer::sum);
			if (state.getBlock() instanceof DoorBlock && state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER) doors.add(pos.toImmutable());
			if (state.getBlock() instanceof BedBlock) check(!world.isSkyVisible(pos), "Bedroom needs a roof at " + pos);
		}
		check(doors.size() >= 6, "Town needs six usable building entrances; got " + doors.size());
		// Walk at ground level from the square, treating wooden doors as openable by a player.
		Set<BlockPos> reached = new HashSet<>();
		var queue = new ArrayDeque<BlockPos>();
		queue.add(CENTER.south(6));
		while (!queue.isEmpty()) {
			var pos = queue.removeFirst();
			if (Math.abs(pos.getX()) > 31 || Math.abs(pos.getZ()) > 31 || !walkable(world, pos) || !reached.add(pos)) continue;
			for (Direction dir : Direction.Type.HORIZONTAL) queue.add(pos.offset(dir));
		}
		for (var door : doors) {
			check(reached.contains(door), "Entrance is disconnected from square: " + door);
			var dir = world.getBlockState(door).get(DoorBlock.FACING);
			check(reached.contains(door.offset(dir, 2)) && reached.contains(door.offset(dir, -2)), "Door must lead into usable room: " + door);
		}
		counts.put(HostKind.MINECART, world.getEntitiesByClass(MinecartEntity.class, HauntTargets.range(CENTER), e -> true).size());
		counts.put(HostKind.ARMOR_STAND, world.getEntitiesByClass(ArmorStandEntity.class, HauntTargets.range(CENTER), e -> true).size());
		for (HostKind kind : HostKind.values()) check(counts.getOrDefault(kind, 0) > kind.defaultCount, "Not enough " + kind + ": " + counts.getOrDefault(kind, 0));
	}
	private static boolean walkable(ServerWorld world, BlockPos pos) {
		for (var at : List.of(pos, pos.up())) {
			var state = world.getBlockState(at);
			if (!(state.getBlock() instanceof DoorBlock) && !(state.getBlock() instanceof CarpetBlock)
				&& !state.getCollisionShape(world, at).isEmpty()) return false;
		}
		return world.getBlockState(pos.down()).isSolidBlock(world, pos.down());
	}
	private static Map<BlockPos, BlockState> snapshot(ServerWorld world) {
		var states = new HashMap<BlockPos, BlockState>();
		for (var pos : BlockPos.iterate(CENTER.add(-32, -2, -32), CENTER.add(31, 16, 31))) states.put(pos.toImmutable(), world.getBlockState(pos));
		return states;
	}

	private static void command(MinecraftClient client, String command) {
		var server = client.getServer();
		server.getCommandManager().executeWithPrefix(server.getCommandSource().withWorld(server.getOverworld()).withPosition(Vec3d.ofCenter(CENTER)), command);
	}
	private static void camera(MinecraftClient client, double x, double y, double z, float yaw, float pitch) {
		var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
		p.teleport(client.getServer().getOverworld(), x, CENTER.getY() + y, z, yaw, pitch);
	}
	private void onServer(MinecraftClient client, Runnable task) { client.getServer().execute(() -> { try { task.run(); } catch (Throwable e) { failure = e.toString(); } }); }
	private static void capture(MinecraftClient client, String name) { ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> {}); }
	private static void check(boolean result, String reason) { if (!result) throw new IllegalStateException(reason); }
	private void finish(MinecraftClient client, String result) {
		finished = true;
		System.out.println("[TOWN-VALIDATION] " + result);
		try { Files.writeString(Path.of("result.txt"), result + "\n"); } catch (Exception e) { throw new RuntimeException(e); }
		client.scheduleStop();
	}
}
