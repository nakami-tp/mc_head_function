package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.client.BlazeHeadAppearance;
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
	private volatile int secondTargetId;
	private volatile int interruptionTarget;
	public static boolean sideView;
	public static boolean coreView;
	private volatile int coreTarget, chainTarget, coreEdgeTarget, coreOutsideTarget;
	private int coreBlasts;
	private net.minecraft.client.particle.Particle movingFlame;
	private double movingZ;
	private int pendingScreenshots;
	private int tick, highFrames, coolingFrames, readySounds, waveSounds, wallApproachFrames;
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
			if (tick == 25) {
				ClientPlayNetworking.send(new ThrowHeadC2SPayload());
				server(c, () -> {
					var w = c.getServer().getOverworld();
					var p = c.getServer().getPlayerManager().getPlayerList().getFirst();
					for (double x : new double[]{-1.5, 2.5})
						com.nakami.mcheadfunction.blaze.BlazeFire.trail(w, p,
							new net.minecraft.util.math.Vec3d(x, 101, 4.5), new net.minecraft.util.math.Vec3d(x, 101, 16));
				});
			}
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
					cow.refreshPositionAndAngles(-1.5, 103, 13, 180, 0); cow.setAiDisabled(true); cow.setNoGravity(true);
					cow.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100); cow.setHealth(100);
					w.spawnEntity(cow); targetId = cow.getId();
					var other = EntityType.COW.create(w);
					other.refreshPositionAndAngles(2.5, 103, 13, 180, 0); other.setAiDisabled(true); other.setNoGravity(true);
					other.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100); other.setHealth(100);
					w.spawnEntity(other); secondTargetId = other.getId();
				});
			}
			if (tick == 85) capture(c, "02-high-heat.png");
			if (tick == 90) {
				check(highFrames > 0, "high heat never synchronized");
				check(BlazeHeadAppearance.renderedHigh > 0, "high-heat head was not drawn");
				ClientPlayNetworking.send(new HeadSkillC2SPayload(false));
			}
			if (tick == 92) server(c, () -> c.getServer().getPlayerManager().getPlayerList().getFirst().teleport(c.getServer().getOverworld(), 12, 104, 9, 90, 15));
			if (tick == 94 || tick == 100 || tick == 108) capture(c, "03-wave-" + tick + ".png");
			if (tick == 100) {
				var pillars = kind(c, BlazeFlameS2CPayload.Kind.PILLAR);
				check(pillars.stream().anyMatch(p -> p.getBoundingBox().getCenter().y < 100.6), "erupting fire detached from ground");
				check(pillars.stream().anyMatch(p -> p.getBoundingBox().getCenter().y > 103.5), "chain reaction has no tall fire wall");
			}
			if (tick == 125) {
				check(c.world.getEntityById(targetId) instanceof LivingEntity, "target not tracked");
				check(c.world.getEntityById(secondTargetId) instanceof LivingEntity other && other.getHealth() <= 94, "second trail pillar did not hit raised target");
				var cow = (LivingEntity)c.world.getEntityById(targetId);
				check(cow.getHealth() <= 94 && cow.getHealth() >= 92, "propagated hit did not sync once: " + cow.getHealth());
				server(c, () -> c.getServer().getPlayerManager().getPlayerList().getFirst().teleport(c.getServer().getOverworld(), 0.5, 100, 0.5, 0, 0));
				c.options.setPerspective(Perspective.FIRST_PERSON);
				ClientPlayNetworking.send(new HeadSkillC2SPayload(true));
			}
			if (tick == 150) capture(c, "04-first-person.png");
			if (tick == 175) ClientPlayNetworking.send(new HeadSkillC2SPayload(false));
			if (tick == 208) c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			if (tick == 213) {
				check(HeadHud.status != null && HeadHud.status.blazeHeat() == 0, "surge release did not clear heat");
				capture(c, "05-surge-ended.png");
			}
			if (tick == 255) {
				check(HeadHud.status != null && !HeadHud.status.active() && HeadHud.status.blazeHeat() == 0, "release did not end the surge");
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
		if (tick == 477) {
			var burst = kind(c, BlazeFlameS2CPayload.Kind.BURST);
			check(!burst.isEmpty(), "release did not send a forward burst");
			check(burst.stream().anyMatch(p -> p.getBoundingBox().getCenter().z > 4.5), "release lacks forward momentum");
			check(burst.stream().allMatch(p -> p.getBoundingBox().getCenter().y < 102.5), "release rose before travelling forward");
		}
		if (tick == 480) check(kind(c, BlazeFlameS2CPayload.Kind.BURST).stream()
			.anyMatch(p -> p.getBoundingBox().getCenter().z > 9.5), "released fire did not reach the extended range promptly");
		if (tick == 477 || tick == 480 || tick == 484) capture(c, "10-side-wave-" + tick + ".png");
		if (tick == 510) {
			check(flames(c).isEmpty(), "flames survived release beyond their lifetime");
			sideView = false; c.options.hudHidden = false;
			c.options.setPerspective(Perspective.FIRST_PERSON);
			c.options.getParticles().setValue(net.minecraft.client.option.ParticlesMode.MINIMAL);
		}
		if (tick == 515) ClientPlayNetworking.send(new HeadSkillC2SPayload(true));
		if (tick == 535) { check(flames(c).size() >= 4, "minimal particles lost the core flame"); capture(c, "11-first-minimal.png"); }
		if (tick == 550) capture(c, "12-first-high.png");
		if (tick == 560) ClientPlayNetworking.send(new HeadSkillC2SPayload(false));
		if (tick == 563) {
			check(kind(c, BlazeFlameS2CPayload.Kind.BURST).size() >= 20, "minimal particles lost release body");
			capture(c, "12b-first-release-minimal.png");
		}
		if (tick == 588) check(flames(c).isEmpty(), "minimal flame did not expire");
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
			if (flames(c).stream().anyMatch(p -> p.getBoundingBox().getCenter().z > 2.2)) wallApproachFrames++;
			check(flames(c).stream().allMatch(p -> p.getBoundingBox().getCenter().z < 3.001), "flame tunneled through wall");
		}
		if (tick == 620) check(wallApproachFrames > 0, "flame never reached wall approach during the collision sample");
		if (tick == 615) capture(c, "13-wall-impact.png");
		if (tick == 645) ClientPlayNetworking.send(new HeadSkillC2SPayload(false));
		if (tick >= 647 && tick <= 653) check(kind(c, BlazeFlameS2CPayload.Kind.BURST).stream()
			.allMatch(p -> p.getBoundingBox().getCenter().z < 3.001), "released fire crossed the wall");
		if (tick == 648) capture(c, "13b-wall-release.png");
		if (tick == 665) {
			check(flames(c).isEmpty(), "wall flame kept emitting after release");
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
		}
		coreScenario(c);
		if (tick == 1640) {
			check(pendingScreenshots == 0, "screenshots not saved");
			finish(c, "PASS highFrames=" + highFrames + " coolingFrames=" + coolingFrames
				+ " renderedHigh=" + BlazeHeadAppearance.renderedHigh + " renderedCooling=" + BlazeHeadAppearance.renderedCooling
				+ " readySounds=" + readySounds
				+ " waveSounds=" + waveSounds + " spectator/water cancelled; moving flames, release lifetime, minimal particles, wall/water collision; molten core charge/flight/blast/chain/minimal/cancellation; shared 160-tick stop and recast without independent cooldown");
		}
	}
	private void coreFixture(MinecraftClient c, boolean firstPerson) {
		sideView = false; coreView = !firstPerson;
		c.options.hudHidden = true;
		c.options.setPerspective(firstPerson ? Perspective.FIRST_PERSON : Perspective.THIRD_PERSON_BACK);
		c.options.getParticles().setValue(firstPerson ? net.minecraft.client.option.ParticlesMode.MINIMAL : net.minecraft.client.option.ParticlesMode.ALL);
		server(c, () -> {
			var w = c.getServer().getOverworld(); var p = c.getServer().getPlayerManager().getPlayerList().getFirst();
			for (int x = 18; x <= 38; x++) for (int z = -5; z <= 26; z++) {
				w.setBlockState(new BlockPos(x, 99, z), Blocks.DEEPSLATE_TILES.getDefaultState());
				for (int y = 100; y <= 105; y++) w.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
			}
			for (var entity : w.getEntitiesByClass(LivingEntity.class, new net.minecraft.util.math.Box(18, 100, -5, 38, 110, 27), e -> e != p)) entity.discard();
			p.teleport(w, 25.5, 100, 0.5, 0, 0);
			// Independent visual fixture after proving the full shared stop and a normal recast.
			var state = PlayerHeadAccess.state(p); state.blazeCooldown = 0;
			var cow = EntityType.COW.create(w); cow.refreshPositionAndAngles(25.5, 100, 13.5, 180, 0);
			cow.setAiDisabled(true); cow.setNoGravity(true);
			cow.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100); cow.setHealth(100);
			w.spawnEntity(cow); coreTarget = cow.getId();
			for (int i = 0; i < 2; i++) {
				var side = EntityType.COW.create(w);
				side.refreshPositionAndAngles(i == 0 ? 33.5 : 36.5, 100, 13.5, 180, 0);
				side.setAiDisabled(true); side.setNoGravity(true);
				side.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100); side.setHealth(100);
				w.spawnEntity(side);
				if (i == 0) coreEdgeTarget = side.getId(); else coreOutsideTarget = side.getId();
			}
			if (!firstPerson) {
				com.nakami.mcheadfunction.blaze.BlazeFire.trail(w, p, new net.minecraft.util.math.Vec3d(25.5, 101, 10), new net.minecraft.util.math.Vec3d(25.5, 101, 24));
				var raised = EntityType.COW.create(w); raised.refreshPositionAndAngles(25.5, 103, 24.5, 180, 0);
				raised.setAiDisabled(true); raised.setNoGravity(true);
				raised.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100); raised.setHealth(100);
				w.spawnEntity(raised); chainTarget = raised.getId();
			}
		});
	}
	private void coreScenario(MinecraftClient c) throws Exception {
		if (tick >= 720) c.options.sneakKey.setPressed(false);
		if (tick == 720) coreFixture(c, false);
		if (tick == 750 || tick == 1015 || tick == 1210 || tick == 1400 || tick == 1510) ClientPlayNetworking.send(new HeadSkillC2SPayload(true));
		if (tick >= 740 && tick < 1140) capture(c, String.format("core-motion-%03d.png", tick - 739));
		if (tick == 820 || tick == 841) capture(c, "core-charge-" + tick + ".png");
		if (tick == 842) {
			check(com.nakami.mcheadfunction.client.BlazeCoreEffects.rendered(BlazeFlameS2CPayload.Kind.CORE_CHARGE) > 0, "core charge was not drawn");
			server(c, () -> {
				var p = c.getServer().getPlayerManager().getPlayerList().getFirst();
				check(!p.isSneaking() && PlayerHeadAccess.state(p).blazeCoreCharge == 30, "standing R hold did not transition into a full core");
			});
		}
		if (tick == 845 || tick == 1010 || tick == 1110 || tick == 1305 || tick == 1470 || tick == 1610) ClientPlayNetworking.send(new HeadSkillC2SPayload(false));
		if (tick == 850 || tick == 855 || tick == 860 || tick == 867) capture(c, "core-impact-" + tick + ".png");
		if (tick == 860) {
			var field = com.nakami.mcheadfunction.client.BlazeCoreEffects.class.getDeclaredField("bursts");
			field.setAccessible(true);
			boolean expanded = false;
			for (Object burst : (java.util.List<?>) field.get(null)) {
				var payload = burst.getClass().getDeclaredField("payload"); payload.setAccessible(true);
				var event = (BlazeFlameS2CPayload) payload.get(burst);
				if (event.kind() == BlazeFlameS2CPayload.Kind.CORE_BLAST && event.range() == 10) expanded = true;
			}
			check(expanded, "client did not receive the ten-block blast radius");
		}
		if (tick == 880) {
			check(com.nakami.mcheadfunction.client.BlazeCoreEffects.rendered(BlazeFlameS2CPayload.Kind.CORE_FLY) > 0, "flying core was not drawn");
			coreBlasts = com.nakami.mcheadfunction.client.BlazeCoreEffects.rendered(BlazeFlameS2CPayload.Kind.CORE_BLAST);
			check(coreBlasts > 0, "core blast was not drawn");
			check(c.world.getEntityById(coreTarget) instanceof LivingEntity cow && cow.getHealth() <= 70, "heavy core damage did not synchronize");
			checkExpandedBlast(c);
			check(c.world.getEntityById(chainTarget) instanceof LivingEntity cow && cow.getHealth() < 100, "core did not chain along owned fire trail");
			check(HeadHud.status != null && HeadHud.status.blazeCooldown() > 120 && HeadHud.status.blazeHeat() == 0, "eight-second overheat did not synchronize after firing");
		}
		if (tick == 891) ClientPlayNetworking.send(new HeadSkillC2SPayload(true));
		if (tick == 965) {
			check(coolingFrames > 0 && BlazeHeadAppearance.renderedCooling > 0, "overheated head was not drawn");
			check(HeadHud.status.blazeCooldown() > 0 && !HeadHud.status.active(), "core stop ended before eight seconds");
			capture(c, "core-eight-second-overheat.png");
		}
		if (tick == 1009) check(HeadHud.status.blazeCooldown() == 0 && !HeadHud.status.active() && HeadHud.status.blazeHeat() == 0, "holding through recovery restarted fire");
		if (tick == 1020) check(HeadHud.status.active() && HeadHud.status.blazeHeat() > 0, "fresh R did not resume spray after eight seconds");
		if (tick == 1107) {
			server(c, () -> check(PlayerHeadAccess.state(c.getServer().getPlayerManager().getPlayerList().getFirst()).blazeCoreCharge == 30,
				"an independent ultimate cooldown blocked the second core after recovery"));
			capture(c, "core-ready-again.png");
		}
		if (tick == 1132) capture(c, "core-recast-blast.png");
		if (tick == 1135) {
			check(com.nakami.mcheadfunction.client.BlazeCoreEffects.rendered(BlazeFlameS2CPayload.Kind.CORE_BLAST) > coreBlasts, "second core did not fire after shared recovery");
			coreBlasts = com.nakami.mcheadfunction.client.BlazeCoreEffects.rendered(BlazeFlameS2CPayload.Kind.CORE_BLAST);
		}
		if (tick == 1200) coreFixture(c, true);
		if (tick == 1300) capture(c, "core-first-charge-minimal.png");
		if (tick == 1310) capture(c, "core-first-flight-minimal.png");
		if (tick == 1317) capture(c, "core-first-blast-minimal.png");
		if (tick == 1340) {
			check(com.nakami.mcheadfunction.client.BlazeCoreEffects.rendered(BlazeFlameS2CPayload.Kind.CORE_BLAST) > coreBlasts, "minimal setting lost the core blast");
			check(c.world.getEntityById(coreTarget) instanceof LivingEntity cow && cow.getHealth() <= 70, "minimal core did not hit");
			checkExpandedBlast(c);
			coreBlasts = com.nakami.mcheadfunction.client.BlazeCoreEffects.rendered(BlazeFlameS2CPayload.Kind.CORE_BLAST);
			check(!com.nakami.mcheadfunction.client.BlazeCoreEffects.active(), "core effects survived beyond their lifetime");
		}
		if (tick == 1390) coreFixture(c, true);
		if (tick == 1465) server(c, () -> {
			var state = PlayerHeadAccess.state(c.getServer().getPlayerManager().getPlayerList().getFirst());
			check(state.blazeCoreCharging && state.blazeCoreCharge < 30 && !state.blazeSpraying, "R did not stop spraying for compression");
		});
		if (tick == 1473) check(!kind(c, BlazeFlameS2CPayload.Kind.BURST).isEmpty(), "incomplete compression did not fall back to the ready surge");
		if (tick == 1495) {
			check(!com.nakami.mcheadfunction.client.BlazeCoreEffects.active(), "partial compression left a charging core");
			check(com.nakami.mcheadfunction.client.BlazeCoreEffects.rendered(BlazeFlameS2CPayload.Kind.CORE_BLAST) == coreBlasts, "partial compression fired a core");
		}
		if (tick == 1605) server(c, () -> {
			var p = c.getServer().getPlayerManager().getPlayerList().getFirst();
			p.getServerWorld().setBlockState(p.getBlockPos(), Blocks.WATER.getDefaultState());
		});
		if (tick == 1625) {
			check(!com.nakami.mcheadfunction.client.BlazeCoreEffects.active(), "water interruption left a core");
			check(com.nakami.mcheadfunction.client.BlazeCoreEffects.rendered(BlazeFlameS2CPayload.Kind.CORE_BLAST) == coreBlasts, "water interruption exploded");
		}
	}
	private void checkExpandedBlast(MinecraftClient c) {
		check(c.world.getEntityById(coreEdgeTarget) instanceof LivingEntity cow && cow.getHealth() <= 84 && cow.getHealth() > 65,
			"eight-block off-axis target did not receive expanded falloff damage");
		check(c.world.getEntityById(coreOutsideTarget) instanceof LivingEntity cow && cow.getHealth() == 100 && !cow.isOnFire(),
			"blast exceeded its ten-block boundary");
	}
	private static java.util.List<net.minecraft.client.particle.Particle> kind(MinecraftClient c, BlazeFlameS2CPayload.Kind kind) throws Exception {
		var result = new java.util.ArrayList<net.minecraft.client.particle.Particle>();
		for (var particle : flames(c)) {
			var field = particle.getClass().getDeclaredField("kind");
			field.setAccessible(true);
			if (field.get(particle) == kind) result.add(particle);
		}
		return result;
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
