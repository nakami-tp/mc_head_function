package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.client.HeadHud;
import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.net.*;
import java.nio.file.*;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

/** Actual C2S presses, synchronized HUD/health and rendered screenshots. */
public final class BlazeClientValidation implements ClientModInitializer {
	private boolean opening, setup, finished;
	private volatile boolean ready;
	private volatile String failure;
	private volatile int targetId;
	private volatile int interruptionTarget;
	private int tick, highFrames, coolingFrames, readySounds, waveSounds;
	private final long started = System.nanoTime();
	@Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }
	private void tick(MinecraftClient c) {
		if (finished) return;
		try {
			check((System.nanoTime() - started) / 1_000_000_000 < 150, "timeout tick=" + tick);
			check(failure == null, failure);
			if (!opening && c.currentScreen != null && c.getOverlay() == null) {
				opening = true; c.options.pauseOnLostFocus = false;
				c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
				c.options.getViewDistance().setValue(5); c.options.getMaxFps().setValue(60);
				c.options.getParticles().setValue(net.minecraft.client.option.ParticlesMode.ALL);
				c.options.getSoundVolumeOption(net.minecraft.sound.SoundCategory.MASTER).setValue(1.0);
				c.getSoundManager().registerListener((sound, set, range) -> {
					if (sound.getId().toString().contains("note_block.bell")) readySounds++;
					if (sound.getId().toString().contains("firecharge.use")) waveSounds++;
				});
				c.createIntegratedServerLoader().createAndStart("blaze-" + System.currentTimeMillis(),
					new LevelInfo("Blaze validation", GameMode.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(), DataConfiguration.SAFE_MODE),
					new GeneratorOptions(42, false, false), registries -> registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder(), c.currentScreen);
			}
			if (c.player == null || c.world == null || c.getServer() == null) return;
			if (!setup) {
				setup = true;
				server(c, () -> {
					var w = c.getServer().getOverworld();
					w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, c.getServer());
					w.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, c.getServer());
					w.setTimeOfDay(6000); w.setWeather(0, 100000, false, false);
					for (int x = -15; x <= 15; x++) for (int z = -10; z <= 25; z++) w.setBlockState(new BlockPos(x, 99, z), Blocks.SMOOTH_QUARTZ.getDefaultState());
					var p = c.getServer().getPlayerManager().getPlayerList().getFirst();
					p.teleport(w, 0.5, 100, 0.5, 0, 0);
					p.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.BLAZE)));
					p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(HeadItems.item(HeadType.BLAZE)));
					ready = true;
				});
				c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			}
			if (!ready) return;
			tick++;
			if (HeadHud.status != null) {
				if (HeadHud.status.blazeHeat() >= 40) highFrames++;
				if (HeadHud.status.blazeCooldown() > 0) coolingFrames++;
			}
			if (tick == 25) ClientPlayNetworking.send(new ThrowHeadC2SPayload());
			if (tick == 34) server(c, () -> {
				var p = c.getServer().getPlayerManager().getPlayerList().getFirst();
				p.teleport(c.getServer().getOverworld(), 6, 103, 8, 90, 27);
			});
			if (tick == 40) {
				capture(c, "01-trail.png");
				server(c, () -> c.getServer().getPlayerManager().getPlayerList().getFirst().teleport(c.getServer().getOverworld(), 0.5, 100, 0.5, 0, 0));
				ClientPlayNetworking.send(new HeadSkillC2SPayload(true));
				server(c, () -> {
					var w = c.getServer().getOverworld(); var cow = EntityType.COW.create(w);
					cow.refreshPositionAndAngles(0.5, 101, 13, 180, 0); cow.setAiDisabled(true); cow.setNoGravity(true);
					cow.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100); cow.setHealth(100);
					w.spawnEntity(cow); targetId = cow.getId();
				});
			}
			if (tick == 85) capture(c, "02-high-heat.png");
			if (tick == 90) { check(highFrames > 0, "high heat never synchronized"); ClientPlayNetworking.send(new HeadSkillC2SPayload(false)); }
			if (tick == 92) server(c, () -> c.getServer().getPlayerManager().getPlayerList().getFirst().teleport(c.getServer().getOverworld(), 6, 103, 8, 90, 27));
			if (tick == 94 || tick == 100 || tick == 108) capture(c, "03-wave-" + tick + ".png");
			if (tick == 125) {
				check(c.world.getEntityById(targetId) instanceof LivingEntity, "target not tracked");
				var cow = (LivingEntity)c.world.getEntityById(targetId);
				check(cow.getHealth() <= 94 && cow.getHealth() >= 92, "propagated hit did not sync once: " + cow.getHealth());
				server(c, () -> c.getServer().getPlayerManager().getPlayerList().getFirst().teleport(c.getServer().getOverworld(), 0.5, 100, 0.5, 0, 0));
				c.options.setPerspective(Perspective.FIRST_PERSON);
				ClientPlayNetworking.send(new HeadSkillC2SPayload(true));
			}
			if (tick == 150) capture(c, "04-first-person.png");
			if (tick == 213) { check(coolingFrames > 0, "overheat did not sync"); capture(c, "05-overheated.png"); }
			if (tick == 255) {
				check(HeadHud.status != null && !HeadHud.status.active() && HeadHud.status.blazeHeat() == 0, "held R restarted after overheat");
				check(readySounds > 0 && waveSounds > 0, "missing heat/wave sounds");
				ClientPlayNetworking.send(new HeadSkillC2SPayload(false));
				capture(c, "06-ended.png");
				}
			if (tick == 260) ClientPlayNetworking.send(new HeadSkillC2SPayload(true));
			if (tick == 300) spawnInterruptionTarget(c);
			if (tick == 305) {
				check(HeadHud.status.blazeHeat() >= 40, "spectator cancellation not tested at high heat");
				server(c, () -> c.getServer().getPlayerManager().getPlayerList().getFirst().changeGameMode(GameMode.SPECTATOR));
			}
			if (tick == 310) ClientPlayNetworking.send(new HeadSkillC2SPayload(false));
			if (tick == 325) {
				checkQuietTarget(c, "spectator");
				server(c, () -> {
					var p = c.getServer().getPlayerManager().getPlayerList().getFirst(); p.changeGameMode(GameMode.CREATIVE);
					p.teleport(c.getServer().getOverworld(), 0.5, 100, 0.5, 0, 0);
					c.getServer().getOverworld().getEntityById(interruptionTarget).discard();
				});
			}
			if (tick == 330) ClientPlayNetworking.send(new HeadSkillC2SPayload(true));
			if (tick == 370) spawnInterruptionTarget(c);
			if (tick == 375) {
				check(HeadHud.status.blazeHeat() >= 40, "water cancellation not tested at high heat");
				server(c, () -> {
				var p = c.getServer().getPlayerManager().getPlayerList().getFirst();
				c.getServer().getOverworld().setBlockState(p.getBlockPos(), Blocks.WATER.getDefaultState());
			});
			}
			if (tick == 380) ClientPlayNetworking.send(new HeadSkillC2SPayload(false));
			if (tick == 395) {
				checkQuietTarget(c, "water");
				finish(c, "PASS highFrames=" + highFrames + " coolingFrames=" + coolingFrames + " readySounds=" + readySounds + " waveSounds=" + waveSounds + " spectator/water cancelled");
			}
		} catch (Throwable e) { finish(c, "FAIL " + e); }
	}
	private void spawnInterruptionTarget(MinecraftClient c) {
		server(c, () -> {
			var w = c.getServer().getOverworld(); var cow = EntityType.COW.create(w);
			cow.refreshPositionAndAngles(2.5, 100, 5, 180, 0); cow.setAiDisabled(true); cow.setNoGravity(true);
			w.spawnEntity(cow); interruptionTarget = cow.getId();
		});
	}
	private void checkQuietTarget(MinecraftClient c, String cause) {
		check(HeadHud.status != null && HeadHud.status.blazeHeat() == 0 && !HeadHud.status.active(), cause + " did not cancel heat/spray");
		check(c.world.getEntityById(interruptionTarget) instanceof LivingEntity, cause + " target not tracked");
		var target = (LivingEntity)c.world.getEntityById(interruptionTarget);
		check(target.getHealth() == 10 && !target.isOnFire(), cause + " continued damage or released wave");
	}

	private void server(MinecraftClient c, Runnable r) { c.getServer().execute(() -> { try { r.run(); } catch (Throwable e) { failure = e.toString(); } }); }
	private static void check(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
	private static void capture(MinecraftClient c, String name) { ScreenshotRecorder.saveScreenshot(c.runDirectory, name, c.getFramebuffer(), message -> {}); }
	private void finish(MinecraftClient c, String message) {
		finished = true; System.out.println("[BLAZE-VALIDATION] " + message);
		try { Files.writeString(Path.of("result.txt"), message + "\n"); } catch (Exception e) { throw new RuntimeException(e); }
		c.scheduleStop();
	}
}
