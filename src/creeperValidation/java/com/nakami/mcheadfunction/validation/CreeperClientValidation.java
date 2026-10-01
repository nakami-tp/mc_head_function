package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.entity.HeadAmmoEntity;
import com.nakami.mcheadfunction.head.PlayerHeadAccess;
import com.nakami.mcheadfunction.wear.SkillHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** Real client + integrated server; server attachment state must arrive before capture. */
public final class CreeperClientValidation implements ClientModInitializer {
	private boolean opening, setup, finished;
	private volatile boolean ready;
	private volatile String failure;
	private volatile int bombId = -1;
	private int tick, phase, frames;
	private final long started = System.nanoTime();
	private static final Direction[] FACES = {Direction.UP, Direction.SOUTH, Direction.DOWN};

	@Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

	private void tick(MinecraftClient client) {
		if (finished) return;
		try {
			check((System.nanoTime() - started) / 1_000_000_000 < 180, "Timed out");
			check(failure == null, failure);
			if (!opening && client.currentScreen != null && client.getOverlay() == null) {
				opening = true;
				client.options.pauseOnLostFocus = false;
				client.options.getViewDistance().setValue(4);
				client.options.getMaxFps().setValue(60);
				client.options.hudHidden = true;
				client.createIntegratedServerLoader().createAndStart("creeper-" + System.currentTimeMillis(),
					new LevelInfo("Creeper regression", GameMode.CREATIVE, false, Difficulty.NORMAL, true,
						new GameRules(), DataConfiguration.SAFE_MODE), new GeneratorOptions(42, false, false),
					registries -> registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder(), client.currentScreen);
			}
			if (client.player == null || client.world == null || client.getServer() == null) return;
			if (!setup) {
				setup = true;
				server(client, () -> {
					var world = client.getServer().getOverworld();
					world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, client.getServer());
					world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, client.getServer());
					world.setTimeOfDay(6000);
					world.setWeather(0, 100000, false, false);
					for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) world.setBlockState(new BlockPos(x, 99, z), Blocks.SMOOTH_QUARTZ.getDefaultState());
					client.getServer().getPlayerManager().getPlayerList().getFirst().teleport(world, 2, 102, 4, 150, 25);
					ready = true;
				});
			}
			if (!ready) return;
			tick++;
			if (tick == 40) server(client, () -> launch(client));
			if (tick >= 55 && tick <= 85) {
				var entity = client.world.getEntityById(bombId);
				check(entity instanceof HeadAmmoEntity, "Client missing bomb");
				var bomb = (HeadAmmoEntity) entity;
				check(bomb.isPlanted() && bomb.attachedFace() == FACES[phase], "Client attachment face mismatch " + phase);
				check(bomb.getVelocity().lengthSquared() < 0.001, "Client planted bomb drifts");
				frames++;
			}
			if (tick == 75) ScreenshotRecorder.saveScreenshot(client.runDirectory, "attached-" + FACES[phase].asString() + ".png", client.getFramebuffer(), message -> {});
			if (tick == 90) server(client, () -> {
				var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
				SkillHandler.onSkill(player, true);
				SkillHandler.onSkill(player, false);
				check(PlayerHeadAccess.state(player).creeperCharges >= 1, "Detonation consumed reserve");
			});
			if (tick == 100) {
				check(client.world.getEntityById(bombId) == null, "Second R failed to remove bomb on client");
				if (++phase == FACES.length) finish(client, "PASS attachmentFrames=" + frames + " faces=UP,SOUTH,DOWN secondPressDetonations=3");
				else tick = 0;
			}
		} catch (Throwable error) { finish(client, "FAIL " + error); }
	}

	private void launch(MinecraftClient client) {
		var world = client.getServer().getOverworld();
		var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
		for (int x = -1; x <= 1; x++) for (int y = 100; y <= 104; y++) world.setBlockState(new BlockPos(x, y, 0), Blocks.AIR.getDefaultState());
		if (phase == 1) for (int x = -1; x <= 1; x++) for (int y = 100; y <= 103; y++) world.setBlockState(new BlockPos(x, y, 0), Blocks.STONE_BRICKS.getDefaultState());
		if (phase == 2) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) world.setBlockState(new BlockPos(x, 104, z), Blocks.STONE_BRICKS.getDefaultState());
		player.teleport(world, 0.5, phase == 0 ? 102 : 100, phase == 1 ? 3 : 0.5, 180, phase == 0 ? 90 : phase == 1 ? 0 : -90);
		player.equipStack(EquipmentSlot.HEAD, Items.CREEPER_HEAD.getDefaultStack());
		PlayerHeadAccess.state(player).creeperCharges = 2;
		SkillHandler.onSkill(player, true);
		SkillHandler.onSkill(player, false);
		bombId = world.getEntity(PlayerHeadAccess.state(player).lastAmmo).getId();
		player.teleport(world, 2, phase == 2 ? 100 : 101, 3.5, 153, phase == 0 ? 30 : phase == 1 ? 10 : -23);
		player.getAbilities().flying = true;
		player.sendAbilitiesUpdate();
	}

	private void server(MinecraftClient client, Runnable action) {
		client.getServer().execute(() -> { try { action.run(); } catch (Throwable error) { failure = error.toString(); } });
	}
	private static void check(boolean pass, String message) { if (!pass) throw new IllegalStateException(message); }
	private void finish(MinecraftClient client, String result) {
		finished = true;
		try { Files.writeString(Path.of("result.txt"), result + "\n"); } catch (Exception error) { throw new RuntimeException(error); }
		client.scheduleStop();
	}
}
