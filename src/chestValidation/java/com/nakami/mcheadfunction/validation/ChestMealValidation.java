package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.haunt.*;
import com.nakami.mcheadfunction.head.*;
import java.nio.file.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.*;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

/** Real tree possession: tongue eats path blocks one by one, then pulls and swallows. */
public final class ChestMealValidation implements ClientModInitializer {
	private static final BlockPos CHEST = new BlockPos(0, 100, 0), TREE = new BlockPos(-6, 100, 0);
	private static final BlockPos FIRST = CHEST.add(0, 0, 4), SECOND = CHEST.add(0, 0, 8);
	private boolean opening, setup, finished, launched, verified, stagedEat, blockedShot, firstEatenShot, retractShot, clearShot;
	private volatile boolean ready, serverVerified;
	private volatile String failure;
	private volatile int preyId;
	private int ticks, swallowFrames, chewFrames, videoFrames, deathPoseFrames, deathSounds, deathParticleFrames;
	private float smallest = 1, lowestLid = 1, highestLid;
	private final long started = System.nanoTime();
	@Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }
	private void tick(MinecraftClient client) {
		if (finished) return;
		try {
			check((System.nanoTime() - started) / 1_000_000_000 < 240, "Timeout tick=" + ticks);
			check(failure == null, failure);
			if (!opening && client.currentScreen != null && client.getOverlay() == null) {
				opening = true;
				client.options.pauseOnLostFocus = false;
				client.options.hudHidden = true;
				client.options.getViewDistance().setValue(6);
				client.options.getMaxFps().setValue(60);
				client.options.getFov().setValue(55);
				client.options.getParticles().setValue(ParticlesMode.MINIMAL);
				client.getSoundManager().registerListener((sound, set, range) -> {
					if (sound.getId().toString().equals("minecraft:entity.sheep.death")) deathSounds++;
				});
				client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
				client.createIntegratedServerLoader().createAndStart("chest-meal-" + System.currentTimeMillis(),
					new LevelInfo("Chest meal validation", GameMode.SPECTATOR, false, Difficulty.NORMAL, true, new GameRules(), DataConfiguration.SAFE_MODE),
					new GeneratorOptions(42, false, false), registries -> registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder(), client.currentScreen);
			}
			if (client.player == null || client.world == null || client.getServer() == null) return;
			if (!setup) {
				setup = true;
				onServer(client, () -> {
					var world = client.getServer().getOverworld();
					world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, client.getServer());
					world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, client.getServer());
					world.setTimeOfDay(18000);
					world.setWeather(0, 100000, false, false);
					for (int x = -10; x <= 10; x++) for (int z = -5; z <= 18; z++) {
						world.setBlockState(new BlockPos(x, 99, z), Blocks.SMOOTH_QUARTZ.getDefaultState());
						world.setBlockState(new BlockPos(x, 98, z), Blocks.SEA_LANTERN.getDefaultState());
					}
					world.setBlockState(CHEST, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH));
					world.setBlockState(FIRST, Blocks.OAK_FENCE.getDefaultState());
					world.setBlockState(SECOND, Blocks.OAK_FENCE.getDefaultState());
					world.setBlockState(TREE, HauntBlocks.TREE.getDefaultState());
					var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
					player.teleport(world, 9.5, 103.5, 6, 90, 20);
					player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.item(HeadType.FROG)));
					((HauntedTreeBlockEntity) world.getBlockEntity(TREE)).hang(player, HeadType.FROG, player.getMainHandStack());
					var sheep = EntityType.SHEEP.create(world);
					sheep.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0);
					sheep.setPosition(Vec3d.ofBottomCenter(CHEST).add(0, 0, 12));
					world.spawnEntity(sheep);
					preyId = sheep.getId();
					ready = true;
				});
			}
			if (!ready) return;
			ticks++;
			if (ticks == 60) onServer(client, () -> ((HauntedTreeBlockEntity) client.getServer().getOverworld().getBlockEntity(TREE)).start(true));
			boolean firstGone = client.world.getBlockState(FIRST).isAir();
			boolean secondGone = client.world.getBlockState(SECOND).isAir();
			if (firstGone && !secondGone) stagedEat = true;
			if (launched && !firstGone && client.world.getEntityById(preyId) instanceof LivingEntity prey)
				check(prey.squaredDistanceTo(Vec3d.ofBottomCenter(CHEST).add(0, 0, 12)) < 0.6, "Prey moved before the first fence was eaten; pos=" + prey.getPos());
			for (var tongue : client.world.getEntitiesByClass(HauntTongueEntity.class, new Box(CHEST).expand(24), e -> e.chest().equals(CHEST))) {
				launched = true;
				capture(client, String.format("sequence-%03d.png", ++videoFrames));
				if (!firstGone) {
					check(tongue.getZ() < FIRST.getZ() + 1.4, "Tongue must stop at the first fence, not pierce it; tip=" + tongue.getPos());
					if (!blockedShot) { blockedShot = true; capture(client, "path-blocked.png"); }
				} else if (!secondGone) {
					if (!firstEatenShot) { firstEatenShot = true; capture(client, "first-eaten.png"); }
					if (!tongue.carriedBlock().isAir() && tongue.squaredDistanceTo(tongue.mouth()) < 4) {
						if (!retractShot) { retractShot = true; capture(client, "first-return.png"); }
					}
				} else if (!clearShot) { clearShot = true; capture(client, "path-clear-pull.png"); }
				int meal = tongue.mealTime();
				if (meal >= 0 && meal < HauntTongueEntity.SWALLOW) {
					if (meal < HauntTongueEntity.SWALLOW - 2) check(client.world.getEntityById(preyId) instanceof LivingEntity, "Live prey must be visible while swallowing; meal=" + meal);
					swallowFrames++;
					smallest = Math.min(smallest, tongue.preyScale(0));
					if (tongue.deathTicks() > 0 && client.world.getEntityById(preyId) instanceof LivingEntity prey) {
						deathPoseFrames++;
						check(prey.getHealth() > 0 && prey.deathTime == 0 && prey.hurtTime == 0, "Death pose must restore vanilla state after rendering");
					}
					if (meal == 2 || meal == 8 || meal == 13) capture(client, "swallow-" + meal + ".png");
					if (meal == 9 || meal == 11) capture(client, "bite-" + meal + ".png");
				}
				if (meal >= HauntTongueEntity.SWALLOW) {
					chewFrames++;
					if (meal < HauntTongueEntity.SWALLOW + 8 && Integer.parseInt(client.particleManager.getDebugString()) > 0) deathParticleFrames++;
					float lid = ((ChestBlockEntity) client.world.getBlockEntity(CHEST)).getAnimationProgress(0);
					lowestLid = Math.min(lowestLid, lid); highestLid = Math.max(highestLid, lid);
					if (meal == 16 || meal == 18 || meal == 23 || meal == 30) capture(client, "chew-" + meal + ".png");
				}
			}
			if (ticks == 340) onServer(client, () -> {
				var world = client.getServer().getOverworld();
				check(world.getBlockState(FIRST).isAir() && world.getBlockState(SECOND).isAir(), "Server must eat both path fences");
				check(count((Inventory) world.getBlockEntity(CHEST), Items.OAK_FENCE) == 2, "Eaten fences must enter the chest");
				check(world.getEntityById(preyId) == null, "Server must remove swallowed prey");
				check(count((Inventory) world.getBlockEntity(CHEST), Items.WHITE_WOOL) == 1, "Normal loot must reach chest once");
				check(world.getEntitiesByClass(ExperienceOrbEntity.class, new Box(CHEST).expand(16), e -> true).isEmpty(), "No XP remains");
				serverVerified = true;
			});
			if (ticks >= 350 && serverVerified && !verified) {
				verified = true;
				check(launched && blockedShot && stagedEat && retractShot, "Each block bite must latch, then pull the block back to the chest");
				check(swallowFrames >= 10 && smallest < 0.3F, "Missing synchronized shrink: frames=" + swallowFrames + " scale=" + smallest);
				check(chewFrames >= 10 && highestLid - lowestLid > 0.25F, "Chest lid must visibly chew: " + lowestLid + "/" + highestLid);
				check(deathPoseFrames >= 5 && deathSounds == 1, "Bite must show dying pose and play one death sound: " + deathPoseFrames + "/" + deathSounds);
				check(deathParticleFrames >= 2, "Vanilla death smoke must arrive with minimal particles");
				check(client.world.getEntityById(preyId) == null, "Client must remove consumed prey");
				check(client.world.getEntitiesByClass(HauntTongueEntity.class, new Box(CHEST).expand(24), e -> true).isEmpty(), "Tongue must finish");
				capture(client, "finished.png");
				finish(client, "PASS real frog tree possession, path blocks eaten one-by-one before pull, no tongue pierce, synchronized swallow/shrink, chest jaw snap/chewing, death pose, one sheep death sound, vanilla death smoke, fence and wool loot, no XP; swallowFrames=" + swallowFrames + " chewFrames=" + chewFrames + " sequence=" + videoFrames);
			}
		} catch (Throwable e) { finish(client, "FAIL " + e); }
	}
	private static int count(Inventory inventory, Item item) { int n = 0; for (int i = 0; i < inventory.size(); i++) if (inventory.getStack(i).isOf(item)) n += inventory.getStack(i).getCount(); return n; }
	private void onServer(MinecraftClient client, Runnable task) { client.getServer().execute(() -> { try { task.run(); } catch (Throwable e) { failure = e.toString(); } }); }
	private static void capture(MinecraftClient client, String name) { ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> {}); }
	private static void check(boolean result, String reason) { if (!result) throw new IllegalStateException(reason); }
	private void finish(MinecraftClient client, String result) {
		finished = true;
		System.out.println("[CHEST-VALIDATION] " + result);
		try { Files.writeString(Path.of("result.txt"), result + "\n"); } catch (Exception e) { throw new RuntimeException(e); }
		client.scheduleStop();
	}
}
