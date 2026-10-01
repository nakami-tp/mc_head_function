package com.nakami.mcheadfunction.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** Real throw path, replicated roller size, repeated damage and squash recovery. */
public final class GolemClientValidation implements ClientModInitializer {
	private boolean opening, setup, finished;
	private volatile boolean ready;
	private volatile String failure;
	private volatile int bombId = -1;
	private int tick, frames;
	private volatile int victimId;
	private float firstHealth;
	private boolean quakeSeen, cameraMoved, slopeAccelerated;
	private int quakeTick;
	private float rollAt55;
	private final long started = System.nanoTime();

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
				client.createIntegratedServerLoader().createAndStart("golem-" + System.currentTimeMillis(),
					new LevelInfo("Golem regression", GameMode.CREATIVE, false, Difficulty.NORMAL, true,
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
			if (tick >= 55 && tick <= 75) {
				var entity = client.world.getEntityById(bombId);
				check(entity instanceof com.nakami.mcheadfunction.entity.ThrownHeadEntity head && head.isGiantRoller()
					&& head.getWidth() == 5F, "Client missing eight-times roller dimensions");
				var head = (com.nakami.mcheadfunction.entity.ThrownHeadEntity) entity;
				var victim = (net.minecraft.entity.LivingEntity) client.world.getEntityById(victimId);
				check(victim != null && ((com.nakami.mcheadfunction.head.HeadCrushAccess) victim).mhf$crushTicks() > 0, "Client victim not flattened");
				if (tick == 55) { firstHealth = victim.getHealth(); rollAt55 = head.getRollDistance(1); }
				if (tick == 75) {
					check(victim.getHealth() < firstHealth, "Repeated damage did not reach client");
					check(Math.abs(head.getRollDistance(1) - rollAt55 - 4) < 0.6, "Client roll phase must cover four blocks per second");
				}
				frames++;
			}
			if (tick == 60 || tick == 100 || tick == 150) capture(client, "roller-" + tick);
			if (tick == 200) {
				var head = (com.nakami.mcheadfunction.entity.ThrownHeadEntity) client.world.getEntityById(bombId);
				check(head != null && head.getRollDistance(1) > 30, "Roller must continue beyond old twenty-block range");
			}
			if (tick == 250) {
				check(client.world.getEntityById(bombId) == null, "Roller did not finish forty-block journey");
				var victim = (net.minecraft.entity.LivingEntity) client.world.getEntityById(victimId);
				check(victim != null && ((com.nakami.mcheadfunction.head.HeadCrushAccess) victim).mhf$crushTicks() > 0, "Squash must remain for ten seconds after last contact");
			}
			if (tick == 280) {
				var victim = (net.minecraft.entity.LivingEntity) client.world.getEntityById(victimId);
				check(victim != null && ((com.nakami.mcheadfunction.head.HeadCrushAccess) victim).mhf$crushTicks() == 0, "Squash must recover after ten seconds");
				server(client, () -> drop(client));
			}
			if (tick > 280 && tick < 410 && com.nakami.mcheadfunction.client.GolemQuake.amplitude(0) > 0) {
				if (!quakeSeen) { quakeSeen = true; quakeTick = tick; capture(client, "landing-impact"); }
				cameraMoved |= Math.abs(client.gameRenderer.getCamera().getPitch() - client.player.getPitch()) > 0.02;
				if (tick == quakeTick + 4 || tick == quakeTick + 10) capture(client, "quake-" + (tick - quakeTick));
			}
			if (tick == 410) {
				check(quakeSeen && cameraMoved, "Landing must deliver quake packet and move rendered camera");
				check(com.nakami.mcheadfunction.client.GolemQuake.amplitude(0) == 0, "Quake must stop");
				capture(client, "crater-after");
				server(client, () -> {
					var world = client.getServer().getOverworld();
					int holes = 0;
					for (int x = -7; x <= 7; x++) for (int z = 5; z < 28; z++)
						if (world.getBlockState(new BlockPos(x, 99, z)).isAir()) holes++;
					check(holes > 30, "High landing must leave a real crater");
					var entity = world.getEntityById(bombId);
					if (entity != null) entity.discard();
				});
			}
			if (tick == 440) server(client, () -> slope(client));
			if (tick > 455 && tick < 545) {
				var entity = client.world.getEntityById(bombId);
				if (entity != null) slopeAccelerated |= entity.getVelocity().horizontalLength() > 0.25;
			}
			if (tick == 500) capture(client, "downhill");
			if (tick == 565) {
				check(slopeAccelerated, "Downhill acceleration must reach client");
				finish(client, "PASS eightTimes=true forwardSpawn=true fourBlocksPerSecond=true fortyBlockRange=true "
					+ "crushFrames=" + frames + " crater=true quakePacket=true cameraShake=true shakeExpired=true downhill=true");
			}
		} catch (Throwable error) { finish(client, "FAIL " + error); }
	}

	private void launch(MinecraftClient client) {
		var world = client.getServer().getOverworld();
		var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
		for (int x = -9; x <= 9; x++) for (int z = -8; z <= 58; z++) for (int y = 94; y <= 99; y++)
			world.setBlockState(new BlockPos(x, y, z), (y == 99 ? Blocks.SMOOTH_QUARTZ : Blocks.STONE).getDefaultState());
		for (int x = -2; x <= 2; x++) for (int y = 100; y <= 103; y++) world.setBlockState(new BlockPos(x, y, 14), Blocks.STONE_BRICKS.getDefaultState());
		player.teleport(world, 0, 100, 0, 0, 0);
		player.refreshPositionAndAngles(0, 100, 0, 0, 0);
		player.setHeadYaw(0);
		player.setBodyYaw(0);
		player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, com.nakami.mcheadfunction.head.HeadItems.item(com.nakami.mcheadfunction.head.HeadType.IRON_GOLEM).getDefaultStack());
		var victim = net.minecraft.entity.EntityType.COW.create(world);
		victim.setAiDisabled(true);
		victim.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100);
		victim.setHealth(100);
		victim.setPosition(2.2, 100, 9);
		world.spawnEntity(victim);
		victimId = victim.getId();
		com.nakami.mcheadfunction.throwing.ThrowHandler.throwHeldHead(player);
		var thrown = world.getEntitiesByClass(com.nakami.mcheadfunction.entity.ThrownHeadEntity.class, player.getBoundingBox().expand(6), e -> true).getFirst();
		check(thrown.getPos().distanceTo(player.getPos()) > 4.4, "Spawn must be ahead of player");
		bombId = thrown.getId();
		player.teleport(world, 13, 109, 17, 135, 23);
		player.getAbilities().flying = true;
		player.sendAbilitiesUpdate();
	}

	private void drop(MinecraftClient client) {
		var world = client.getServer().getOverworld();
		var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
		var head = new com.nakami.mcheadfunction.entity.ThrownHeadEntity(world, player,
			com.nakami.mcheadfunction.head.HeadType.IRON_GOLEM, com.nakami.mcheadfunction.head.HeadType.ThrowStyle.GOLEM_ROLL);
		head.setPosition(0, 116, 7);
		head.setVelocity(0, 0, 0.2);
		world.spawnEntity(head);
		bombId = head.getId();
		player.teleport(world, 12, 110, 27, 140, 29);
	}

	private void slope(MinecraftClient client) {
		var world = client.getServer().getOverworld();
		var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
		for (int x = 20; x <= 30; x++) for (int z = -5; z <= 48; z++) {
			int top = 108 - Math.max(0, z / 4);
			for (int y = 93; y < top; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.STONE_BRICKS.getDefaultState());
		}
		var head = new com.nakami.mcheadfunction.entity.ThrownHeadEntity(world, player,
			com.nakami.mcheadfunction.head.HeadType.IRON_GOLEM, com.nakami.mcheadfunction.head.HeadType.ThrowStyle.GOLEM_ROLL);
		head.setPosition(25, 108, 0);
		head.setVelocity(0, 0, 0.2);
		world.spawnEntity(head);
		bombId = head.getId();
		player.teleport(world, 38, 114, 23, 145, 29);
	}

	private void capture(MinecraftClient client, String name) {
		ScreenshotRecorder.saveScreenshot(client.runDirectory, name + ".png", client.getFramebuffer(), message -> {});
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
