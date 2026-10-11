package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.client.HauntFurnaceEffects;
import com.nakami.mcheadfunction.haunt.GhostHeadEntity;
import com.nakami.mcheadfunction.haunt.HauntBlocks;
import com.nakami.mcheadfunction.haunt.HauntWorldData;
import com.nakami.mcheadfunction.haunt.HauntedTreeBlockEntity;
import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.rule.HauntRules;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CowEntity;
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

/** Fixed camera: a possessed furnace overflows like a fireplace fire and does not harm. */
public final class FurnaceOverflowValidation implements ClientModInitializer {
	private static final BlockPos TREE = new BlockPos(0, 100, 0);
	private static final BlockPos FURNACE = new BlockPos(0, 100, 4);
	private boolean opening, setup, finished, minimalCaptured;
	private int overflowAt, hauntAt, pendingScreenshots;
	private boolean startRequested;
	private volatile boolean hauntStarted, serverOverflowVerified, serverStopped;
	private String endingResult;
	private long endingSince;
	private volatile boolean ready;
	private volatile String failure;
	private volatile String serverState = "";
	private int ticks;
	private int cowId = -1;
	private float cowHealth;
	private final long started = System.nanoTime();

	@Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

	private void tick(MinecraftClient client) {
		if (finished) return;
		if (endingResult != null) {
			if (failure != null) endingResult = "FAIL " + failure;
			if (pendingScreenshots == 0) complete(client, endingResult);
			else if ((System.nanoTime() - endingSince) / 1_000_000_000 >= 10)
				complete(client, "FAIL Screenshot writes timed out; pending=" + pendingScreenshots);
			return;
		}
		try {
			check((System.nanoTime() - started) / 1_000_000_000 < 180, "Timeout tick=" + ticks);
			check(failure == null, failure);
			if (!opening && client.currentScreen != null && client.getOverlay() == null) {
				opening = true;
				client.options.pauseOnLostFocus = false;
				client.options.hudHidden = true;
				client.options.getViewDistance().setValue(6);
				client.options.getMaxFps().setValue(60);
				client.options.getFov().setValue(50);
				client.options.getParticles().setValue(ParticlesMode.ALL);
				client.getWindow().setWindowedSize(854, 480);
				client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
				client.createIntegratedServerLoader().createAndStart("furnace-fx-" + System.currentTimeMillis(),
					new LevelInfo("Furnace overflow validation", GameMode.SPECTATOR, false, Difficulty.PEACEFUL, true, new GameRules(), DataConfiguration.SAFE_MODE),
					new GeneratorOptions(42, false, false), registries -> registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder(), client.currentScreen);
			}
			if (client.player == null || client.world == null || client.getServer() == null) return;
			if (!setup) {
				setup = true;
				onServer(client, () -> {
					var world = client.getServer().getOverworld();
					world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, client.getServer());
					world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, client.getServer());
					HauntWorldData.get(world).setOff(true);
					world.setTimeOfDay(18000);
					world.setWeather(0, 100000, false, false);
					for (int x = -6; x <= 6; x++) for (int z = -2; z <= 12; z++) {
						world.setBlockState(new BlockPos(x, 99, z), Blocks.SMOOTH_QUARTZ.getDefaultState());
						world.setBlockState(new BlockPos(x, 98, z), Blocks.SEA_LANTERN.getDefaultState());
					}
					world.setBlockState(TREE, HauntBlocks.TREE.getDefaultState());
					HauntBlocks.TREE.onPlaced(world, TREE, world.getBlockState(TREE), null, ItemStack.EMPTY);
					world.setBlockState(FURNACE, Blocks.FURNACE.getDefaultState()
						.with(AbstractFurnaceBlock.FACING, Direction.SOUTH)
						.with(AbstractFurnaceBlock.LIT, false));
					var cow = EntityType.COW.create(world);
					cow.setAiDisabled(true);
					cow.refreshPositionAndAngles(FURNACE.getX() + 3.5, FURNACE.getY(), FURNACE.getZ() + 0.5, 0, 0);
					world.spawnEntity(cow);
					cowId = cow.getId();
					cowHealth = cow.getHealth();
					var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
					// teleport uses feet height; include the player's eye height in this framing.
					player.teleport(world, 0.5, 101, 10.4, 180, 10);
					var stack = new ItemStack(HeadItems.item(HeadType.BLAZE));
					player.setStackInHand(Hand.MAIN_HAND, stack);
					var tree = (HauntedTreeBlockEntity) world.getBlockEntity(TREE);
					check(tree != null, "Server tree missing after place");
					tree.hang(player, HeadType.BLAZE, stack);
					check(tree.has(HeadType.BLAZE), "Hang did not store the blaze head");
					ready = true;
				});
			}
			if (!ready || client.currentScreen != null || client.getOverlay() != null) return;
			ticks++;
			if (ticks >= 40 && !startRequested) {
				startRequested = true;
				verifyQuiet(client);
				capture(client, "01-quiet.png", () -> onServer(client, () -> {
					var world = client.getServer().getOverworld();
					world.setTimeOfDay(18000);
					var tree = (HauntedTreeBlockEntity) world.getBlockEntity(TREE);
					check(tree != null, "start: missing tree");
					tree.start(true);
					serverState = "haunting=" + tree.haunting() + " hung=" + tree.hung();
					check(tree.haunting() && tree.has(HeadType.BLAZE), "start did not begin haunt " + serverState);
					hauntStarted = true;
				}));
			}
			if (!hauntStarted) return;
			if (hauntAt == 0) hauntAt = ticks;
			int live = hauntAt + HauntRules.possessionReadyAt();
			if (overflowAt == 0 && ticks >= live) {
				if (HauntFurnaceEffects.overflowing(FURNACE)) {
					overflowAt = ticks;
					verifyOverflow(client);
					capture(client, "02-overflow.png");
				} else {
					check(ticks < live + 80, "Client overflow never appeared; " + ghostInfo(client));
				}
			}
			if (overflowAt == 0) return;
			int elapsed = ticks - overflowAt;
			// All 24 motion frames belong to the active haunt; stop only after the final sample.
			if (elapsed <= 115 && elapsed % 5 == 0) {
				verifyOverflow(client);
				capture(client, String.format("motion-%03d.png", elapsed / 5));
			}
			if (!minimalCaptured && elapsed >= 40) {
				minimalCaptured = true;
				client.options.getParticles().setValue(ParticlesMode.MINIMAL);
			}
			if (elapsed == 55) {
				verifyOverflow(client);
				capture(client, "03-minimal-particles.png");
				onServer(client, () -> {
					verifyServer(client, true);
					serverOverflowVerified = true;
				});
			}
			if (elapsed == 120) {
				onServer(client, () -> {
					check(serverOverflowVerified, "Missing server overflow verification");
					((HauntedTreeBlockEntity) client.getServer().getOverworld().getBlockEntity(TREE)).stop();
					verifyServer(client, false);
					serverStopped = true;
				});
			}
			if (elapsed >= 120 + HauntRules.RETURN_TICKS + 20 && serverStopped) {
				verifyQuiet(client);
				capture(client, "04-stopped.png");
				finish(client, "PASS client hearth overflow, 24 active motion frames, minimal particles, clean stop; server lit/facing/cow verified");
			}
		} catch (Throwable e) { finish(client, "FAIL " + e); }
	}

	private void verifyQuiet(MinecraftClient client) {
		var tree = (HauntedTreeBlockEntity) client.world.getBlockEntity(TREE);
		check(tree != null && !tree.haunting(), "Tree should be quiet");
		var state = client.world.getBlockState(FURNACE);
		check(state.isOf(Blocks.FURNACE), "Furnace missing");
		check(!state.get(AbstractFurnaceBlock.LIT), "Quiet furnace must be unlit");
		check(state.get(AbstractFurnaceBlock.FACING) == Direction.SOUTH, "Facing drifted while quiet");
		check(!HauntFurnaceEffects.overflowing(FURNACE), "Overflow lingered after stop");
	}

	private String ghostInfo(MinecraftClient client) {
		return client.world.getEntitiesByClass(GhostHeadEntity.class, new Box(FURNACE).expand(16),
			g -> g.head() == HeadType.BLAZE).stream()
			.map(g -> "phase=" + g.phase() + " at=" + g.getBlockPos()).toList() + " " + serverState;
	}

	private void verifyOverflow(MinecraftClient client) {
		var state = client.world.getBlockState(FURNACE);
		check(state.isOf(Blocks.FURNACE), "Possessed furnace must stay a block");
		check(state.get(AbstractFurnaceBlock.FACING) == Direction.SOUTH, "Overflow must not turn the furnace");
		// This is the production effect's current client state, never a server diagnostic fallback.
		check(HauntFurnaceEffects.overflowing(FURNACE), "Client did not draw hearth overflow; " + ghostInfo(client));
	}

	/** Only called on the integrated server thread. */
	private void verifyServer(MinecraftClient client, boolean haunting) {
		var world = client.getServer().getOverworld();
		var tree = (HauntedTreeBlockEntity) world.getBlockEntity(TREE);
		check(tree != null && tree.haunting() == haunting, "Unexpected server haunt state");
		var state = world.getBlockState(FURNACE);
		check(state.isOf(Blocks.FURNACE), "Server furnace must stay a block");
		check(state.get(AbstractFurnaceBlock.FACING) == Direction.SOUTH, "Server furnace facing drifted");
		check(state.get(AbstractFurnaceBlock.LIT) == haunting, "Unexpected server furnace light: " + state);
		var cow = world.getEntityById(cowId);
		check(cow instanceof CowEntity living && living.isAlive() && living.getHealth() == cowHealth,
			"Overflow must not harm the nearby cow");
		check(cow instanceof CowEntity living && !living.isOnFire(), "Overflow must not ignite the cow");
	}

	private void onServer(MinecraftClient client, Runnable action) { client.getServer().execute(() -> { try { action.run(); } catch (Throwable e) { failure = e.toString(); } }); }
	private void capture(MinecraftClient client, String name) { capture(client, name, () -> {}); }
	private void capture(MinecraftClient client, String name, Runnable afterSaved) {
		pendingScreenshots++;
		ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> client.execute(() -> {
			pendingScreenshots--;
			try {
				Path path = client.runDirectory.toPath().resolve("screenshots").resolve(name);
				check(Files.isRegularFile(path) && Files.size(path) > 0, "Screenshot was not saved: " + name + "; " + message.getString());
				afterSaved.run();
			} catch (Throwable e) { failure = e.toString(); }
		}));
	}
	private static void check(boolean ok, String reason) { if (!ok) throw new IllegalStateException(reason); }
	private void finish(MinecraftClient client, String result) {
		endingResult = result;
		endingSince = System.nanoTime();
	}
	private void complete(MinecraftClient client, String result) {
		finished = true;
		System.out.println("[FURNACE-FX] " + result);
		try { Files.writeString(Path.of("result.txt"), result + "\n"); } catch (Exception e) { throw new RuntimeException(e); }
		client.scheduleStop();
	}
}
