package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.head.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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

/** ./gradlew runRabbitValidation: isolated real-client gameplay regression. */
public final class RabbitClientValidation implements ClientModInitializer {
	private boolean opening, setup, finished, wasGround = true;
	private volatile boolean ready;
	private volatile String failure;
	private int ticks, jumpCount;
	private final long started = System.nanoTime();
	@Override public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}
	private void tick(MinecraftClient client) {
		if (finished) return;
		try {
			check((System.nanoTime() - started) / 1_000_000_000 < 240, "Client validation timeout jumps=" + jumpCount);
			check(failure == null, failure);
			if (!opening && client.currentScreen != null && client.getOverlay() == null) {
				opening = true;
				client.options.pauseOnLostFocus = false;
				client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
				client.options.getViewDistance().setValue(6);
				client.options.getMaxFps().setValue(60);
				client.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(1.0);
				client.createIntegratedServerLoader().createAndStart("rabbit-movement-" + System.currentTimeMillis(),
					new LevelInfo("Rabbit movement validation", GameMode.SURVIVAL, false, Difficulty.NORMAL, true, new GameRules(), DataConfiguration.SAFE_MODE),
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
					for (int x = -4; x <= 4; x++) for (int z = -5; z <= 850; z++) world.setBlockState(new BlockPos(x, 99, z), Blocks.SMOOTH_QUARTZ.getDefaultState());
					var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
					p.teleport(world, 0.5, 100, 0.5, 0, 0);
					p.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.RABBIT)));
					ready = true;
				});
				client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			}
			if (!ready) return;
			ticks++;
			rabbit(client);
		} catch (Throwable e) { finish(client, "FAIL " + e); }
	}
	private double launchSpeed, apexSpeed, launchZ, jumpPeak;
	private boolean apex;
	private final List<String> samples = new ArrayList<>();
	private void rabbit(MinecraftClient client) {
		if (ticks < 30) return;
		var player = client.player;
		client.options.forwardKey.setPressed(true);
		client.options.sprintKey.setPressed(true);
		client.options.jumpKey.setPressed(ticks >= 45 && jumpCount < 16);
		boolean ground = player.isOnGround();
		double speed = player.getVelocity().horizontalLength();
		if (wasGround && !ground) {
			jumpCount++;
			launchSpeed = speed;
			launchZ = player.getZ();
			jumpPeak = player.getY();
			apex = false;
		}
		if (!ground) {
			jumpPeak = Math.max(jumpPeak, player.getY());
			check(player.getY() <= 180.1, "Rabbit exceeded eighty-block ceiling");
			if (!apex && player.getVelocity().y <= 0) {
				apex = true;
				apexSpeed = speed;
				if (jumpCount == 8 || jumpCount == 16) ScreenshotRecorder.saveScreenshot(client.runDirectory,
					"rabbit-" + jumpCount + "-apex.png", client.getFramebuffer(), message -> {});
			}
		}
		if (!wasGround && ground) {
			var bonus = player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED)
				.getModifier(com.nakami.mcheadfunction.wear.RabbitMovement.SPEED);
			String sample = "jump=" + jumpCount + " launch=" + launchSpeed + " apex=" + apexSpeed + " landing=" + speed
				+ " height=" + (jumpPeak - 100) + " distance=" + (player.getZ() - launchZ) + " sprint=" + player.isSprinting() + " food=" + player.getHungerManager().getFoodLevel()
				+ " bonus=" + (bonus == null ? 0 : bonus.value());
			samples.add(sample);
			System.out.println("[RABBIT-MOVEMENT] " + sample);
			check(Math.abs(jumpPeak - 100 - com.nakami.mcheadfunction.rule.HeadDepthRules.rabbitHeight(jumpCount)) < 0.1, "Wrong jump height: " + sample);
			check(bonus != null && bonus.value() > 0, "Movement bonus disappeared: " + sample);
			check(player.getZ() > launchZ, "Rabbit jump moved player backward: " + sample);
			check(speed > 0.2, "Forward movement disappeared: " + sample);
			// Measured old tuning: eighth apex ~0.425, sixteenth ~0.530 blocks/tick.
			check(jumpCount != 8 || apexSpeed > 0.50, "Eighth jump needs a noticeable speed increase: " + sample);
			check(jumpCount != 16 || apexSpeed > 0.74, "Full chain needs a noticeable speed increase: " + sample);
			if (jumpCount == 16) finish(client, "PASS " + String.join("\n", samples));
		}
		wasGround = ground;
	}
	private void onServer(MinecraftClient client, Runnable task) { client.getServer().execute(() -> { try { task.run(); } catch (Throwable e) { failure = e.toString(); } }); }
	private static void check(boolean result, String reason) { if (!result) throw new IllegalStateException(reason); }
	private void finish(MinecraftClient client, String result) {
		finished = true;
		client.options.jumpKey.setPressed(false); client.options.forwardKey.setPressed(false); client.options.sprintKey.setPressed(false);
		try { Files.writeString(Path.of("result.txt"), result + "\n"); Files.write(Path.of("movement.txt"), samples); }
		catch (Exception e) { throw new RuntimeException(e); }
		System.out.println("[RABBIT-VALIDATION] " + result);
		client.scheduleStop();
	}
}
