package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.net.HeadSkillC2SPayload;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.*;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

/** ./gradlew runGoatValidation: isolated real-client gameplay regression. */
public final class GoatClientValidation implements ClientModInitializer {
	private boolean opening, setup, finished;
	private volatile boolean ready;
	private volatile String failure;
	private int ticks;
	private float rightYaw, neutralYaw;
	private final List<String> sounds = new ArrayList<>();
	private final long started = System.nanoTime();
	@Override public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}
	private void tick(MinecraftClient client) {
		if (finished) return;
		try {
			check((System.nanoTime() - started) / 1_000_000_000 < 240, "Goat validation timeout tick=" + ticks);
			check(failure == null, failure);
			if (!opening && client.currentScreen != null && client.getOverlay() == null) {
				opening = true;
				client.options.pauseOnLostFocus = false;
				client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
				client.options.getViewDistance().setValue(6);
				client.options.getMaxFps().setValue(60);
				client.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(1.0);
				client.getSoundManager().registerListener((sound, set, range) -> {
					String id = sound.getId().toString();
					if (id.contains("goat") || id.endsWith(".hit") || id.endsWith(".break")) sounds.add("tick=" + ticks + " " + id);
				});
				client.createIntegratedServerLoader().createAndStart("goat-mining-" + System.currentTimeMillis(),
					new LevelInfo("Goat mining validation", GameMode.SURVIVAL, false, Difficulty.NORMAL, true, new GameRules(), DataConfiguration.SAFE_MODE),
					new GeneratorOptions(42, false, false), registries -> registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder(), client.currentScreen);
			}
			if (client.player == null || client.world == null || client.getServer() == null) return;
			if (!setup) {
				setup = true;
				onServer(client, () -> {
					var world = client.getServer().getOverworld();
					world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, client.getServer());
					world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, client.getServer());
					world.setTimeOfDay(6000);
					world.setWeather(0, 100000, false, false);
					for (int x = -5; x <= 5; x++) for (int z = -5; z <= 30; z++) world.setBlockState(new BlockPos(x, 99, z), Blocks.SMOOTH_QUARTZ.getDefaultState());
					var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
					p.teleport(world, 0.5, 100, 0.5, 0, 0);
					p.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.GOAT)));
					for (int y = 100; y <= 101; y++) {
						world.setBlockState(new BlockPos(0, y, 3), Blocks.DIRT.getDefaultState());
						world.setBlockState(new BlockPos(0, y, 4), Blocks.STONE.getDefaultState());
						world.setBlockState(new BlockPos(0, y, 5), Blocks.DIRT.getDefaultState());
						world.setBlockState(new BlockPos(0, y, 6), Blocks.BEDROCK.getDefaultState());
					}
					ready = true;
				});
				client.options.setPerspective(Perspective.FIRST_PERSON);
			}
			if (!ready) return;
			ticks++;
			scenario(client);
		} catch (Throwable e) { finish(client, "FAIL " + e); }
	}

	private int shakeFrames, hitSounds;
	private boolean dirtGone, stoneGone, sawThrownHead;
	private volatile boolean throwVerified;
	private void scenario(MinecraftClient client) {
		check(!client.options.forwardKey.isPressed() && !client.options.jumpKey.isPressed(), "Regression must run without movement input");
		if (ticks == 15) {
			client.getSoundManager().registerListener((sound, set, range) -> {
				if (sound.getId().toString().endsWith(".hit")) hitSounds++;
			});
		}
		if (ticks == 20) press();
		if (shakeFrames == 16) client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		if (com.nakami.mcheadfunction.client.GoatImpact.ticks > 0) {
			shakeFrames++;
			if (shakeFrames == 3 || shakeFrames == 15 || shakeFrames == 23) capture(client, "goat-contact-" + shakeFrames + ".png");
		}
		if (ticks > 20) {
			dirtGone |= client.world.getBlockState(new BlockPos(0, 100, 3)).isAir()
				&& client.world.getBlockState(new BlockPos(0, 101, 3)).isAir();
			stoneGone |= client.world.getBlockState(new BlockPos(0, 100, 4)).isAir()
				&& client.world.getBlockState(new BlockPos(0, 101, 4)).isAir();
		}
		if (ticks == 135) {
			check(client.world.getBlockState(new BlockPos(0, 100, 5)).isAir() && client.world.getBlockState(new BlockPos(0, 101, 5)).isAir() && client.player.getZ() > 5, "Charge stalled after breaking a wall without forward input: " + client.player.getPos());
			check(dirtGone && stoneGone, "Charge must open both dirt and stone walls: dirt=" + dirtGone + " stone=" + stoneGone + " pos=" + client.player.getPos());
			check(client.world.getBlockState(new BlockPos(0, 100, 6)).isOf(Blocks.BEDROCK), "Bedrock broken");
			check(client.player.getZ() < 6, "Charge crossed bedrock");
			check(shakeFrames > 10 && hitSounds > 3, "Missing impact shake/audio " + shakeFrames + "/" + hitSounds);
			check(com.nakami.mcheadfunction.client.GoatImpact.ticks == 0, "Shake did not expire");
			capture(client, "goat-finished.png");
			onServer(client, () -> {
				var world = client.getServer().getOverworld();
				var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
				check(PlayerHeadAccess.state(p).goatDashTicks == 0, "Bedrock must stop charge before duration expires");
				p.teleport(world, 3.5, 100, 0.5, 0, 0);
				p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(HeadItems.item(HeadType.GOAT)));
				for (int y = 100; y <= 102; y++) world.setBlockState(new BlockPos(3, y, 4), Blocks.STONE.getDefaultState());
			});
		}
		if (ticks == 150) {
			check(client.player.getMainHandStack().isOf(HeadItems.item(HeadType.GOAT)), "Throw setup did not sync");
			ClientPlayNetworking.send(new com.nakami.mcheadfunction.net.ThrowHeadC2SPayload());
		}
		if (ticks >= 150) for (var entity : client.world.getEntities()) {
			if (entity instanceof com.nakami.mcheadfunction.entity.ThrownHeadEntity) sawThrownHead = true;
		}
		if (ticks == 165) capture(client, "goat-thrown-recovered.png");
		if (ticks == 175) onServer(client, () -> {
			var world = client.getServer().getOverworld();
			var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
			check(p.getMainHandStack().isEmpty(), "Throw must consume held goat head");
			var area = new net.minecraft.util.math.Box(2, 99, 0, 5, 104, 5);
			check(world.getEntitiesByClass(com.nakami.mcheadfunction.entity.ThrownHeadEntity.class, area, e -> true).isEmpty(), "Goat projectile must end on wall impact");
			check(world.getEntitiesByClass(ItemEntity.class, area, e -> e.getStack().isOf(HeadItems.item(HeadType.GOAT))).size() == 1, "Goat must recover as exactly one item after wall impact");
			throwVerified = true;
		});
		if (ticks == 185) {
			check(sawThrownHead && throwVerified, "Real goat throw and wall recovery did not complete");
			onServer(client, () -> {
				var world = client.getServer().getOverworld();
				for (int x = -30; x <= 30; x++) for (int z = 30; z <= 100; z++) world.setBlockState(new BlockPos(x, 99, z), Blocks.SMOOTH_QUARTZ.getDefaultState());
				var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
				p.teleport(world, 0.5, 100, 35, 0, 0);
				PlayerHeadAccess.state(p).goatCooldown = 0;
			});
			client.options.setPerspective(Perspective.FIRST_PERSON);
		}

		if (ticks == 200) press();
		if (ticks == 205) {
			check(com.nakami.mcheadfunction.client.GoatChargeControl.active(), "Charge did not lock view");
			float yaw = client.player.getYaw(), pitch = client.player.getPitch();
			client.player.changeLookDirection(1200, 600);
			check(client.player.getYaw() == yaw && client.player.getPitch() == pitch, "Mouse must not turn view during charge");
			client.options.rightKey.setPressed(true);
		}
		if (ticks == 215) {
			rightYaw = client.player.getYaw();
			check(rightYaw > 8 && rightYaw < 25, "Right key must steer gradually: " + rightYaw);
			check(client.player.input.movementSideways == 0, "Steering must not add strafe movement");
			check(client.player.getVelocity().x < 0, "Charge movement must turn with view");
			capture(client, "goat-steering-right.png");
			client.options.leftKey.setPressed(true);
		}
		if (ticks == 220) {
			neutralYaw = client.player.getYaw();
			check(Math.abs(neutralYaw - rightYaw) <= 4, "Both keys should cancel steering");
			client.options.rightKey.setPressed(false);
			client.options.leftKey.setPressed(false);
		}
		if (ticks == 230) {
			check(Math.abs(client.player.getYaw() - neutralYaw) < 0.01, "Released keys must hold heading");
			client.options.leftKey.setPressed(true);
		}
		if (ticks == 240) {
			check(client.player.getYaw() < neutralYaw - 8, "Left key must steer back");
			client.options.leftKey.setPressed(false);
			capture(client, "goat-steering-left.png");
			press();
		}
		if (ticks == 248) {
			check(!com.nakami.mcheadfunction.client.GoatChargeControl.active(), "Stopping must unlock view");
			float yaw = client.player.getYaw();
			client.player.changeLookDirection(100, 20);
			check(client.player.getYaw() != yaw, "Mouse must work again after stopping");
			onServer(client, () -> PlayerHeadAccess.state(client.getServer().getPlayerManager().getPlayerList().getFirst()).goatCooldown = 0);
		}
		if (ticks == 255) press();
		if (ticks == 260) {
			check(com.nakami.mcheadfunction.client.GoatChargeControl.active(), "Second charge must relock view");
			onServer(client, () -> client.getServer().getPlayerManager().getPlayerList().getFirst().equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY));
		}
		if (ticks == 268) {
			check(!com.nakami.mcheadfunction.client.GoatChargeControl.active(), "Unequipping must unlock view");
			finish(client, "PASS continuous breaking, throw recovery, mouse lock, A/D steering, both/released keys, stop/unequip unlock; shakeFrames=" + shakeFrames + " hitSounds=" + hitSounds);
		}

	}
	private static void press() { ClientPlayNetworking.send(new HeadSkillC2SPayload(true)); ClientPlayNetworking.send(new HeadSkillC2SPayload(false)); }
	private void onServer(MinecraftClient client, Runnable task) { client.getServer().execute(() -> { try { task.run(); } catch (Throwable e) { failure = e.toString(); } }); }
	private static void capture(MinecraftClient client, String name) { ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> {}); }
	private static void check(boolean result, String reason) { if (!result) throw new IllegalStateException(reason); }
	private void finish(MinecraftClient client, String result) {
		finished = true; client.options.jumpKey.setPressed(false);
		client.options.leftKey.setPressed(false); client.options.rightKey.setPressed(false);
		System.out.println("[GOAT-VALIDATION] " + result);
		try { Files.writeString(Path.of("result.txt"), result + "\n"); Files.write(Path.of("sound-events.txt"), sounds); } catch (Exception e) { throw new RuntimeException(e); }
		client.scheduleStop();
	}
}
