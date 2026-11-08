package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.net.HeadSkillC2SPayload;
import com.nakami.mcheadfunction.wear.GoatCharge;
import com.nakami.mcheadfunction.client.GoatChargeEffects;
import com.nakami.mcheadfunction.net.GoatEffectsS2CPayload;
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
	public static int showcaseCamera;
	public static float renderedTilt;
	public static float renderedInsideLean;
	private boolean opening, setup, finished;
	private volatile boolean ready;
	private volatile String failure;
	private int ticks;
	private float rightYaw, neutralYaw;
	private final List<String> sounds = new ArrayList<>();
	private final long started = System.nanoTime();
	@Override public void onInitializeClient() {
		GoatSmoothnessValidation.register();
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getOverworld().getEntityById(monsterId) instanceof net.minecraft.entity.mob.CreeperEntity target) {
				// Keep the fixture focused on collision/landing rather than retaliation.
				target.setTarget(null); target.setFuseSpeed(-1);
			}
		});
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
				client.options.getParticles().setValue(net.minecraft.client.option.ParticlesMode.ALL);
				client.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(1.0);
				client.getSoundManager().registerListener((sound, set, range) -> {
					String id = sound.getId().toString();
					if (id.contains("goat") || id.contains("minecart") || id.endsWith(".hit") || id.endsWith(".break")) sounds.add("tick=" + ticks + " " + id);
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
					if (GoatSmoothnessValidation.ENABLED) {
						for (int x = -48; x <= 48; x++) for (int z = -48; z <= 96; z++)
							world.setBlockState(new BlockPos(x, 99, z), (Math.floorMod(x, 8) == 0 || Math.floorMod(z, 8) == 0
								? Blocks.LIGHT_GRAY_CONCRETE : Blocks.GRAY_CONCRETE).getDefaultState());
					}
					for (int y = 100; !GoatSmoothnessValidation.ENABLED && y <= 101; y++) {
						world.setBlockState(new BlockPos(0, y, 3), Blocks.DIRT.getDefaultState());
						world.setBlockState(new BlockPos(0, y, 4), Blocks.STONE.getDefaultState());
						world.setBlockState(new BlockPos(0, y, 5), Blocks.DIRT.getDefaultState());
						world.setBlockState(new BlockPos(0, y, 6), Blocks.BEDROCK.getDefaultState());
					}
					ready = true;
				});
				client.options.setPerspective(GoatSmoothnessValidation.perspective());
			}
			if (!ready) return;
			// Charge input intentionally ignores unfocused windows. Ask the OS to
			// focus this disposable client so the real keyboard path stays active.
			if (!client.isWindowFocused()) org.lwjgl.glfw.GLFW.glfwFocusWindow(client.getWindow().getHandle());
			ticks++;
			if (GoatSmoothnessValidation.ENABLED) {
				String result = GoatSmoothnessValidation.tick(client, ticks);
				if (result != null) finish(client, result);
			} else scenario(client);
		} catch (Throwable e) { finish(client, "FAIL " + e); }
	}

	private int shakeFrames, hitSounds, sequence, crushParticles, driftParticles, driftSounds;
	private boolean dirtGone, stoneGone, sawThrownHead;
	private float normalSteerYaw;
	private volatile Boolean driftSpeedOk;
	private volatile String driftSpeedDetail;
	private volatile boolean throwVerified;
	private volatile int monsterId;
	private int monsterHitTick, hitPacketsBefore, dustFramesBefore, hitFramesBefore;
	private int stoppedRunPackets, stoppedDriftPackets;
	private boolean sawAirborneMonster, sawMinimalHit;
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
		if (ticks == 242) {
			check(com.nakami.mcheadfunction.client.GoatChargeControl.active(), "Skill key must not stop an active charge");
			stopCharge(client);
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
			onServer(client, () -> {
				var world = client.getServer().getOverworld();
				for (int x = -5; x <= 5; x++) for (int z = 110; z <= 250; z++) world.setBlockState(new BlockPos(x, 99, z), Blocks.SMOOTH_QUARTZ.getDefaultState());
				world.setBlockState(new BlockPos(0, 100, 115), Blocks.OAK_LEAVES.getDefaultState().with(net.minecraft.block.LeavesBlock.PERSISTENT, true));
				world.setBlockState(new BlockPos(0, 101, 115), Blocks.OAK_LEAVES.getDefaultState().with(net.minecraft.block.LeavesBlock.PERSISTENT, true));
				world.setBlockState(new BlockPos(0, 100, 116), Blocks.GLASS.getDefaultState());
				world.setBlockState(new BlockPos(0, 101, 116), Blocks.GLASS.getDefaultState());
				world.setBlockState(new BlockPos(0, 100, 117), Blocks.OAK_DOOR.getDefaultState()
					.with(net.minecraft.block.DoorBlock.HALF, net.minecraft.block.enums.DoubleBlockHalf.LOWER)
					.with(net.minecraft.block.DoorBlock.FACING, net.minecraft.util.math.Direction.NORTH));
				world.setBlockState(new BlockPos(0, 101, 117), Blocks.OAK_DOOR.getDefaultState()
					.with(net.minecraft.block.DoorBlock.HALF, net.minecraft.block.enums.DoubleBlockHalf.UPPER)
					.with(net.minecraft.block.DoorBlock.FACING, net.minecraft.util.math.Direction.NORTH));
				world.setBlockState(new BlockPos(0, 100, 118), Blocks.OAK_FENCE_GATE.getDefaultState());
				world.setBlockState(new BlockPos(0, 100, 119), Blocks.BAMBOO.getDefaultState());
				world.setBlockState(new BlockPos(0, 101, 119), Blocks.BAMBOO.getDefaultState());
				var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
				p.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.GOAT)));
				PlayerHeadAccess.state(p).goatCooldown = 0;
				p.teleport(world, 0.5, 100, 112.5, 0, 10);
			});
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		}
		if (ticks >= 276 && ticks <= 320) {
			crushParticles = Math.max(crushParticles, particles(client));
			capture(client, String.format("sequence-%03d.png", ++sequence));
		}
		if (ticks == 276) {
			check(client.world.getBlockState(new BlockPos(0, 100, 115)).isOf(Blocks.OAK_LEAVES), "Crush course did not sync");
			capture(client, "goat-crush-start.png");
			press();
		}
		if (ticks == 292) capture(client, "goat-crush-peak.png");
		if (ticks == 320) {
			check(client.world.getBlockState(new BlockPos(0, 100, 115)).isAir() && client.world.getBlockState(new BlockPos(0, 101, 115)).isAir(), "Leaves must shatter without a mining pause");
			check(client.world.getBlockState(new BlockPos(0, 100, 116)).isAir() && client.world.getBlockState(new BlockPos(0, 101, 116)).isAir(), "Glass must shatter on contact");
			check(client.world.getBlockState(new BlockPos(0, 100, 117)).isAir() && client.world.getBlockState(new BlockPos(0, 101, 117)).isAir(), "Wooden door must shatter on contact");
			check(client.world.getBlockState(new BlockPos(0, 100, 118)).isAir(), "Fence gate must shatter on contact");
			check(client.world.getBlockState(new BlockPos(0, 100, 119)).isAir() && client.world.getBlockState(new BlockPos(0, 101, 119)).isAir(), "Bamboo must shatter on contact");
			check(client.player.getZ() > 119.4, "Charge must keep speed through brittle blocks: " + client.player.getPos());
			check(com.nakami.mcheadfunction.client.GoatChargeControl.active(), "Brittle blocks must not stop the charge");
			check(crushParticles >= 8, "Crushing must spawn break particles on the client: " + crushParticles);
			capture(client, "goat-crush-end.png");
			onServer(client, () -> {
				var world = client.getServer().getOverworld();
				for (int x = -2; x <= 2; x++) {
					world.setBlockState(new BlockPos(x, 100, 168), Blocks.DIRT.getDefaultState());
					for (int z = 169; z <= 190; z++) world.setBlockState(new BlockPos(x, 100, z), Blocks.SMOOTH_QUARTZ.getDefaultState());
				}
				resetDash(client, 0.5, 100, 164.5, 0, 12);
			});
		}
		if (ticks == 336) {
			check(client.world.getBlockState(new BlockPos(0, 100, 168)).isOf(Blocks.DIRT), "Step course did not sync");
			check(!com.nakami.mcheadfunction.client.GoatChargeControl.active(), "Previous charge must stop before the step course");
			capture(client, "goat-step-start.png");
			press();
		}
		if (ticks == 341) check(com.nakami.mcheadfunction.client.GoatChargeControl.active(), "Step charge did not start");
		if (ticks == 365) {
			check(client.world.getBlockState(new BlockPos(0, 100, 168)).isOf(Blocks.DIRT), "A one-block step must not be mined: " + client.world.getBlockState(new BlockPos(0, 100, 168)));
			check(client.player.getY() >= 100.9, "Charge must step onto the one-block dirt: " + client.player.getPos());
			check(client.player.getZ() >= 167.6, "Charge must keep moving over the step: " + client.player.getPos());
			capture(client, "goat-step-up.png");
			onServer(client, () -> {
				for (int x = -30; x <= 30; x++) for (int z = 195; z <= 235; z++) client.getServer().getOverworld().setBlockState(new BlockPos(x, 99, z), Blocks.GRAY_CONCRETE.getDefaultState());
				resetDash(client, 0.5, 100, 205.5, 0, 0);
			});
			client.options.setPerspective(Perspective.FIRST_PERSON);
		}
		if (ticks == 381) press();
		if (ticks == 385) {
			check(com.nakami.mcheadfunction.client.GoatChargeControl.active(), "Normal steer charge did not start");
			client.options.rightKey.setPressed(true);
		}
		if (ticks == 395) {
			normalSteerYaw = client.player.getYaw();
			check(normalSteerYaw > 8 && normalSteerYaw < 25, "Normal right steer must stay gradual: " + normalSteerYaw);
			capture(client, "goat-steer-normal.png");
			client.options.rightKey.setPressed(false);
			onServer(client, () -> resetDash(client, 0.5, 100, 205.5, 0, 0));
		}
		if (ticks == 411) press();
		if (ticks == 415) {
			check(com.nakami.mcheadfunction.client.GoatChargeControl.active(), "Drift charge did not start");
			client.options.sneakKey.setPressed(true);
			client.options.rightKey.setPressed(true);
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			showcaseCamera = 1;
		}
		if (ticks >= 415 && ticks <= 431) {
			driftParticles = Math.max(driftParticles, particles(client));
			if (sounds.stream().anyMatch(s -> s.contains("minecart"))) driftSounds++;
		}
		if (ticks == 425) {
			float driftYaw = client.player.getYaw();
			check(driftYaw > 45 && driftYaw < 85, "Sneak+A/D must snap the turn to about 6 degrees a tick: " + driftYaw + " vs normal " + normalSteerYaw);
			check(driftYaw > normalSteerYaw + 20, "Drift must turn clearly faster than normal steering");
			check(!client.player.isSneaking() && !client.player.input.sneaking, "Drift must not crouch the charge hitbox");
			check(driftParticles >= 6, "Drift must spawn hoof-smoke particles: " + driftParticles);
			check(driftSounds > 0, "Drift must play a friction sound");
			check(Math.abs(com.nakami.mcheadfunction.client.GoatDriftPose.lean(client.player, 1)) > 12, "Drift must lean the character into the bend");
			check(renderedTilt > .12F, "The real rendered player matrix must tilt into the drift: " + renderedTilt);
			check(renderedInsideLean > .12F, "Rendered body must lean toward the inside of the corner: " + renderedInsideLean);
			capture(client, "goat-drift-peak.png");
			onServer(client, () -> {
				var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
				var state = PlayerHeadAccess.state(p);
				driftSpeedOk = state.goatDrifting && state.goatSpeed > 0.5;
				driftSpeedDetail = "drifting=" + state.goatDrifting + " goatSpeed=" + state.goatSpeed;
				double travel = Math.toDegrees(Math.atan2(-p.getVelocity().x, p.getVelocity().z));
				check(Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees((float) (p.getYaw() - travel))) > 12, "Real client movement must slide outside body facing: " + p.getYaw() + " travel=" + travel);
			});
		}
		if (ticks == 428) {
			check(Boolean.TRUE.equals(driftSpeedOk), "Sneak+A/D must set server drift while the charge keeps accelerating: " + driftSpeedDetail);
			client.options.sneakKey.setPressed(false);
			client.options.rightKey.setPressed(false);
			onServer(client, () -> GoatCharge.stop(client.getServer().getPlayerManager().getPlayerList().getFirst()));
		}
		if (ticks >= 411 && ticks <= 435) capture(client, String.format("drift-sequence-%03d.png", ticks - 411));
		if (ticks == 436) {
			check(sequence == 45, "Crush sequence must capture 45 frames, got " + sequence);
			check(GoatChargeEffects.received(GoatEffectsS2CPayload.DRIFT) > 0 && GoatChargeEffects.drawnDust() > 0, "Directional drift dust must arrive and actually render");
			stoppedRunPackets = GoatChargeEffects.received(GoatEffectsS2CPayload.RUN);
			stoppedDriftPackets = GoatChargeEffects.received(GoatEffectsS2CPayload.DRIFT);
			prepareMonster(client);
		}
		if (ticks == 448) {
			check(GoatChargeEffects.received(GoatEffectsS2CPayload.RUN) == stoppedRunPackets && GoatChargeEffects.received(GoatEffectsS2CPayload.DRIFT) == stoppedDriftPackets, "Stopping must end trail emissions");
			check(effectParticles(client) == 0, "Short-lived hoof dust must clear after stopping");
			check(Math.abs(com.nakami.mcheadfunction.client.GoatDriftPose.lean(client.player, 1)) < .2, "Stopping must clear the drift pose");
			beginMonsterCharge(client);
		}
		if (ticks > 448 && ticks < 510 || ticks > 558 && ticks < 620) {
			var monster = client.world.getEntityById(monsterId);
			check(monster instanceof net.minecraft.entity.mob.CreeperEntity, "Monster must be tracked before collision");
			var target = (net.minecraft.entity.LivingEntity) monster;
			if (target.getHealth() < 20 && monsterHitTick == 0) {
				monsterHitTick = ticks;
				check(target.getHealth() == 16, "Charge must deal four damage, once: " + target.getHealth());
			}
			if (monsterHitTick > 0) {
				sawAirborneMonster |= monster.getY() > 100.2;
				if (ticks == monsterHitTick + 2) capture(client, ticks < 510 ? "goat-monster-impact.png" : "goat-monster-impact-minimal.png");
				if (ticks >= monsterHitTick && ticks <= monsterHitTick + 32)
					capture(client, String.format(ticks < 510 ? "hit-sequence-%03d.png" : "minimal-hit-sequence-%03d.png", ticks - monsterHitTick));
				if (ticks == monsterHitTick + 6) {
					check(sawAirborneMonster, "Monster must visibly leave the ground");
					check(GoatChargeEffects.received(GoatEffectsS2CPayload.HIT) == hitPacketsBefore + 1, "One server hit must produce one client burst");
					check(GoatChargeEffects.drawnHits() > hitFramesBefore && GoatChargeEffects.drawnDust() > dustFramesBefore, "Impact sparks and ground dust must actually render");
					check(effectParticles(client) > 0, "Impact cue must remain visible through the contact window");
					if (ticks > 558) sawMinimalHit = true;
					stopCharge(client);
				}
			}
		}
		if (ticks == 510 || ticks == 620) {
			check(monsterHitTick > 0 && sawAirborneMonster, "Charge must collide with and launch the real monster");
			onServer(client, () -> {
				var target = (net.minecraft.entity.LivingEntity) client.getServer().getOverworld().getEntityById(monsterId);
				check(target != null && target.getHealth() > 0 && target.getHealth() <= 16 && GoatCharge.isStunned(target), "Landed monster must retain damage and a live stun: target=" + target + (target == null ? "" : " health=" + target.getHealth() + " ground=" + target.isOnGround() + " stun=" + GoatCharge.isStunned(target)));
			});
			capture(client, ticks == 510 ? "goat-monster-stunned.png" : "goat-monster-stunned-minimal.png");
		}
		if (ticks == 548 || ticks == 658) onServer(client, () -> {
			var target = (net.minecraft.entity.LivingEntity) client.getServer().getOverworld().getEntityById(monsterId);
			check(target != null && !GoatCharge.isStunned(target), "Landing stun must expire");
		});
		if (ticks == 550) {
			client.options.getParticles().setValue(net.minecraft.client.option.ParticlesMode.MINIMAL);
			prepareMonster(client);
		}
		if (ticks == 558) beginMonsterCharge(client);
		if (ticks == 666) {
			check(sawMinimalHit, "Monster impact must also render at minimal particles");
			check(effectParticles(client) == 0 && com.nakami.mcheadfunction.client.GoatImpact.ticks == 0, "Impact dust and camera impulse must expire");
			showcaseCamera = 0;
			client.options.setPerspective(Perspective.FIRST_PERSON);
			onServer(client, () -> resetDash(client, .5, 100, 205.5, 0, 8));
		}
		if (ticks == 680) press();
		if (ticks == 685) {
			client.options.sneakKey.setPressed(true);
			client.options.leftKey.setPressed(true);
		}
		if (ticks == 695) {
			check(com.nakami.mcheadfunction.client.GoatDriftPose.lean(client.player, 1) < -12, "Left drift must mirror the right lean");
			check(com.nakami.mcheadfunction.client.GoatDriftPose.cameraRoll(1) < -2, "First-person drift must bank gently");
			float cameraRightY = new org.joml.Vector3f(1, 0, 0).rotate(client.gameRenderer.getCamera().getRotation()).y;
			check(Math.abs(cameraRightY) > .025, "Actual first-person camera must roll with the drift: " + cameraRightY);
			check(effectParticles(client) > 0, "Minimal particles must retain drift dust");
			capture(client, "goat-drift-first-person.png");
			client.options.sneakKey.setPressed(false);
			client.options.leftKey.setPressed(false);
		}
		if (ticks == 710) {
			check(Math.abs(com.nakami.mcheadfunction.client.GoatDriftPose.cameraRoll(1)) < .1, "Releasing Shift must level the camera");
			capture(client, "goat-drift-release.png");
			client.options.sneakKey.setPressed(true);
		}
		if (ticks == 720) {
			check(Math.abs(com.nakami.mcheadfunction.client.GoatDriftPose.lean(client.player, 1)) < .2, "Shift without steering must not drift");
			client.options.sneakKey.setPressed(false);
			stopCharge(client);
		}
		if (ticks == 738) {
			check(effectParticles(client) == 0, "All drift dust must expire after stopping");
			finish(client, "PASS monster ram/launch/landing stun/expiry, hit render ALL+MINIMAL, sideways drift+rendered lean, mirrored left drift+actual camera roll+release, minimal drift, stop cleanup, crush-through, step-up, hard-wall mining, throw recovery, mouse lock; hitPackets=" + GoatChargeEffects.received(GoatEffectsS2CPayload.HIT) + " dustDraws=" + GoatChargeEffects.drawnDust() + " hitDraws=" + GoatChargeEffects.drawnHits() + " renderedTilt=" + renderedTilt + " shakeFrames=" + shakeFrames + " crushParticles=" + crushParticles + " driftParticles=" + driftParticles);
		}

	}
	private void prepareMonster(MinecraftClient client) {
		showcaseCamera = 2;
		monsterHitTick = 0; sawAirborneMonster = false;
		onServer(client, () -> {
			var world = client.getServer().getOverworld();
			var old = world.getEntityById(monsterId);
			if (old != null) old.discard();
			for (int x = -8; x <= 8; x++) for (int z = 235; z <= 275; z++) world.setBlockState(new BlockPos(x, 99, z), Blocks.GRAY_CONCRETE.getDefaultState());
			resetDash(client, .5, 100, 240.5, 0, 8);
			var target = EntityType.CREEPER.create(world);
			check(target != null, "Could not spawn collision target");
			target.refreshPositionAndAngles(.5, 100, 246, 180, 0);
			// NoAI suppresses natural airborne movement for this mob. Zero goal
			// movement/aggro instead, keeping real knockback and gravity active.
			target.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0);
			target.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_FOLLOW_RANGE).setBaseValue(0);
			target.setPersistent();
			world.spawnEntity(target); monsterId = target.getId();
		});
		client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
	}
	private void beginMonsterCharge(MinecraftClient client) {
		check(client.world.getEntityById(monsterId) != null, "Real monster must sync before charge starts");
		hitPacketsBefore = GoatChargeEffects.received(GoatEffectsS2CPayload.HIT);
		dustFramesBefore = GoatChargeEffects.drawnDust(); hitFramesBefore = GoatChargeEffects.drawnHits();
		press();
	}
	private static int effectParticles(MinecraftClient client) {
		return particles(client, true);
	}
	private static void press() { ClientPlayNetworking.send(new HeadSkillC2SPayload(true)); ClientPlayNetworking.send(new HeadSkillC2SPayload(false)); }
	private void stopCharge(MinecraftClient client) {
		onServer(client, () -> GoatCharge.stop(client.getServer().getPlayerManager().getPlayerList().getFirst()));
	}
	private static void resetDash(MinecraftClient client, double x, double y, double z, float yaw, float pitch) {
		var world = client.getServer().getOverworld();
		var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
		GoatCharge.stop(p);
		PlayerHeadAccess.state(p).goatCooldown = 0;
		p.teleport(world, x, y, z, yaw, pitch);
	}
	private void onServer(MinecraftClient client, Runnable task) { client.getServer().execute(() -> { try { task.run(); } catch (Throwable e) { failure = e.toString(); } }); }
	private static void capture(MinecraftClient client, String name) { ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> {}); }
	private static void check(boolean result, String reason) { if (!result) throw new IllegalStateException(reason); }
	private void finish(MinecraftClient client, String result) {
		showcaseCamera = 0;
		finished = true; client.options.jumpKey.setPressed(false);
		client.options.leftKey.setPressed(false); client.options.rightKey.setPressed(false);
		client.options.sneakKey.setPressed(false);
		System.out.println("[GOAT-VALIDATION] " + result);
		try { Files.writeString(Path.of("result.txt"), result + "\n"); Files.write(Path.of("sound-events.txt"), sounds); } catch (Exception e) { throw new RuntimeException(e); }
		client.scheduleStop();
	}

	@SuppressWarnings("unchecked")
	private static int particles(MinecraftClient client) {
		return particles(client, false);
	}
	@SuppressWarnings("unchecked")
	private static int particles(MinecraftClient client, boolean goatOnly) {
		try {
			var field = net.minecraft.client.particle.ParticleManager.class.getDeclaredField("particles");
			field.setAccessible(true);
			int count = 0;
			for (var list : ((Map<?, ?>) field.get(client.particleManager)).values())
				for (var particle : (Collection<?>) list)
					if (!goatOnly || particle.getClass().getName().contains("GoatChargeEffects$")) count++;
			return count;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
