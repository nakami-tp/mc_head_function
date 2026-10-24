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
	public static boolean sideView;
	private net.minecraft.client.particle.Particle movingFlame;
	private double movingZ;
	private int pendingScreenshots;
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
			}
			visualScenario(c);
		} catch (Throwable e) { finish(c, "FAIL " + e); }
	}
	private void visualScenario(MinecraftClient c) throws Exception {
		if (tick >= 400) {
			// Mouse motion while this isolated window loses focus must not aim the collision fixture at the floor.
			c.player.setYaw(0); c.player.prevYaw = 0; c.player.setPitch(0); c.player.prevPitch = 0;
			c.player.setHeadYaw(0); c.player.prevHeadYaw = 0; c.player.setBodyYaw(0); c.player.prevBodyYaw = 0;
		}
		if (tick == 400) server(c, () -> {
			var w = c.getServer().getOverworld();
			for (int x = 20; x <= 38; x++) for (int z = -5; z <= 15; z++)
				w.setBlockState(new BlockPos(x, 99, z), Blocks.DEEPSLATE_TILES.getDefaultState());
			var p = c.getServer().getPlayerManager().getPlayerList().getFirst();
			p.teleport(w, 25.5, 100, 0.5, 0, 0);
		});
		if (tick == 420) {
			sideView = true;
			c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			c.options.hudHidden = true;
		}
		if (tick == 425) ClientPlayNetworking.send(new HeadSkillC2SPayload(true));
		if (tick == 430) capture(c, "07-side-start.png");
		if (tick == 440) {
			var flames = flames(c);
			check(flames.size() >= 12, "network pulse did not create a visible stream: " + flames.size());
			check(flames.stream().allMatch(p -> p.getBoundingBox().getCenter().z <= 6.501), "flame exceeded six-block range");
			movingFlame = flames.stream().filter(p -> p.getBoundingBox().getCenter().z < 3).findFirst().orElseThrow();
			movingZ = movingFlame.getBoundingBox().getCenter().z;
		}
		if (tick == 442) check(movingFlame.getBoundingBox().getCenter().z > movingZ + 0.3, "flame did not travel forwards");
		if (tick == 450) capture(c, "08-side-stream.png");
		if (tick == 470) { check(HeadHud.status.blazeHeat() >= 40, "side high heat missing"); capture(c, "09-side-high.png"); }
		if (tick >= 425 && tick < 495) capture(c, String.format("motion-%03d.png", tick - 425));
		if (tick == 474) ClientPlayNetworking.send(new HeadSkillC2SPayload(false));
		if (tick == 477 || tick == 480 || tick == 484) capture(c, "10-side-wave-" + tick + ".png");
		if (tick == 500) {
			check(flames(c).isEmpty(), "flames survived release beyond their lifetime");
			sideView = false; c.options.hudHidden = false;
			c.options.setPerspective(Perspective.FIRST_PERSON);
			c.options.getParticles().setValue(net.minecraft.client.option.ParticlesMode.MINIMAL);
		}
		if (tick == 505) ClientPlayNetworking.send(new HeadSkillC2SPayload(true));
		if (tick == 535) { check(flames(c).size() >= 4, "minimal particles lost the core flame"); capture(c, "11-first-minimal.png"); }
		if (tick == 550) capture(c, "12-first-high.png");
		if (tick == 555) ClientPlayNetworking.send(new HeadSkillC2SPayload(false));
		if (tick == 580) check(flames(c).isEmpty(), "minimal flame did not expire");
		if (tick == 590) {
			c.options.getParticles().setValue(net.minecraft.client.option.ParticlesMode.ALL);
			sideView = true; c.options.hudHidden = true;
			c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			server(c, () -> {
				var w = c.getServer().getOverworld();
				for (int x = 23; x <= 28; x++) for (int y = 100; y <= 104; y++)
					w.setBlockState(new BlockPos(x, y, 3), Blocks.GLASS.getDefaultState());
			});
		}
		if (tick == 600) ClientPlayNetworking.send(new HeadSkillC2SPayload(true));
		if (tick >= 610 && tick <= 620) {
			check(!flames(c).isEmpty(), "wall test has no flame");
			check(flames(c).stream().anyMatch(p -> p.getBoundingBox().getCenter().z > 2.2), "flame did not reach wall approach");
			check(flames(c).stream().allMatch(p -> p.getBoundingBox().getCenter().z < 3.001), "flame tunneled through wall");
		}
		if (tick == 615) capture(c, "13-wall-impact.png");
		if (tick == 625) ClientPlayNetworking.send(new HeadSkillC2SPayload(false));
		if (tick == 665) {
			check(flames(c).isEmpty(), "wall flame kept emitting after low-heat release");
			server(c, () -> {
				var w = c.getServer().getOverworld();
				// Sample fresh water before it flows back to the shooter and cancels the ability.
				for (int x = 23; x <= 28; x++) for (int y = 100; y <= 104; y++)
					w.setBlockState(new BlockPos(x, y, 3), Blocks.WATER.getDefaultState());
			});
		}
		if (tick == 666) ClientPlayNetworking.send(new HeadSkillC2SPayload(true));
		if (tick >= 670 && tick <= 674) {
			check(!c.player.isTouchingWater(), "water fixture reached shooter before collision sample");
			check(!flames(c).isEmpty(), "water test has no flame");
			check(flames(c).stream().allMatch(p -> p.getBoundingBox().getCenter().z < 3.001), "flame crossed water surface");
		}
		if (tick == 672) capture(c, "14-water-impact.png");
		if (tick == 675) ClientPlayNetworking.send(new HeadSkillC2SPayload(false));
		if (tick == 710) {
			check(flames(c).isEmpty(), "water flame kept emitting");
			check(pendingScreenshots == 0, "screenshots not saved");
			finish(c, "PASS highFrames=" + highFrames + " coolingFrames=" + coolingFrames + " readySounds=" + readySounds
				+ " waveSounds=" + waveSounds + " spectator/water cancelled; moving flames, release lifetime, minimal particles, wall/water collision");
		}
	}
	/** Inspect actual particles queued by the normal S2C receiver, only in this development test mod. */
	private static java.util.List<net.minecraft.client.particle.Particle> flames(MinecraftClient c) throws Exception {
		var field = net.minecraft.client.particle.ParticleManager.class.getDeclaredField("particles");
		field.setAccessible(true);
		var result = new java.util.ArrayList<net.minecraft.client.particle.Particle>();
		for (Object queue : ((java.util.Map<?, ?>)field.get(c.particleManager)).values()) {
			for (Object value : (java.util.Queue<?>)queue) {
				if (value instanceof net.minecraft.client.particle.Particle particle && particle.isAlive()
					&& particle.getClass().getName().endsWith("BlazeBreathEffects$Flame")) result.add(particle);
			}
		}
		return result;
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
	private void capture(MinecraftClient c, String name) {
		pendingScreenshots++;
		ScreenshotRecorder.saveScreenshot(c.runDirectory, name, c.getFramebuffer(), message -> c.execute(() -> {
			pendingScreenshots--;
			try {
				Path path = c.runDirectory.toPath().resolve("screenshots").resolve(name);
				check(Files.isRegularFile(path) && Files.size(path) > 0, "missing screenshot " + name);
			} catch (Exception e) { failure = e.toString(); }
		}));
	}
	private void finish(MinecraftClient c, String message) {
		finished = true; System.out.println("[BLAZE-VALIDATION] " + message);
		try { Files.writeString(Path.of("result.txt"), message + "\n"); } catch (Exception e) { throw new RuntimeException(e); }
		c.scheduleStop();
	}
}
