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

/** ./gradlew runFrogValidation: isolated real-client gameplay regression. */
public final class FrogClientValidation implements ClientModInitializer {
	private boolean opening, setup, finished;
	private volatile boolean ready;
	private volatile String failure;
	private int ticks;
	private final List<String> sounds = new ArrayList<>();
	private final long started = System.nanoTime();
	@Override public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}
	private void tick(MinecraftClient client) {
		if (finished) return;
		try {
			check((System.nanoTime() - started) / 1_000_000_000 < 240, "Frog validation timeout tick=" + ticks);
			check(failure == null, failure);
			if (!opening && client.currentScreen != null && client.getOverlay() == null) {
				opening = true;
				client.options.pauseOnLostFocus = false;
				client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
				client.options.getViewDistance().setValue(6);
				client.options.getMaxFps().setValue(60);
				client.options.getParticles().setValue(net.minecraft.client.option.ParticlesMode.MINIMAL);
				client.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(1.0);
				client.getSoundManager().registerListener((sound, set, range) -> {
					String id = sound.getId().toString();
					if (id.contains("frog") || id.contains("slime") || id.contains("snowball"))
						sounds.add("tick=" + ticks + " " + id + " volume=" + sound.getVolume() + " pitch=" + sound.getPitch());
					if (id.equals("minecraft:entity.frog.tongue")) {
						// Vanilla tongue sound has a 0.5 asset gain: 1.6 × 0.5 = 0.8 at the listener.
						if (sound.getVolume() < 0.75F || sound.getVolume() > 0.85F || sound.getPitch() < 1.05F || sound.getPitch() > 1.15F)
							failure = "Incorrect tongue launch audio: " + sound.getVolume() + "/" + sound.getPitch();
						launchSounds++;
					}
				});
				client.createIntegratedServerLoader().createAndStart("frog-tongue-" + System.currentTimeMillis(),
					new LevelInfo("Frog tongue validation", GameMode.SURVIVAL, false, Difficulty.NORMAL, true, new GameRules(), DataConfiguration.SAFE_MODE),
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
					world.getGameRules().get(GameRules.ANNOUNCE_ADVANCEMENTS).set(false, client.getServer());
					world.setWeather(0, 100000, false, false);
					for (int x = -5; x <= 5; x++) for (int z = -5; z <= 30; z++) world.setBlockState(new BlockPos(x, 99, z), Blocks.SMOOTH_QUARTZ.getDefaultState());
					var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
					p.teleport(world, 0.5, 100, 0.5, 0, 0);
					p.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.FROG)));
					world.setBlockState(new BlockPos(0, 101, 9), Blocks.CHEST.getDefaultState());
					((net.minecraft.inventory.Inventory) world.getBlockEntity(new BlockPos(0, 101, 9)))
						.setStack(0, new ItemStack(net.minecraft.item.Items.DIAMOND, 5));
					ready = true;
				});
				client.options.setPerspective(Perspective.FIRST_PERSON);
			}
			if (!ready) return;
			ticks++;
			scenario(client);
		} catch (Throwable e) { finish(client, "FAIL " + e); }
	}

	private int visibleFrames, carryingFrames, launchSounds;
	private boolean extended, returning;
	private void scenario(MinecraftClient client) {
		if (ticks == 20 || ticks == 80) press();
		if (ticks == 83) client.player.setPitch(30);
		if (ticks == 84 || ticks == 88) capture(client, "frog-third-person-" + ticks + ".png");
		if (ticks == 120) {
			check(launchSounds == 2, "Each grab must play one prominent tongue sound; got " + launchSounds);
			check(client.player.getInventory().count(net.minecraft.item.Items.DIRT) == 1, "Second grab must deliver dirt");
			finish(client, "PASS 10-tick grab, prominent launch audio, first/third person, minimal particles, extension, delayed removal, carried chest/dirt, return, exact delivery and expiry; visibleFrames=" + visibleFrames);
		}
		for (var entity : client.world.getEntities()) {
			if (!(entity instanceof com.nakami.mcheadfunction.entity.FrogTongueEntity tongue) || ticks > 65) continue;
			visibleFrames++;
			if (tongue.elapsed() <= com.nakami.mcheadfunction.entity.FrogTongueEntity.EXTEND) {
				extended = true;
				check(client.world.getBlockState(new BlockPos(0, 101, 9)).isOf(Blocks.CHEST), "Chest removed before tongue contact");
			}
			if (!tongue.carriedBlock().isAir()) {
				carryingFrames++;
				returning |= tongue.getZ() < 7;
				check(client.player.getInventory().count(net.minecraft.item.Items.DIAMOND) == 0, "Loot arrived before tongue");
			}
			if (tongue.elapsed() == 2 || tongue.elapsed() == 5 || tongue.elapsed() == 7 || tongue.elapsed() == 9)
				capture(client, "frog-tongue-" + tongue.elapsed() + ".png");
		}
		if (ticks == 34) {
			check(visibleFrames >= 7 && visibleFrames <= 13, "Expected a roughly 10-tick tongue, got " + visibleFrames);
			check(client.world.getEntitiesByClass(com.nakami.mcheadfunction.entity.FrogTongueEntity.class,
				client.player.getBoundingBox().expand(32), e -> true).isEmpty(), "Faster tongue must finish before tick 34");
		}
		if (ticks == 65) {
			check(extended && returning && carryingFrames >= 2, "Missing tongue phases: " + visibleFrames + "/" + carryingFrames);
			check(client.player.getInventory().count(net.minecraft.item.Items.DIAMOND) == 5, "Missing container loot");
			check(client.world.getBlockState(new BlockPos(0, 101, 9)).isAir(), "Chest not removed");
			check(client.world.getEntitiesByClass(com.nakami.mcheadfunction.entity.FrogTongueEntity.class,
				client.player.getBoundingBox().expand(32), e -> true).isEmpty(), "Tongue failed to expire");
			capture(client, "frog-finished.png");
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			onServer(client, () -> client.getServer().getOverworld().setBlockState(new BlockPos(0, 101, 9), Blocks.DIRT.getDefaultState()));
		}
	}

	private static void press() { ClientPlayNetworking.send(new HeadSkillC2SPayload(true)); ClientPlayNetworking.send(new HeadSkillC2SPayload(false)); }
	private void onServer(MinecraftClient client, Runnable task) { client.getServer().execute(() -> { try { task.run(); } catch (Throwable e) { failure = e.toString(); } }); }
	private static void capture(MinecraftClient client, String name) { ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> {}); }
	private static void check(boolean result, String reason) { if (!result) throw new IllegalStateException(reason); }
	private void finish(MinecraftClient client, String result) {
		finished = true; client.options.jumpKey.setPressed(false);
		System.out.println("[FROG-VALIDATION] " + result);
		try { Files.writeString(Path.of("result.txt"), result + "\n"); Files.write(Path.of("sound-events.txt"), sounds); } catch (Exception e) { throw new RuntimeException(e); }
		client.scheduleStop();
	}
}
