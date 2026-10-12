package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.haunt.*;
import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.rule.HauntRules;
import java.nio.file.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

/** Fixed camera, real server hang actions and synchronized visual thresholds. */
public final class TreeEffectsValidation implements ClientModInitializer {
	private static final BlockPos TREE = new BlockPos(0, -60, 0);
	private boolean opening, setup, finished;
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
				client.options.getFov().setValue(55);
				client.options.getParticles().setValue(ParticlesMode.ALL);
				client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
				client.createIntegratedServerLoader().createAndStart("tree-fx-" + System.currentTimeMillis(),
					new LevelInfo("Tree effects validation", GameMode.SPECTATOR, false, Difficulty.PEACEFUL, true, new GameRules(), DataConfiguration.SAFE_MODE),
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
					world.setTimeOfDay(14000);
					world.setWeather(0, 100000, false, false);
					world.setBlockState(TREE, HauntBlocks.TREE.getDefaultState());
					var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
					player.teleport(world, 12, -56, 18, 146, 0);
					hangTo(client, 3);
					ready = true;
				});
			}
			if (!ready) return;
			ticks++;
			if (ticks == 70) { verify(client, 3, false); capture(client, "01-three-quiet.png"); onServer(client, () -> hangTo(client, 4)); }
			if (ticks == 150) { verify(client, 4, false); capture(client, "02-four-ash.png"); onServer(client, () -> hangTo(client, 6)); }
			if (ticks == 230) { verify(client, 6, false); capture(client, "03-six-souls.png"); onServer(client, () -> hangTo(client, 9)); }
			if (ticks == 300) { verify(client, 9, false); onServer(client, () -> hangTo(client, 10)); }
			if (ticks >= 295 && ticks <= 365) capture(client, String.format("transition-%03d.png", ticks - 294));
			if (ticks == 320) capture(client, "04-awakening.png");
			if (ticks == 390) { verify(client, 10, false); capture(client, "05-ten-glow-night.png"); onServer(client, () -> client.getServer().getOverworld().setTimeOfDay(1000)); }
			if (ticks == 430) { verify(client, 10, false); capture(client, "06-ten-glow-day.png"); client.options.getParticles().setValue(ParticlesMode.MINIMAL); }
			if (ticks == 500) { capture(client, "07-minimal-particles.png"); onServer(client, () -> {
				var world = client.getServer().getOverworld();
				world.setTimeOfDay(18000);
				((HauntedTreeBlockEntity) world.getBlockEntity(TREE)).start(true);
			}); }
			if (ticks == 520) { verify(client, 10, true); capture(client, "08-haunt-awakening.png"); }
			if (ticks == 580) { verify(client, 10, true); capture(client, "09-haunting.png"); onServer(client, () -> {
				var world = client.getServer().getOverworld();
				var tree = (HauntedTreeBlockEntity) world.getBlockEntity(TREE);
				tree.stop();
				var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
				while (tree.hungCount() > 3) tree.take(player);
			}); }
			if (ticks == 660) {
				verify(client, 3, false); capture(client, "10-quiet-restored.png");
				onServer(client, () -> {
					var world = client.getServer().getOverworld();
					world.setTimeOfDay(1000);
					hangTo(client, 10);
					client.getServer().getPlayerManager().getPlayerList().getFirst().teleport(world, 6, -56, 9, 146, 0);
				});
			}
			if (ticks == 740) {
				verify(client, 10, false); capture(client, "11-pixel-embers-day-close.png");
				client.options.getParticles().setValue(ParticlesMode.ALL);
				onServer(client, () -> client.getServer().getOverworld().setTimeOfDay(14000));
			}
			if (ticks == 800) {
				verify(client, 10, false); capture(client, "12-pixel-embers-night-close.png");
				finish(client, "PASS server hang/take -> client 3/4/6/9/10 thresholds, day/night glow, minimal particles, haunting start/stop, transition and pixel sprite close-up captures");
			}
		} catch (Throwable e) { finish(client, "FAIL " + e); }
	}
	private static void hangTo(MinecraftClient client, int count) {
		var world = client.getServer().getOverworld();
		var tree = (HauntedTreeBlockEntity) world.getBlockEntity(TREE);
		var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
		for (var type : HeadType.values()) {
			if (tree.hungCount() >= count) break;
			if (tree.has(type)) continue;
			var stack = new ItemStack(HeadItems.item(type));
			player.setStackInHand(Hand.MAIN_HAND, stack);
			tree.hang(player, type, stack);
		}
	}
	private static void verify(MinecraftClient client, int count, boolean haunting) {
		check(client.world.getBlockEntity(TREE) instanceof HauntedTreeBlockEntity, "Missing client tree");
		var tree = (HauntedTreeBlockEntity) client.world.getBlockEntity(TREE);
		check(tree.hungCount() == count && tree.haunting() == haunting, "Unsynchronized tree at count " + count);
		check(HauntRules.headsGlow(count, haunting) == (count >= 10 || haunting), "Wrong glow threshold");
	}
	private void onServer(MinecraftClient client, Runnable action) { client.getServer().execute(() -> { try { action.run(); } catch (Throwable e) { failure = e.toString(); } }); }
	private static void capture(MinecraftClient client, String name) { ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> {}); }
	private static void check(boolean ok, String reason) { if (!ok) throw new IllegalStateException(reason); }
	private void finish(MinecraftClient client, String result) {
		finished = true;
		System.out.println("[TREE-FX] " + result);
		try { Files.writeString(Path.of("result.txt"), result + "\n"); } catch (Exception e) { throw new RuntimeException(e); }
		client.scheduleStop();
	}
}
