package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.haunt.GhostHeadEntity;
import com.nakami.mcheadfunction.haunt.HauntBlocks;
import com.nakami.mcheadfunction.haunt.HauntWorldData;
import com.nakami.mcheadfunction.haunt.HauntedTreeBlockEntity;
import com.nakami.mcheadfunction.haunt.PossessedPropEntity;
import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.rule.HauntRules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.CarvedPumpkinBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** Fixed camera: one zombie lead rises, splits, sinks into pumpkins; a pig ghost wanders. */
public final class GhostFlightValidation implements ClientModInitializer {
	private static final BlockPos TREE = new BlockPos(0, 100, 0);
	private static final BlockPos[] PUMPKINS = {
		new BlockPos(-3, 100, 6), new BlockPos(0, 100, 7), new BlockPos(3, 100, 6)
	};
	private static final int START = 40;
	private static final int SEQUENCE = 140;
	private boolean opening, setup, finished;
	private volatile boolean ready;
	private volatile String failure;
	private int ticks;
	private int sequence;
	private int origin = -1;
	private boolean sawFly, sawSplit, sawEnter, checkedTakeoff, checkedRise, checkedSplit, checkedEnter, checkedPossessed;
	private final long started = System.nanoTime();

	@Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

	private void tick(MinecraftClient client) {
		if (finished) return;
		try {
			check((System.nanoTime() - started) / 1_000_000_000 < 180, "Timeout tick=" + ticks);
			check(failure == null, failure);
			if (!opening && client.currentScreen != null && client.getOverlay() == null) {
				opening = true;
				client.options.pauseOnLostFocus = false;
				client.options.hudHidden = true;
				client.options.getViewDistance().setValue(8);
				client.options.getMaxFps().setValue(60);
				client.options.getFov().setValue(55);
				client.options.getParticles().setValue(ParticlesMode.ALL);
				client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
				client.createIntegratedServerLoader().createAndStart("ghost-fx-" + System.currentTimeMillis(),
					new LevelInfo("Ghost flight validation", GameMode.SPECTATOR, false, Difficulty.PEACEFUL, true, new GameRules(), DataConfiguration.SAFE_MODE),
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
					HauntWorldData.get(world).setOff(true);
					world.setTimeOfDay(18000);
					world.setWeather(0, 100000, false, false);
					for (int x = -8; x <= 8; x++) for (int z = -4; z <= 14; z++) {
						world.setBlockState(new BlockPos(x, 99, z), Blocks.SMOOTH_QUARTZ.getDefaultState());
						world.setBlockState(new BlockPos(x, 98, z), Blocks.SEA_LANTERN.getDefaultState());
					}
					world.setBlockState(TREE, HauntBlocks.TREE.getDefaultState());
					HauntBlocks.TREE.onPlaced(world, TREE, world.getBlockState(TREE), null, ItemStack.EMPTY);
					for (BlockPos pumpkin : PUMPKINS) {
						world.setBlockState(pumpkin, Blocks.CARVED_PUMPKIN.getDefaultState().with(CarvedPumpkinBlock.FACING, Direction.SOUTH));
					}
					var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
					player.teleport(world, 0.5, 102.8, 14.8, 180, 8);
					hang(client, HeadType.ZOMBIE, HeadType.PIG);
					ready = true;
				});
			}
			if (!ready) return;
			ticks++;
			if (ticks == 20) capture(client, "01-ready.png");
			if (ticks == START) {
				onServer(client, () -> ((HauntedTreeBlockEntity) client.getServer().getOverworld().getBlockEntity(TREE)).start(true));
			}
			var zombies = client.world.getEntitiesByClass(GhostHeadEntity.class, area(), ghost -> ghost.head() == HeadType.ZOMBIE);
			if (origin < 0) {
				var vis = visible(client, HeadType.ZOMBIE);
				if (vis.size() == 1 && vis.getFirst().lead() && vis.getFirst().phase() == GhostHeadEntity.FLY) origin = ticks;
			}
			int t = origin < 0 ? -1 : ticks - origin;
			if (t >= 0 && t < SEQUENCE) {
				sequence++;
				capture(client, String.format("sequence-%03d.png", sequence));
				notePhases(client);
			}
			if (!checkedTakeoff && t >= 0 && zombies.size() >= 3) {
				var vis = visible(client, HeadType.ZOMBIE);
				check(vis.size() == 1 && vis.getFirst().lead() && vis.getFirst().phase() == GhostHeadEntity.FLY,
					"Only the lead zombie ghost should be visible while rising; " + describe(zombies));
				check(pumpkinsAreBlocks(client), "Pumpkins must stay blocks at takeoff");
				checkedTakeoff = true;
				sawFly = true;
				capture(client, "02-takeoff.png");
			}
			if (!checkedRise && t >= 12 && t <= 36 && checkedTakeoff) {
				var vis = visible(client, HeadType.ZOMBIE);
				check(vis.size() == 1 && vis.getFirst().phase() == GhostHeadEntity.FLY,
					"Rising must still be one flying ghost; " + describe(zombies));
				check(pumpkinsAreBlocks(client), "Pumpkins must stay blocks while the ghost is still rising");
				checkedRise = true;
				capture(client, "03-rising.png");
			}
			if (!checkedSplit && t >= HauntRules.FLY_TICKS + 8 && t <= HauntRules.FLY_TICKS + 32) {
				var diving = visible(client, HeadType.ZOMBIE);
				check(diving.size() == 3 && diving.stream().allMatch(ghost -> ghost.phase() == GhostHeadEntity.SPLIT),
					"After the split, three ghosts must dive; " + describe(zombies));
				check(pumpkinsAreBlocks(client), "Pumpkins must stay blocks during the dive");
				checkedSplit = true;
				sawSplit = true;
				capture(client, "04-split.png");
			}
			if (t == HauntRules.FLY_TICKS + HauntRules.SPLIT_TICKS) {
				check(pumpkinsAreBlocks(client), "Pumpkins must stay blocks until the ghost finishes entering");
				capture(client, "05-hover.png");
			}
			if (!checkedEnter && t >= HauntRules.FLY_TICKS + HauntRules.SPLIT_TICKS + 8
				&& t <= HauntRules.FLY_TICKS + HauntRules.SPLIT_TICKS + 28) {
				var entering = visible(client, HeadType.ZOMBIE);
				check(entering.stream().anyMatch(ghost -> ghost.phase() == GhostHeadEntity.ENTER),
					"A ghost must be sinking in; " + describe(zombies));
				check(pumpkinsAreBlocks(client), "The host must not convert mid-enter");
				checkedEnter = true;
				sawEnter = true;
				capture(client, "06-enter.png");
			}
			if (t == HauntRules.WAVE_GAP + 16) {
				check(!client.world.getEntitiesByClass(GhostHeadEntity.class, area(), ghost -> ghost.head() == HeadType.PIG).isEmpty(),
					"The pig head must send a wandering ghost");
			}
			if (ticks == START + 50) check(origin >= 0, "Client never saw the rising lead; " + describe(zombies) + " server=" + describeServer(client));
			if (t == 45) {
				check(checkedTakeoff && checkedRise, "Client never saw a single rising lead; " + describe(zombies) + " server=" + describeServer(client));
			}
			if (t == HauntRules.FLY_TICKS + 36) check(checkedSplit, "Client never saw the split; " + describe(zombies));
			if (t == HauntRules.FLY_TICKS + HauntRules.SPLIT_TICKS + 30) check(checkedEnter, "Client never saw the enter; " + describe(zombies));
			if (t == HauntRules.possessionReadyAt() + 30) check(checkedPossessed, "Client never saw possession finish");
			if (!checkedPossessed && t >= HauntRules.possessionReadyAt() + 6 && t <= HauntRules.possessionReadyAt() + 24) {
				check(sawFly && sawSplit && sawEnter && checkedEnter, "Client missed a flight beat");
				check(client.world.getEntitiesByClass(PossessedPropEntity.class, area(), e -> true).size() == 3,
					"Each pumpkin must be a possessed prop after the sink");
				check(visible(client, HeadType.ZOMBIE).stream().allMatch(ghost -> ghost.phase() == GhostHeadEntity.HELD),
					"Possessing ghosts must sit on their hosts after rising back out");
				checkedPossessed = true;
				capture(client, "07-possessed.png");
				client.options.getParticles().setValue(ParticlesMode.MINIMAL);
			}
			if (checkedPossessed && t == HauntRules.possessionReadyAt() + 28) {
				check(client.world.getEntitiesByClass(PossessedPropEntity.class, area(), e -> true).size() == 3,
					"Hosts must remain after switching to minimal particles");
				capture(client, "08-minimal-particles.png");
				check(sequence == SEQUENCE, "Missing sequence frames: " + sequence);
				finish(client, "PASS client saw one rising lead, a three-way split, a sink-before-possess, and a wandering pig ghost");
			}
		} catch (Throwable e) { finish(client, "FAIL " + e); }
	}

	private void notePhases(MinecraftClient client) {
		for (var ghost : client.world.getEntitiesByClass(GhostHeadEntity.class, area(), e -> true)) {
			if (ghost.head() == HeadType.ZOMBIE && ghost.phase() == GhostHeadEntity.FLY) sawFly = true;
			if (ghost.head() == HeadType.ZOMBIE && ghost.phase() == GhostHeadEntity.SPLIT) sawSplit = true;
			if (ghost.head() == HeadType.ZOMBIE && ghost.phase() == GhostHeadEntity.ENTER) sawEnter = true;
		}
	}

	private static String describe(List<GhostHeadEntity> ghosts) {
		return ghosts.stream().map(ghost -> "phase=" + ghost.phase() + "/lead=" + ghost.lead() + "/vis=" + ghost.visibleSplit()
			+ "/pos=" + ghost.getBlockPos()).toList().toString();
	}

	private static String describeServer(MinecraftClient client) {
		var world = client.getServer() == null ? null : client.getServer().getOverworld();
		if (world == null) return "[]";
		return describe(world.getEntitiesByClass(GhostHeadEntity.class, area(), ghost -> ghost.head() == HeadType.ZOMBIE));
	}

	private static List<GhostHeadEntity> visible(MinecraftClient client, HeadType type) {
		return client.world.getEntitiesByClass(GhostHeadEntity.class, area(), ghost -> ghost.head() == type && ghost.visibleSplit());
	}

	private static boolean pumpkinsAreBlocks(MinecraftClient client) {
		for (BlockPos pumpkin : PUMPKINS) {
			if (!client.world.getBlockState(pumpkin).isOf(Blocks.CARVED_PUMPKIN)) return false;
		}
		return client.world.getEntitiesByClass(PossessedPropEntity.class, area(), e -> true).isEmpty();
	}

	private static Box area() { return new Box(TREE).expand(24, 20, 24); }

	private static void hang(MinecraftClient client, HeadType... types) {
		var tree = (HauntedTreeBlockEntity) client.getServer().getOverworld().getBlockEntity(TREE);
		var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
		for (var type : types) {
			var stack = new ItemStack(HeadItems.item(type));
			player.setStackInHand(Hand.MAIN_HAND, stack);
			tree.hang(player, type, stack);
		}
	}

	private void onServer(MinecraftClient client, Runnable action) {
		client.getServer().execute(() -> { try { action.run(); } catch (Throwable e) { failure = e.toString(); } });
	}
	private static void capture(MinecraftClient client, String name) {
		ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> {});
	}
	private static void check(boolean ok, String reason) { if (!ok) throw new IllegalStateException(reason); }
	private void finish(MinecraftClient client, String result) {
		finished = true;
		System.out.println("[GHOST-FX] " + result);
		try { Files.writeString(Path.of("result.txt"), result + "\n"); } catch (Exception e) { throw new RuntimeException(e); }
		client.scheduleStop();
	}
}
