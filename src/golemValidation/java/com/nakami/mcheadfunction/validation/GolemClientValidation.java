package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.entity.ThrownHeadEntity;
import com.nakami.mcheadfunction.head.HeadCrushAccess;
import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.HeadType;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** Real held-head throw: small flight, vertical giant descent, impact, rolling and recovery. */
public final class GolemClientValidation implements ClientModInitializer {
	private static String renderFailure;
	private static int rollSamples;
	private static final float[] THROW_YAWS = {-45, 0, 45, 90, 135, 180, -135, -90};
	private int scenario;
	public static void recordRollAlignment(float alignment) {
		rollSamples++;
		if (!(alignment > 0.98F)) renderFailure = "Roll rotation disagrees with travel: alignment=" + alignment;
	}
	private boolean opening, setup, finished;
	private volatile boolean ready;
	private volatile String failure;
	private volatile int headId = -1, victimId;
	private int tick, fallFrames;
	private boolean smallSeen, giantSeen, rollSeen, crushSeen, quakeSeen;
	private Vec3d fallPosition;
	private final long started = System.nanoTime();

	@Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

	private void tick(MinecraftClient client) {
		if (finished) return;
		try {
			check((System.nanoTime() - started) / 1_000_000_000 < 180, "Timed out");
			check(failure == null, failure);
			check(renderFailure == null, renderFailure);
			if (!opening && client.currentScreen != null && client.getOverlay() == null) {
				opening = true;
				client.options.pauseOnLostFocus = false;
				client.options.getViewDistance().setValue(4);
				client.options.getMaxFps().setValue(60);
				client.options.hudHidden = true;
				client.createIntegratedServerLoader().createAndStart("golem-" + System.currentTimeMillis(),
					new LevelInfo("Golem throw regression", GameMode.CREATIVE, false, Difficulty.NORMAL, true,
						new GameRules(), DataConfiguration.SAFE_MODE), new GeneratorOptions(42, false, false),
					registries -> registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder(), client.currentScreen);
			}
			if (client.player == null || client.world == null || client.getServer() == null) return;
			if (!setup) {
				setup = true;
				server(client, () -> {
					var world = client.getServer().getOverworld();
					world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, client.getServer());
					world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, client.getServer());
					world.setTimeOfDay(6000);
					world.setWeather(0, 100000, false, false);
					for (int x = -70; x <= 70; x++) for (int z = -70; z <= 70; z++)
						world.setBlockState(new BlockPos(x, 99, z), Blocks.BEDROCK.getDefaultState());
					var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
					var camera = rotate(20, 30);
					player.teleport(world, camera.x, 113, camera.z, 130 + THROW_YAWS[scenario], 12);
					player.getAbilities().flying = true;
					player.sendAbilitiesUpdate();
					ready = true;
				});
			}
			if (!ready) return;
			tick++;
			if (tick == 40) server(client, () -> launch(client));
			var entity = client.world.getEntityById(headId);
			if (entity instanceof ThrownHeadEntity head) {
				if (!head.isActive()) {
					check(head.getWidth() == 0.5F, "Flying head must retain original dimensions");
					if (!smallSeen) { smallSeen = true; capture(client, "01-small-flight"); }
				} else {
					check(head.isGiantRoller() && head.getWidth() == 5F, "Giant dimensions must reach client");
					if (head.getY() > 106 && head.getRollDistance(1) == 0) {
						if (fallPosition == null) fallPosition = head.getPos();
						check(head.getPos().subtract(fallPosition).horizontalLength() < 0.3, "Giant must descend vertically");
						if (++fallFrames == 5) { giantSeen = true; capture(client, "02-giant-descent"); }
					}
					if (!rollSeen && head.getRollDistance(1) > 3) {
						check(head.getY() < 100.1, "Rolling starts only after landing");
						rollSeen = true;
						capture(client, "04-rolling");
					}
				}
			}
			var victim = client.world.getEntityById(victimId);
			if (victim instanceof net.minecraft.entity.LivingEntity living)
				crushSeen |= living.getHealth() < 100 && ((HeadCrushAccess) living).mhf$crushTicks() > 0;
			if (!quakeSeen && com.nakami.mcheadfunction.client.GolemQuake.amplitude(0) > 0) {
				quakeSeen = true;
				capture(client, "03-landing");
			}
			if (tick == (scenario == 0 ? 340 : 140)) {
				check(smallSeen && giantSeen && rollSeen && crushSeen && quakeSeen,
					"Missing stage: small=" + smallSeen + " giant=" + giantSeen + " rolling=" + rollSeen + " crush=" + crushSeen + " quake=" + quakeSeen);
				check(rollSamples > 20, "Insufficient rendered rolling samples");
				if (scenario == 0) {
					check(entity == null, "Head must recycle after ground rolling journey");
					check(victim instanceof HeadCrushAccess crushed && crushed.mhf$crushTicks() == 0, "Squash must expire");
				}
				System.out.println("GOLEM_DIRECTION_PASS yaw=" + THROW_YAWS[scenario] + " renderedSamples=" + rollSamples);
				if (++scenario == THROW_YAWS.length) {
					finish(client, "PASS directions=8 renderRotationMatchesTravel=true smallFlight=true verticalGiantDescent=true landingQuake=true rolling=true crush=true recovery=true");
				} else {
					int oldHead = headId, oldVictim = victimId;
					server(client, () -> {
						var world = client.getServer().getOverworld();
						var h = world.getEntityById(oldHead);
						var v = world.getEntityById(oldVictim);
						if (h != null) h.discard();
						if (v != null) v.discard();
					});
					tick = 0; headId = -1; victimId = -1; fallFrames = 0; rollSamples = 0;
					smallSeen = giantSeen = rollSeen = crushSeen = quakeSeen = false;
					fallPosition = null;
				}
			}
		} catch (Throwable error) { finish(client, "FAIL " + error); }
	}

	private void launch(MinecraftClient client) {
		var world = client.getServer().getOverworld();
		var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
		// Set the server throw origin without moving the spectator camera away from the scene.
		player.refreshPositionAndAngles(0, 100, 0, THROW_YAWS[scenario], 0);
		player.setHeadYaw(THROW_YAWS[scenario]);
		player.setBodyYaw(THROW_YAWS[scenario]);
		player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, HeadItems.item(HeadType.IRON_GOLEM).getDefaultStack());
		var victim = net.minecraft.entity.EntityType.COW.create(world);
		victim.setAiDisabled(true);
		victim.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100);
		victim.setHealth(100);
		var target = rotate(1.8, 18);
		victim.setPosition(target.x, 100, target.z);
		world.spawnEntity(victim);
		victimId = victim.getId();
		com.nakami.mcheadfunction.throwing.ThrowHandler.throwHeldHead(player);
		var thrown = world.getEntitiesByClass(ThrownHeadEntity.class, player.getBoundingBox().expand(3), e -> e.getOwner() == player).getFirst();
		check(!thrown.isActive() && thrown.getWidth() == 0.5F, "Throw must start small");
		headId = thrown.getId();
		var camera = rotate(20, 30);
		player.teleport(world, camera.x, 113, camera.z, 130 + THROW_YAWS[scenario], 12);
	}

	private Vec3d rotate(double x, double z) {
		double angle = Math.toRadians(THROW_YAWS[scenario]);
		return new Vec3d(x * Math.cos(angle) - z * Math.sin(angle), 0, x * Math.sin(angle) + z * Math.cos(angle));
	}

	private void capture(MinecraftClient client, String name) {
		ScreenshotRecorder.saveScreenshot(client.runDirectory, "direction-" + scenario + "-" + name + ".png", client.getFramebuffer(), message -> {});
	}
	private void server(MinecraftClient client, Runnable action) {
		client.getServer().execute(() -> { try { action.run(); } catch (Throwable error) { failure = error.toString(); } });
	}
	private static void check(boolean pass, String message) { if (!pass) throw new IllegalStateException(message); }
	private void finish(MinecraftClient client, String result) {
		finished = true;
		try { Files.writeString(Path.of("result.txt"), result + "\n"); } catch (Exception error) { throw new RuntimeException(error); }
		client.scheduleStop();
	}
}
