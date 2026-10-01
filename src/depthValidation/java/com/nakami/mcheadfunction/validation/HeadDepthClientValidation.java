package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.client.HeadProgressHud;
import com.nakami.mcheadfunction.net.HeadSkillC2SPayload;
import com.nakami.mcheadfunction.wear.GoatCharge;
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
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

/** ./gradlew runDepthValidation: isolated real-client gameplay regression. */
public final class HeadDepthClientValidation implements ClientModInitializer {
	private boolean opening, setup, finished, wasGround = true;
	private volatile boolean ready;
	private volatile String failure;
	private int airTicks;
	private double sprintLaunchGain, maxChainAirSpeed;
	private int ticks, phase, jumpCount, targetId = -1, stunticks, marks;
	private double launchY, peak, maximumHeight, previousHorizontalSpeed;
	private final List<Double> heights = new ArrayList<>();
	private final List<String> sounds = new ArrayList<>();
	private final List<Integer> goats = new ArrayList<>();
	private int ramSounds, gallopSounds, stunSounds, beeSounds;
	private final long started = System.nanoTime();
	@Override public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}
	private void tick(MinecraftClient client) {
		if (finished) return;
		try {
			check((System.nanoTime() - started) / 1_000_000_000 < 240, "Client validation timeout phase=" + phase + " jumps=" + jumpCount);
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
					if (id.contains("goat") || id.contains("gallop") || id.contains("bell") || id.contains("beehive") || id.contains("rabbit")) sounds.add("phase=" + phase + " tick=" + ticks + " " + id);
					if (id.endsWith("goat.ram_impact")) ramSounds++;
					if (id.endsWith("horse.gallop")) gallopSounds++;
					if (id.endsWith("bell.resonate")) stunSounds++;
					if (id.endsWith("beehive.work")) beeSounds++;
				});
				client.createIntegratedServerLoader().createAndStart("head-depth-" + System.currentTimeMillis(),
					new LevelInfo("Head depth validation", GameMode.SURVIVAL, false, Difficulty.NORMAL, true, new GameRules(), DataConfiguration.SAFE_MODE),
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
					for (int x = -25; x <= 25; x++) for (int z = -25; z <= 85; z++) world.setBlockState(new BlockPos(x, 99, z), Blocks.SMOOTH_QUARTZ.getDefaultState());
					var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
					p.teleport(world, 0.5, 100, 0.5, 0, 0);
					p.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.RABBIT)));
					ready = true;
				});
				client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			}
			if (!ready) return;
			ticks++;
			if (phase == 0) rabbit(client);
			else if (phase == 1) goat(client);
			else if (phase == 2) bee(client);
			else if (phase == 3 && ticks > 15) {
				check(HeadProgressHud.progress != null && HeadProgressHud.progress.points()[HeadType.PIG.ordinal()] == 1000, "Mastery HUD failed to sync");
				check(com.nakami.mcheadfunction.progression.HeadMastery.mastered(client.player, HeadType.PIG), "Client domain mastery failed to sync");
				capture(client, "depth-mastery.png");
				finish(client, "PASS rabbitHeights=" + heights + " max=" + maximumHeight + " sprintGain=" + sprintLaunchGain + " maxAirSpeed=" + maxChainAirSpeed + " stunFrames=" + stunticks + " targetFrames=" + marks + " ramSounds=" + ramSounds + " gallopSounds=" + gallopSounds + " stunSounds=" + stunSounds + " beeSounds=" + beeSounds);
			}
		} catch (Throwable e) { finish(client, "FAIL " + e); }
	}
	private void rabbit(MinecraftClient client) {
		if (ticks >= 20 && jumpCount == 0) client.options.forwardKey.setPressed(true);
		if (ticks < 30) return;
		check(HeadLookups.worn(client.player) == HeadType.RABBIT, "Rabbit equipment did not sync");
		client.options.jumpKey.setPressed(jumpCount < 16);
		boolean ground = client.player.isOnGround();
		if (wasGround && !ground) {
			jumpCount++;
			airTicks = 0;
			if (jumpCount == 1) {
				double speed = client.player.getVelocity().horizontalLength();
				System.out.println("[RABBIT-SPEED] before=" + previousHorizontalSpeed + " after=" + speed);
				check(previousHorizontalSpeed > 0.1 && speed >= previousHorizontalSpeed * 0.9,
					"Rabbit launch lost forward momentum: before=" + previousHorizontalSpeed + " after=" + speed);
				client.options.forwardKey.setPressed(false);
			}
			if (jumpCount == 2) {
				sprintLaunchGain = client.player.getVelocity().horizontalLength() - previousHorizontalSpeed;
				check(sprintLaunchGain > 0.12, "Rabbit sprint launch lost vanilla boost: " + sprintLaunchGain);
				client.options.forwardKey.setPressed(false);
				client.options.sprintKey.setPressed(false);
				client.player.setSprinting(false);
			}
			launchY = 100;
			peak = client.player.getY();
		}
		if (!ground) {
			airTicks++;
			if (jumpCount == 16) {
				client.options.forwardKey.setPressed(true);
				if (airTicks >= 50) {
					maxChainAirSpeed = client.player.getVelocity().horizontalLength();
					check(maxChainAirSpeed > 0.35, "Rabbit speed bonus missing in air: " + maxChainAirSpeed);
				}
			}
		}
		if (!ground) { peak = Math.max(peak, client.player.getY()); maximumHeight = Math.max(maximumHeight, client.player.getY() - launchY); }
		check(client.player.getY() <= 180.1, "Rabbit exceeded 80-block ceiling: " + client.player.getY());
		if (jumpCount == 16 && !ground && Math.abs(client.player.getVelocity().y) < 0.08) capture(client, "depth-rabbit-apex.png");
		if (!wasGround && ground) {
			heights.add(peak - launchY);
			check(Math.abs(peak - launchY - com.nakami.mcheadfunction.rule.HeadDepthRules.rabbitHeight(jumpCount)) < 0.1,
				"Incorrect height for jump " + jumpCount + ": " + heights);
			if (jumpCount == 1) {
				client.options.forwardKey.setPressed(true);
				client.options.sprintKey.setPressed(true);
			}
			check(client.player.getHealth() >= 19, "Rabbit leap caused fall damage");
			if (jumpCount >= 16) {
				check(maximumHeight > 79 && maximumHeight <= 80.1, "Sixteenth jump did not reach 80 blocks: " + heights);
				System.out.println("[HEAD-DEPTH-RABBIT] heights=" + heights + " sprintGain=" + sprintLaunchGain + " maxAirSpeed=" + maxChainAirSpeed);
				client.options.forwardKey.setPressed(false);
				client.options.jumpKey.setPressed(false);
				phase = 1; ticks = 0;
				onServer(client, () -> {
					var world = client.getServer().getOverworld();
					var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
					p.teleport(world, 0.5, 100, 0.5, 0, 0);
					p.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.GOAT)));
					for (int i = 0; i < 3; i++) {
						var cow = EntityType.COW.create(world);
						cow.refreshPositionAndAngles(0.5, 100, 4 + i, 180, 0);
						cow.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100);
						cow.setHealth(100);
						world.spawnEntity(cow); goats.add(cow.getId());
					}
				});
			}
		}
		previousHorizontalSpeed = client.player.getVelocity().horizontalLength();
		wasGround = ground;
	}
	private void goat(MinecraftClient client) {
		if (ticks == 20) press();
		if (ticks == 27 || ticks == 32) capture(client, "depth-charge-" + ticks + ".png");
		if (ticks == 35) client.player.setYaw(75);
		if (ticks == 36) check(Math.abs(client.player.getVelocity().x) < 0.2, "Goat turned abruptly without inertia");
		for (int id : goats) if (client.world.getEntityById(id) instanceof LivingEntity entity && GoatCharge.isStunned(entity)) {
			stunticks++;
			if (stunticks == 1) capture(client, "depth-stunned-first.png");
		}
		if (ticks == 45) {
			press();
			onServer(client, () -> client.getServer().getPlayerManager().getPlayerList().getFirst().teleport(client.getServer().getOverworld(), 5, 102, 11, 130, 15));
			client.options.setPerspective(Perspective.FIRST_PERSON);
		}
		if (ticks == 50 || ticks == 55 || ticks == 65 || ticks == 70) capture(client, "depth-stunned-" + ticks + ".png");
		if (ticks == 110) {
			check(stunticks > 5 && ramSounds > 0 && gallopSounds > 0 && stunSounds > 0, "Goat visual/audio sync failed: stun=" + stunticks + " sounds=" + sounds);
			for (int id : goats) if (client.world.getEntityById(id) instanceof LivingEntity entity) check(!GoatCharge.isStunned(entity), "Stun did not expire on client");
			phase = 2; ticks = 0;
			onServer(client, () -> {
				var world = client.getServer().getOverworld();
				for (int id : goats) if (world.getEntityById(id) != null) world.getEntityById(id).discard();
				var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
				p.teleport(world, 0.5, 100, 0.5, 0, 10);
				p.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.BEE)));
				var target = EntityType.COW.create(world);
				target.refreshPositionAndAngles(0.5, 100, 5, 180, 0); target.setAiDisabled(true);
				target.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100); target.setHealth(100);
				world.spawnEntity(target); targetId = target.getId();
				for (int i = 0; i < 3; i++) {
					var bee = EntityType.BEE.create(world); bee.refreshPositionAndAngles(-0.5 + i, 101, 2, 0, 0);
					bee.addCommandTag("mhf_owner:" + p.getUuid()); world.spawnEntity(bee);
				}
			});
		}
	}
	private void bee(MinecraftClient client) {
		if (ticks == 20) press();
		if (ticks >= 25 && client.world.getEntityById(targetId) instanceof LivingEntity target) {
			check(!target.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.GLOWING), "Bee target used a glowing outline"); marks++;
		}
		if (ticks == 28 || ticks == 40 || ticks == 60) capture(client, "depth-bee-target-" + ticks + ".png");
		if (ticks == 100) {
			check(beeSounds > 0, "Bee command sound was not delivered");
			onServer(client, () -> {
				var world = client.getServer().getOverworld();
				var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
				var target = (LivingEntity) world.getEntityById(targetId);
				check(target.getHealth() < 100, "Bees did not hit designated target");
				var achievement = p.getServer().getAdvancementLoader().get(net.minecraft.util.Identifier.of("mc_head_function", "bee_commander"));
				check(achievement != null && p.getAdvancementTracker().getProgress(achievement).isDone(), "Bee achievement missing");
				PlayerHeadAccess.state(p).mastery.put(HeadType.PIG, 995);
				p.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.PIG)));
				var food = new ItemStack(net.minecraft.item.Items.APPLE);
				com.nakami.mcheadfunction.wear.WearHandler.onEat(p, food, food.get(net.minecraft.component.DataComponentTypes.FOOD));
			});
			phase = 3; ticks = 0;
		}
	}
	private static void press() { ClientPlayNetworking.send(new HeadSkillC2SPayload(true)); ClientPlayNetworking.send(new HeadSkillC2SPayload(false)); }
	private void onServer(MinecraftClient client, Runnable task) { client.getServer().execute(() -> { try { task.run(); } catch (Throwable e) { failure = e.toString(); } }); }
	private static void capture(MinecraftClient client, String name) { ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> {}); }
	private static void check(boolean result, String reason) { if (!result) throw new IllegalStateException(reason); }
	private void finish(MinecraftClient client, String result) {
		finished = true; client.options.jumpKey.setPressed(false); client.options.forwardKey.setPressed(false); client.options.sprintKey.setPressed(false);
		System.out.println("[HEAD-DEPTH-VALIDATION] " + result);
		try { Files.writeString(Path.of("result.txt"), result + "\n"); Files.write(Path.of("sound-events.txt"), sounds); } catch (Exception e) { throw new RuntimeException(e); }
		client.scheduleStop();
	}
}
