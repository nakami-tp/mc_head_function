package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.entity.HeadAmmoEntity;
import com.nakami.mcheadfunction.head.PlayerHeadAccess;
import com.nakami.mcheadfunction.wear.SkillHandler;
import com.nakami.mcheadfunction.client.CreeperAmmoEffects;
import com.nakami.mcheadfunction.client.CreeperLaunchEffects;
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
	private int tick, phase = Boolean.parseBoolean(System.getenv("MC_HEAD_EXHAUST_ONLY")) ? 6 : 0, frames;
	private int blastBaseline, attachmentBaseline;
	private int launchBaseline, flameBaseline;
	private final long started = System.nanoTime();
	private static final Direction[] FACES = {Direction.UP, Direction.SOUTH, Direction.DOWN, Direction.UP, Direction.SOUTH, Direction.DOWN};

	@Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

	private void tick(MinecraftClient client) {
		if (finished) return;
		try {
			check((System.nanoTime() - started) / 1_000_000_000 < 240, "Timed out");
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
			if (phase >= FACES.length) { exhaustScene(client); return; }
			tick++;
			if (tick == 40) {
				client.options.getParticles().setValue(phase < 3 ? net.minecraft.client.option.ParticlesMode.ALL : net.minecraft.client.option.ParticlesMode.MINIMAL);
				blastBaseline = CreeperAmmoEffects.receivedBlasts();
				attachmentBaseline = CreeperAmmoEffects.renderedAttachments();
				server(client, () -> launch(client));
			}
			if (tick >= 55 && tick <= 85) {
				var entity = client.world.getEntityById(bombId);
				check(entity instanceof HeadAmmoEntity, "Client missing bomb");
				var bomb = (HeadAmmoEntity) entity;
				check(bomb.isPlanted() && bomb.attachedFace() == FACES[phase], "Client attachment face mismatch " + phase);
				check(bomb.getVelocity().lengthSquared() < 0.001, "Client planted bomb drifts");
				frames++;
			}
			if (tick == 75) capture(client, "attached");
			if (phase >= 3 && (tick >= 40 && tick <= 65 || tick >= 215 && tick <= 255))
				capture(client, String.format("frame-%03d", tick));
			if (tick == 220) {
				var bomb = (HeadAmmoEntity) client.world.getEntityById(bombId);
				check(bomb != null && bomb.plantedTicks() >= 160 && bomb.plantedTicks() < 200, "Client missing late fuse time");
				check(CreeperAmmoEffects.renderedAttachments() > attachmentBaseline, "Attachment glow never rendered");
				capture(client, "urgent");
			}
			if (tick == 230) server(client, () -> {
				var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
				SkillHandler.onSkill(player, true);
				SkillHandler.onSkill(player, false);
				check(PlayerHeadAccess.state(player).creeperCharges >= 1, "Detonation consumed reserve");
			});
			if (tick == 232 || tick == 234 || tick == 237 || tick == 240) capture(client, "blast-" + (tick - 230));
			if (tick == 240) {
				check(client.world.getEntityById(bombId) == null, "Second R failed to remove bomb on client");
				check(CreeperAmmoEffects.receivedBlasts() == blastBaseline + 1, "Expected one server blast event");
				check(CreeperAmmoEffects.activeBlasts() == 1, "Blast ended too early");
				check(CreeperAmmoEffects.renderedBlastAt() >= client.world.getTime() - 2, "Blast was not actually rendered");
			}
			if (tick == 255) {
				check(CreeperAmmoEffects.activeBlasts() == 0, "Blast did not expire");
				check(CreeperAmmoEffects.renderedWakes() > 0, "Flight trail never rendered");
				capture(client, "clear");
				phase++; tick = 0;
			}
		} catch (Throwable error) { finish(client, "FAIL " + error); }
	}

	/** Third-person view watches the actual local player launch, not a synthetic flame spawn. */
	private void exhaustScene(MinecraftClient client) {
		tick++;
		if (tick == 1) {
			client.setCameraEntity(client.player);
			client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
			client.options.getParticles().setValue(phase == 6 ? net.minecraft.client.option.ParticlesMode.ALL : net.minecraft.client.option.ParticlesMode.MINIMAL);
			launchBaseline = CreeperLaunchEffects.receivedLaunches();
			flameBaseline = CreeperLaunchEffects.renderedFlames();
			server(client, () -> {
				var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
				var world = player.getServerWorld();
				for (int x = -4; x <= 3; x++) for (int y = 100; y <= 105; y++)
					for (int z = -1; z <= 2; z++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
				player.changeGameMode(GameMode.SURVIVAL);
				player.getAbilities().flying = false; player.sendAbilitiesUpdate();
				world.setBlockState(new BlockPos(-2, 100, 0), Blocks.STONE_BRICKS.getDefaultState());
				player.teleport(world, -1.5, 101, .5, 90, 60);
				player.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
				player.equipStack(EquipmentSlot.HEAD, Items.CREEPER_HEAD.getDefaultStack());
				PlayerHeadAccess.state(player).creeperCharges = 2;
			});
		}
		if (tick == 25) server(client, () -> {
			var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
			SkillHandler.onSkill(player, true); SkillHandler.onSkill(player, false);
			bombId = player.getServerWorld().getEntity(PlayerHeadAccess.state(player).lastAmmo).getId();
		});
		if (tick == 35) {
			server(client, () -> {
				var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
				player.teleport(player.getServerWorld(), -1.5, 101, .5, -35, 12);
			});
			client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
		}
		if (tick == 45) {
			check(client.world.getEntityById(bombId) instanceof HeadAmmoEntity bomb && bomb.isPlanted(), "Launch scene bomb not planted");
			check(CreeperLaunchEffects.receivedLaunches() == launchBaseline, "Exhaust triggered before explosion");
		}
		if (tick == 50) server(client, () -> {
			var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
			SkillHandler.onSkill(player, true); SkillHandler.onSkill(player, false);
		});
		if (tick == 57) {
			check(CreeperLaunchEffects.receivedLaunches() == launchBaseline + 1, "Missing real launch network event");
			check(CreeperLaunchEffects.activeLaunches() == 1, "Launch exhaust ended too early");
			check(CreeperLaunchEffects.renderedFlames() > flameBaseline, "Exhaust particles not drawn");
			check(!client.player.isOnGround() && client.player.getY() > 101.1, "Owner was not actually launched");
		}
		if (tick >= 40 && tick <= 90) ScreenshotRecorder.saveScreenshot(client.runDirectory,
			(phase == 6 ? "all" : "minimal") + String.format("-exhaust-%03d.png", tick), client.getFramebuffer(), message -> {});
		if (tick == 92) {
			check(CreeperLaunchEffects.activeLaunches() == 0, "Exhaust emitter did not stop");
			flameBaseline = CreeperLaunchEffects.renderedFlames();
		}
		if (tick == 105) {
			check(CreeperLaunchEffects.renderedFlames() == flameBaseline, "Exhaust particles outlived their fade");
			client.setCameraEntity(client.player);
			if (++phase == 8) finish(client, "PASS attachmentFrames=" + frames + " particles=ALL,MINIMAL blastEvents="
				+ CreeperAmmoEffects.receivedBlasts() + " exhaustScenes=2 actualPlayerFlight=2 exhaustDrawAndExpiry=2");
			else tick = 0;
		}
	}
	private void capture(MinecraftClient client, String stage) {
		ScreenshotRecorder.saveScreenshot(client.runDirectory, (phase < 3 ? "all-" : "minimal-")
			+ FACES[phase].asString() + "-" + stage + ".png", client.getFramebuffer(), message -> {});
	}

	private void launch(MinecraftClient client) {
		var world = client.getServer().getOverworld();
		var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
		for (int x = -1; x <= 1; x++) for (int y = 100; y <= 104; y++) world.setBlockState(new BlockPos(x, y, 0), Blocks.AIR.getDefaultState());
		for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) world.setBlockState(new BlockPos(x, 104, z), Blocks.AIR.getDefaultState());
		int face = phase % 3;
		if (face == 1) for (int x = -1; x <= 1; x++) for (int y = 100; y <= 103; y++) world.setBlockState(new BlockPos(x, y, 0), Blocks.STONE_BRICKS.getDefaultState());
		if (face == 2) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) world.setBlockState(new BlockPos(x, 104, z), Blocks.STONE_BRICKS.getDefaultState());
		player.teleport(world, 0.5, face == 0 ? 102 : 100, face == 1 ? 3 : 0.5, 180, face == 0 ? 90 : face == 1 ? 0 : -90);
		player.equipStack(EquipmentSlot.HEAD, Items.CREEPER_HEAD.getDefaultStack());
		PlayerHeadAccess.state(player).creeperCharges = 2;
		SkillHandler.onSkill(player, true);
		SkillHandler.onSkill(player, false);
		bombId = world.getEntity(PlayerHeadAccess.state(player).lastAmmo).getId();
		player.teleport(world, 2, face == 2 ? 100 : 101, 3.5, 153, face == 0 ? 30 : face == 1 ? 10 : -23);
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
