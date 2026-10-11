package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.client.PigSwallowEffects;
import com.nakami.mcheadfunction.entity.ThrownHeadEntity;
import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.head.PigStuffedAccess;
import com.nakami.mcheadfunction.rule.HeadRules;
import com.nakami.mcheadfunction.throwing.ThrowHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** ./gradlew runPigValidation: swallow pose, fat belly, spit and audio. */
public final class PigSwallowValidation implements ClientModInitializer {
	private boolean opening, setup, finished, swallowed;
	private volatile boolean ready;
	private volatile String failure;
	private int ticks;
	private int swallowFrames, fatFrames, incomingFrames;
	private volatile int restoredFood = -1;
	private boolean heardEat, heardPig, heardGulp, heardBurp, heardSlime;
	private final List<String> sounds = new ArrayList<>();
	private final long started = System.nanoTime();

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(MinecraftClient client) {
		if (finished) return;
		try {
			check((System.nanoTime() - started) / 1_000_000_000 < 180, "Pig validation timeout tick=" + ticks);
			check(failure == null, failure);
			if (!opening && client.currentScreen != null && client.getOverlay() == null) {
				opening = true;
				client.options.pauseOnLostFocus = false;
				client.options.hudHidden = true;
				client.options.getViewDistance().setValue(6);
				client.options.getMaxFps().setValue(60);
				client.options.getFov().setValue(70);
				client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
				client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
				client.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(1.0);
				client.options.getSoundVolumeOption(SoundCategory.PLAYERS).setValue(1.0);
				client.getSoundManager().registerListener((sound, set, range) -> {
					String id = sound.getId().toString();
					if (id.contains("eat") || id.contains("pig") || id.contains("drink") || id.contains("burp") || id.contains("slime")) {
						sounds.add("tick=" + ticks + " " + id + " vol=" + sound.getVolume() + " pitch=" + sound.getPitch());
					}
					if (id.equals("minecraft:entity.generic.eat")) heardEat = true;
					if (id.equals("minecraft:entity.pig.ambient")) heardPig = true;
					if (id.equals("minecraft:entity.generic.drink")) heardGulp = true;
					if (id.equals("minecraft:entity.player.burp")) heardBurp = true;
					if (id.equals("minecraft:entity.slime.squish")) heardSlime = true;
				});
				client.createIntegratedServerLoader().createAndStart("pig-swallow-" + System.currentTimeMillis(),
					new LevelInfo("Pig swallow validation", GameMode.SURVIVAL, false, Difficulty.NORMAL, true,
						new GameRules(), DataConfiguration.SAFE_MODE),
					new GeneratorOptions(42, false, false),
					registries -> registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder(),
					client.currentScreen);
			}
			if (client.player == null || client.world == null || client.getServer() == null) return;
			if (!setup) {
				setup = true;
				onServer(client, () -> {
					var world = client.getServer().getOverworld();
					world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, client.getServer());
					world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, client.getServer());
					world.getGameRules().get(GameRules.ANNOUNCE_ADVANCEMENTS).set(false, client.getServer());
					world.setTimeOfDay(6000);
					world.setWeather(0, 100000, false, false);
					for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
						world.setBlockState(new BlockPos(x, 99, z), Blocks.SMOOTH_QUARTZ.getDefaultState());
					}
					var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
					player.changeGameMode(GameMode.SURVIVAL);
					player.getHungerManager().setFoodLevel(6);
					player.getHungerManager().setSaturationLevel(2);
					player.getInventory().clear();
					player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(HeadItems.item(HeadType.PIG)));
					player.teleport(world, 0.5, 100, 0.5, 200, 8);
					ready = true;
				});
			}
			if (!ready) return;
			ticks++;
			if (ticks == 20) {
				client.options.setPerspective(Perspective.FIRST_PERSON);
				onServer(client, () -> {
					var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
					player.getAbilities().creativeMode = false;
					player.getHungerManager().setFoodLevel(6);
					player.getHungerManager().setSaturationLevel(2);
					ThrowHandler.throwHeldHead(player);
				});
				swallowed = true;
			}
			if (swallowed && client.player != null) {
				int stuffed = ((PigStuffedAccess) client.player).mhf$stuffedTicks();
				if (HeadRules.pigSwallowProgress(stuffed, 0) < 1) {
					swallowFrames++;
					if (HeadRules.pigSwallowProgress(stuffed, 0) < 0.95F) incomingFrames++;
				}
				if (HeadRules.pigFatness(stuffed, 0) > 0.7F) fatFrames++;
			}
			if (ticks == 28) {
				check(client.world.getEntitiesByClass(ThrownHeadEntity.class, client.player.getBoundingBox().expand(8), e -> true).isEmpty(),
					"Swallow must not spawn a thrown head");
				check(heardEat, "Missing chew: " + sounds);
				check(heardPig, "Missing pig oink: " + sounds);
				capture(client, "01-swallow-first.png");
			}
			if (ticks == 32) {
				client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
			}
			if (ticks == 50) {
				check(heardGulp, "Missing gulp: " + sounds);
				check(PigSwallowEffects.stuffed(client.player) > 100, "Client must see stuffed ticks");
				check(HeadRules.pigFatness(PigSwallowEffects.stuffed(client.player), 0) > 0.8F, "Belly must be inflated");
				capture(client, "02-fat-belly.png");
			}
			if (ticks == 224) {
				onServer(client, () -> restoredFood = client.getServer().getPlayerManager().getPlayerList().getFirst().getHungerManager().getFoodLevel());
			}
			if (ticks == 226) {
				check(heardBurp, "Missing burp on spit: " + sounds);
				check(heardSlime, "Missing belly squash: " + sounds);
				check(PigSwallowEffects.stuffed(client.player) == 0, "Stuffed must end after ten seconds");
				var items = client.world.getEntitiesByClass(ItemEntity.class, client.player.getBoundingBox().expand(8),
					item -> item.getStack().isOf(HeadItems.item(HeadType.PIG)));
				check(!items.isEmpty(), "Spit must drop the pig head");
				check(restoredFood == 6, "Hunger must return to the pre-swallow value: " + restoredFood);
				check(swallowFrames >= 6, "Swallow pose was too short: " + swallowFrames);
				check(fatFrames >= 20, "Fat belly was too brief: " + fatFrames);
				check(incomingFrames >= 4, "Incoming head was not visible: " + incomingFrames);
				capture(client, "03-spit.png");
				finish(client, "PASS client saw a gulp, a fat belly, a spit and heard eat/pig/drink/burp/slime");
			}
		} catch (Exception e) {
			finish(client, "FAIL " + e.getMessage());
		}
	}

	private void onServer(MinecraftClient client, Runnable task) {
		client.getServer().execute(() -> {
			try {
				task.run();
			} catch (Throwable e) {
				failure = e.toString();
			}
		});
	}

	private static void check(boolean ok, String message) {
		if (!ok) throw new IllegalStateException(message);
	}

	private void capture(MinecraftClient client, String name) {
		ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), ignored -> {});
	}

	private void finish(MinecraftClient client, String line) {
		finished = true;
		try {
			Files.writeString(Path.of(client.runDirectory.getPath(), "result.txt"), line);
		} catch (Exception ignored) {
		}
		client.scheduleStop();
	}
}
