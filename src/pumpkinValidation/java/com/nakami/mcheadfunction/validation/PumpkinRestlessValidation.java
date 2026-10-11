package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.haunt.*;
import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.rule.HauntRules;
import com.nakami.mcheadfunction.client.HauntPumpkinEffects;
import java.nio.file.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

/** Fixed camera: leftover carved faces wake without a zombie head, then hopping hosts are excluded from restless effects. */
public final class PumpkinRestlessValidation implements ClientModInitializer {
	private static final BlockPos TREE = new BlockPos(0, 100, 0);
	private static final BlockPos CENTER = new BlockPos(0, 100, 4);
	private static final BlockPos LEFT = new BlockPos(-2, 100, 4);
	private static final BlockPos RIGHT = new BlockPos(2, 100, 4);
	private static final BlockPos LAMP = new BlockPos(4, 100, 4);
	private static final BlockPos RAW = new BlockPos(-4, 100, 4);
	private static final HeadType[] NO_ZOMBIE = {
		HeadType.FROG, HeadType.LLAMA, HeadType.CHARGED_CREEPER, HeadType.BLAZE,
		HeadType.GOAT, HeadType.RABBIT, HeadType.CHICKEN, HeadType.BAT
	};
	private boolean opening, setup, finished, minimalCaptured, hostSeen;
	private volatile boolean ready;
	private volatile String failure;
	private int ticks;
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
				client.options.getViewDistance().setValue(6);
				client.options.getMaxFps().setValue(60);
				client.options.getFov().setValue(50);
				client.options.getParticles().setValue(ParticlesMode.ALL);
				client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
				client.createIntegratedServerLoader().createAndStart("pumpkin-fx-" + System.currentTimeMillis(),
					new LevelInfo("Pumpkin restless validation", GameMode.SPECTATOR, false, Difficulty.PEACEFUL, true, new GameRules(), DataConfiguration.SAFE_MODE),
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
					for (int x = -8; x <= 8; x++) for (int z = -2; z <= 12; z++) {
						world.setBlockState(new BlockPos(x, 99, z), Blocks.SMOOTH_QUARTZ.getDefaultState());
						world.setBlockState(new BlockPos(x, 98, z), Blocks.SEA_LANTERN.getDefaultState());
					}
					world.setBlockState(TREE, HauntBlocks.TREE.getDefaultState());
					face(world, LEFT, Blocks.CARVED_PUMPKIN);
					face(world, CENTER, Blocks.CARVED_PUMPKIN);
					face(world, RIGHT, Blocks.CARVED_PUMPKIN);
					face(world, LAMP, Blocks.JACK_O_LANTERN);
					world.setBlockState(RAW, Blocks.PUMPKIN.getDefaultState());
					var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
					player.teleport(world, 0.5, 101.6, 9.2, 180, 12);
					hang(client, NO_ZOMBIE);
					ready = true;
				});
			}
			if (!ready) return;
			ticks++;
			if (ticks == 40) {
				verifyQuiet(client);
				capture(client, "01-quiet.png");
				onServer(client, () -> ((HauntedTreeBlockEntity) client.getServer().getOverworld().getBlockEntity(TREE)).start(true));
			}
			if (ticks == 55) {
				verifyRestless(client, true);
				capture(client, "02-chorus.png");
			}
			if (ticks == 130) {
				verifyRestless(client, true);
				capture(client, "03-restless.png");
				client.options.getParticles().setValue(ParticlesMode.MINIMAL);
			}
			if (ticks >= 65 && ticks <= 180 && (ticks - 65) % 5 == 0) {
				verifyRestless(client, true);
				check(HauntPumpkinEffects.replacesBlock(CENTER, client.world.getBlockState(CENTER)), "Center body was not animated");
				capture(client, String.format("motion-%03d.png", (ticks - 65) / 5));
			}
			if (!minimalCaptured && ticks >= 150 && !HauntRules.blinkShut(client.world.getTime(), 0)) {
				minimalCaptured = true;
				capture(client, "04-minimal-particles.png");
			}
			if (ticks == 220) {
				onServer(client, () -> ((HauntedTreeBlockEntity) client.getServer().getOverworld().getBlockEntity(TREE)).stop());
			}
			if (ticks == 240) {
				verifyQuiet(client);
				capture(client, "05-stopped.png");
				onServer(client, () -> {
					var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
					var stack = new ItemStack(HeadItems.item(HeadType.ZOMBIE));
					player.setStackInHand(Hand.MAIN_HAND, stack);
					var tree = (HauntedTreeBlockEntity) client.getServer().getOverworld().getBlockEntity(TREE);
					tree.hang(player, HeadType.ZOMBIE, stack);
					tree.start(true);
				});
			}
			if (ticks >= 240 + HauntRules.possessionReadyAt() + 20
				&& ticks <= 240 + HauntRules.possessionReadyAt() + HauntRules.BLINK_PERIOD
				&& !HauntRules.blinkShut(client.world.getTime(), 0)) {
				verifyPossessed(client);
				if (!hostSeen) {
					hostSeen = true;
					// Isolate the host screenshot: remove leftover faces so their glow cannot pass for host glow.
					onServer(client, () -> {
						var world = client.getServer().getOverworld();
						for (var pos : new BlockPos[] {LEFT, CENTER, RIGHT, LAMP})
							if (HauntRules.restlessFace(world.getBlockState(pos).isOf(Blocks.CARVED_PUMPKIN), world.getBlockState(pos).isOf(Blocks.JACK_O_LANTERN)))
								world.removeBlock(pos, false);
					});
				}
			}
			if (hostSeen && ticks == 240 + HauntRules.possessionReadyAt() + HauntRules.BLINK_PERIOD + 10) {
				verifyPossessed(client);
				capture(client, "06-possessed.png");
				finish(client, "PASS leftover blocks visibly animate without a zombie head; raw stays asleep; stop restores static models; hopping hosts excluded");
			}
		} catch (Throwable e) { finish(client, "FAIL " + e); }
	}

	private static void face(net.minecraft.server.world.ServerWorld world, BlockPos pos, Block block) {
		world.setBlockState(pos, block.getDefaultState().with(CarvedPumpkinBlock.FACING, Direction.SOUTH));
	}

	private static void hang(MinecraftClient client, HeadType... types) {
		var world = client.getServer().getOverworld();
		var tree = (HauntedTreeBlockEntity) world.getBlockEntity(TREE);
		var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
		for (var type : types) {
			var stack = new ItemStack(HeadItems.item(type));
			player.setStackInHand(Hand.MAIN_HAND, stack);
			tree.hang(player, type, stack);
		}
	}

	private static void verifyQuiet(MinecraftClient client) {
		check(client.world.getBlockEntity(TREE) instanceof HauntedTreeBlockEntity, "Missing client tree");
		var tree = (HauntedTreeBlockEntity) client.world.getBlockEntity(TREE);
		check(!tree.haunting(), "Tree should be quiet");
		check(client.world.getBlockState(CENTER).isOf(Blocks.CARVED_PUMPKIN), "Center carved pumpkin missing");
		check(client.world.getBlockState(RAW).isOf(Blocks.PUMPKIN), "Uncarved pumpkin missing");
		check(!HauntPumpkinEffects.replacesBlock(CENTER, client.world.getBlockState(CENTER)), "Static pumpkin did not return after stop");
	}

	private static void verifyRestless(MinecraftClient client, boolean haunting) {
		var tree = (HauntedTreeBlockEntity) client.world.getBlockEntity(TREE);
		check(tree.haunting() == haunting, "Unsynchronized haunting");
		check(client.world.getBlockState(CENTER).isOf(Blocks.CARVED_PUMPKIN), "Leftover carved pumpkin must stay a block");
		check(client.world.getBlockState(LEFT).isOf(Blocks.CARVED_PUMPKIN), "Left carved pumpkin must stay a block");
		check(client.world.getBlockState(LAMP).isOf(Blocks.JACK_O_LANTERN), "Jack-o-lantern must stay a block");
		check(client.world.getBlockState(RAW).isOf(Blocks.PUMPKIN), "Uncarved pumpkin must not wake into another block");
		check(!HauntPumpkinEffects.replacesBlock(RAW, client.world.getBlockState(RAW)), "Raw pumpkin must not animate");
		check(client.world.getBlockState(CENTER).get(CarvedPumpkinBlock.FACING) == Direction.SOUTH, "Animation changed actual block facing");
	}

	private static void verifyPossessed(MinecraftClient client) {
		var tree = (HauntedTreeBlockEntity) client.world.getBlockEntity(TREE);
		check(tree.haunting() && tree.has(HeadType.ZOMBIE), "Zombie haunt must be running");
		boolean host = !client.world.getEntitiesByClass(PossessedPropEntity.class, new net.minecraft.util.math.Box(CENTER).expand(3),
			e -> HauntRules.restlessFace(e.block().isOf(Blocks.CARVED_PUMPKIN), e.block().isOf(Blocks.JACK_O_LANTERN))).isEmpty();
		check(host, "A possessed pumpkin must keep the carved face");
	}

	private void onServer(MinecraftClient client, Runnable action) { client.getServer().execute(() -> { try { action.run(); } catch (Throwable e) { failure = e.toString(); } }); }
	private static void capture(MinecraftClient client, String name) { ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> {}); }
	private static void check(boolean ok, String reason) { if (!ok) throw new IllegalStateException(reason); }
	private void finish(MinecraftClient client, String result) {
		finished = true;
		System.out.println("[PUMPKIN-FX] " + result);
		try { Files.writeString(Path.of("result.txt"), result + "\n"); } catch (Exception e) { throw new RuntimeException(e); }
		client.scheduleStop();
	}
}
