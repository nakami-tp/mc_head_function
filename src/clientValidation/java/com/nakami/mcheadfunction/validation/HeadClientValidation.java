package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.entity.ThrownHeadEntity;
import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.throwing.ThrowHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** ./gradlew runClientValidation. All triggers run through normal gameplay entry points. */
public final class HeadClientValidation implements ClientModInitializer {
	private boolean opening, setup, launched, finished;
	private volatile boolean ready;
	private volatile String serverFailure;
	private volatile int cowId = -1;
	private int ticks, sceneTick, phase;
	private int eats, teleports, bites;
	private int zombieFrames, biteFrames, endermanFrames, recycledCreeperFrames;
	private double minZombieY = Double.POSITIVE_INFINITY, maxZombieY;
	private boolean grounded, airborneAfterGround, landedAfterHop;
	private int equipChecks;
	private final List<String> soundLog = new ArrayList<>();
	private final long started = System.nanoTime();

	@Override public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void listen(MinecraftClient client) {
		client.getSoundManager().registerListener((sound, set, range) -> {
			String id = sound.getId().toString();
			if (id.equals("minecraft:entity.generic.eat")) {
				if (phase == 1) bites++;
				if (phase == 2) eats++;
			}
			if (id.equals("minecraft:entity.enderman.teleport") && phase == 2) teleports++;
			if (id.contains("eat") || id.contains("teleport")) {
				soundLog.add("phase=" + phase + " tick=" + sceneTick + " " + id + " at="
					+ sound.getX() + "," + sound.getY() + "," + sound.getZ());
			}
		});
	}

	private void tick(MinecraftClient client) {
		if (finished) return;
		try {
			if ((System.nanoTime() - started) / 1_000_000_000 > 180) throw new IllegalStateException("Client validation timed out");
			if (serverFailure != null) throw new IllegalStateException(serverFailure);
			if (!opening && client.currentScreen != null && client.getOverlay() == null) {
				opening = true;
				listen(client);
				client.options.pauseOnLostFocus = false;
				client.options.getViewDistance().setValue(4);
				client.options.getMaxFps().setValue(60);
				client.options.hudHidden = true;
				client.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(1.0);
				client.options.getSoundVolumeOption(SoundCategory.PLAYERS).setValue(1.0);
				client.createIntegratedServerLoader().createAndStart("head-validation-" + System.currentTimeMillis(),
					new LevelInfo("Head regression", GameMode.CREATIVE, false, Difficulty.NORMAL, true,
						new GameRules(), DataConfiguration.SAFE_MODE), new GeneratorOptions(42, false, false),
					registries -> registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder(),
					client.currentScreen);
			}
			if (client.player == null || client.world == null || client.getServer() == null) return;
			if (!setup) {
				setup = true;
				onServer(client, () -> {
					var server = client.getServer();
					var world = server.getOverworld();
					world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
					world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
					world.setTimeOfDay(6000);
					world.setWeather(0, 100000, false, false);
					for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
						world.setBlockState(new BlockPos(x, 99, z), Blocks.SMOOTH_QUARTZ.getDefaultState());
					}
					var cow = EntityType.COW.create(world);
					cow.refreshPositionAndAngles(2, 100, 0, 90, 0);
					cow.setAiDisabled(true);
					cow.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(40);
					cow.setHealth(40);
					world.spawnEntity(cow);
					cowId = cow.getId();
					camera(client);
					ready = true;
				});
			}
			if (!ready || (!launched && client.world.getEntityById(cowId) == null)) return;
			if (ticks++ < 40) return;
			if (!launched) {
				launched = true;
				phase = 1;
				onServer(client, () -> { throwAt(client, HeadType.ZOMBIE, -2, 102, 0); camera(client); });
			}
			sceneTick++;
			if (phase == 1) zombieScene(client);
			else if (phase == 2) endermanScene(client);
			else if (phase == 3) creeperScene(client);
			else if (phase == 4) equipmentScene(client);
		} catch (Throwable failure) { finish(client, "FAIL " + failure); }
	}

	private void zombieScene(MinecraftClient client) {
		for (var entity : client.world.getEntities()) if (entity instanceof ThrownHeadEntity head && head.getHeadType() == HeadType.ZOMBIE && head.isActive()) {
			zombieFrames++;
			minZombieY = Math.min(minZombieY, head.getY());
			maxZombieY = Math.max(maxZombieY, head.getY());
			check(head.getY() >= 99.99, "Client zombie penetrated floor: " + head.getY());
			if (head.getY() < 100.04) { grounded = true; if (airborneAfterGround) landedAfterHop = true; }
			if (grounded && head.getY() > 100.15) airborneAfterGround = true;
			if (head.getBiteTicks() > 0) { biteFrames++; if (biteFrames == 1) capture(client, "zombie-bite.png"); }
		}
		if (sceneTick <= 100) capture(client, String.format("zombie-%03d.png", sceneTick));
		if (sceneTick == 120) {
			check(zombieFrames > 40 && landedAfterHop && biteFrames > 0 && bites > 0,
				"Zombie state/hop/bite/sound failed: frames=" + zombieFrames + " landed=" + landedAfterHop + " biteFrames=" + biteFrames + " sounds=" + bites);
			onServer(client, () -> {
				var world = client.getServer().getOverworld();
				var cow = (LivingEntity) world.getEntityById(cowId);
				check(cow.getHealth() < cow.getMaxHealth(), "Zombie dealt no damage");
				cow.refreshPositionAndAngles(5, 100, -3, 90, 0);
				world.getEntitiesByClass(ThrownHeadEntity.class, new Box(-12, 99, -12, 12, 110, 12), e -> true).forEach(ThrownHeadEntity::discard);
				for (int x = -4; x < 4; x++) for (int z = 0; z < 2; z++) world.setBlockState(new BlockPos(x, 100, z), Blocks.STONE.getDefaultState());
				throwAt(client, HeadType.ENDERMAN, -3.5, 103, 0.5);
				camera(client);
				client.getServer().getPlayerManager().getPlayerList().getFirst().teleport(world, 0, 105, 8, 180, 35);
			});
			phase = 2; sceneTick = 0;
		}
	}

	private void endermanScene(MinecraftClient client) {
		for (var entity : client.world.getEntities()) if (entity instanceof ThrownHeadEntity head && head.getHeadType() == HeadType.ENDERMAN && head.isActive()) endermanFrames++;
		if (sceneTick <= 100) capture(client, String.format("enderman-%03d.png", sceneTick));
		if (sceneTick == 115) {
			int remaining = 0;
			for (int x = -4; x < 4; x++) for (int z = 0; z < 2; z++) if (!client.world.getBlockState(new BlockPos(x, 100, z)).isAir()) remaining++;
			check(remaining == 0 && endermanFrames > 20 && eats == 16 && teleports == 17,
				"Enderman failed: remaining=" + remaining + " frames=" + endermanFrames + " eats=" + eats + " teleportsIncludingLaunch=" + teleports);
			phase = 3; sceneTick = 0;
			onServer(client, () -> {
				// This scene checks recycling above an intact surface; destructive blasts are tested separately.
				for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
					client.getServer().getOverworld().setBlockState(new BlockPos(x, 99, z), Blocks.OBSIDIAN.getDefaultState());
				}
				throwAt(client, HeadType.CREEPER, -2, 103, 0); camera(client);
			});
		}
	}

	private void creeperScene(MinecraftClient client) {
		for (var entity : client.world.getEntities()) if (entity instanceof ItemEntity item && item.getStack().isOf(HeadItems.item(HeadType.CREEPER))) {
			check(item.getY() >= 99.99, "Recycled creeper head penetrated floor");
			recycledCreeperFrames++;
		}
		if (sceneTick <= 30) capture(client, String.format("creeper-%03d.png", sceneTick));
		if (sceneTick == 45) {
			check(recycledCreeperFrames > 10, "Creeper head did not recycle above floor");
			phase = 4; sceneTick = 0;
			prepareEquipment(client, 0);
		}
	}

	private void prepareEquipment(MinecraftClient client, int index) {
		onServer(client, () -> {
			var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
			player.changeGameMode(GameMode.SURVIVAL);
			player.teleport(client.getServer().getOverworld(), 0, 100, 8, 180, -70);
			player.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.ENDERMAN)));
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.item(index == 0 ? HeadType.ZOMBIE : HeadType.CREEPER), 64));
			player.currentScreenHandler.sendContentUpdates();
		});
	}

	private void equipmentScene(MinecraftClient client) {
		if (sceneTick == 15 || sceneTick == 50) {
			client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
		}
		if (sceneTick == 30 || sceneTick == 65) {
			HeadType expected = sceneTick == 30 ? HeadType.ZOMBIE : HeadType.CREEPER;
			ItemStack worn = client.player.getEquippedStack(EquipmentSlot.HEAD);
			check(worn.isOf(HeadItems.item(expected)) && worn.getCount() == 1 && client.player.getMainHandStack().getCount() == 63,
				"Client right-click equipment sync failed for " + expected);
			check(client.player.getInventory().count(HeadItems.item(HeadType.ENDERMAN)) == (sceneTick == 30 ? 1 : 2), "Previous head not returned to inventory");
			equipChecks++;
			capture(client, "equipment-" + expected + ".png");
			if (sceneTick == 30) prepareEquipment(client, 1);
			else finish(client, "PASS zombieFrames=" + zombieFrames + " zombieY=" + minZombieY + ".." + maxZombieY
				+ " landedAfterHop=" + landedAfterHop + " biteFrames=" + biteFrames + " biteSounds=" + bites
				+ " endermanFrames=" + endermanFrames + " eatSounds=" + eats + " teleportSoundsIncludingLaunch=" + teleports
				+ " creeperItemFrames=" + recycledCreeperFrames + " rightClickChecks=" + equipChecks);
		}
	}

	private void throwAt(MinecraftClient client, HeadType type, double x, double y, double z) {
		var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
		player.teleport(client.getServer().getOverworld(), x, y, z, 0, 90);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.item(type)));
		ThrowHandler.throwHeldHead(player);
	}

	private void camera(MinecraftClient client) {
		var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
		player.teleport(client.getServer().getOverworld(), 0, 102, 6, 180, 18);
		player.getAbilities().flying = true;
		player.sendAbilitiesUpdate();
		player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
	}

	private void onServer(MinecraftClient client, Runnable action) {
		client.getServer().execute(() -> { try { action.run(); } catch (Throwable failure) { serverFailure = failure.toString(); } });
	}
	private static void check(boolean pass, String message) { if (!pass) throw new IllegalStateException(message); }
	private static void capture(MinecraftClient client, String name) {
		ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> {});
	}
	private void finish(MinecraftClient client, String result) {
		finished = true;
		System.out.println("[HEAD-CLIENT-VALIDATION] " + result);
		try {
			Files.writeString(Path.of("result.txt"), result + "\n");
			Files.write(Path.of("sound-events.txt"), soundLog);
		} catch (Exception e) { throw new RuntimeException(e); }
		client.scheduleStop();
	}
}
