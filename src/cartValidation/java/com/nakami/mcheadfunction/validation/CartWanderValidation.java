package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.haunt.GhostHeadEntity;
import com.nakami.mcheadfunction.haunt.HauntBlocks;
import com.nakami.mcheadfunction.haunt.HauntWorldData;
import com.nakami.mcheadfunction.haunt.HauntedTreeBlockEntity;
import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.rule.HauntRules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** Fixed high camera: a possessed minecart weaves in range and boards a cow it bumps. */
public final class CartWanderValidation implements ClientModInitializer {
	private static final BlockPos TREE = new BlockPos(0, 100, 0);
	private static final int START = 40;
	private static final int WANDER = 56;
	private static final int BOARD_WAIT = 16;
	private static final int CARRY = 48;
	private static final int SEQUENCE = 28;
	private boolean opening, setup, finished;
	private boolean startRequested, snapped, wanderChecked, boardedShot, carryingShot;
	private volatile boolean hauntStarted, serverBoarded, serverStopped, snappedOnServer;
	private volatile boolean naturalBoard;
	private volatile boolean ready;
	private volatile String failure;
	private volatile String serverState = "";
	private int ticks, hauntAt, liveAt, boardAt, pendingScreenshots, sequence;
	private int cartId = -1, cowA = -1, cowB = -1;
	private String endingResult;
	private long endingSince;
	private final List<Vec3d> path = new ArrayList<>();
	private final long started = System.nanoTime();

	@Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

	private void tick(MinecraftClient client) {
		if (finished) return;
		if (endingResult != null) {
			if (failure != null) endingResult = "FAIL " + failure;
			if (pendingScreenshots == 0) complete(client, endingResult);
			else if ((System.nanoTime() - endingSince) / 1_000_000_000 >= 10)
				complete(client, "FAIL Screenshot writes timed out; pending=" + pendingScreenshots);
			return;
		}
		try {
			check((System.nanoTime() - started) / 1_000_000_000 < 180, "Timeout tick=" + ticks);
			check(failure == null, failure);
			if (!opening && client.currentScreen != null && client.getOverlay() == null) {
				opening = true;
				client.options.pauseOnLostFocus = false;
				client.options.hudHidden = true;
				client.options.getViewDistance().setValue(8);
				client.options.getMaxFps().setValue(60);
				client.options.getFov().setValue(50);
				client.options.getParticles().setValue(ParticlesMode.ALL);
				client.getWindow().setWindowedSize(854, 480);
				client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
				client.createIntegratedServerLoader().createAndStart("cart-fx-" + System.currentTimeMillis(),
					new LevelInfo("Goat cart validation", GameMode.SPECTATOR, false, Difficulty.PEACEFUL, true, new GameRules(), DataConfiguration.SAFE_MODE),
					new GeneratorOptions(42, false, false), registries -> registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder(), client.currentScreen);
			}
			if (client.player == null || client.world == null || client.getServer() == null) return;
			if (!setup) {
				setup = true;
				onServer(client, () -> {
					var world = client.getServer().getOverworld();
					world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, client.getServer());
					world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, client.getServer());
					HauntWorldData.get(world).setOff(true);
					world.setTimeOfDay(18000);
					world.setWeather(0, 100000, false, false);
					for (int x = -16; x <= 16; x++) for (int z = -4; z <= 24; z++) {
						world.setBlockState(new BlockPos(x, 99, z), Blocks.SMOOTH_QUARTZ.getDefaultState());
						world.setBlockState(new BlockPos(x, 98, z), Blocks.SEA_LANTERN.getDefaultState());
					}
					world.setBlockState(TREE, HauntBlocks.TREE.getDefaultState());
					HauntBlocks.TREE.onPlaced(world, TREE, world.getBlockState(TREE), null, ItemStack.EMPTY);
					var cart = new MinecartEntity(world, 0.5, 100.1, 6.5);
					world.spawnEntity(cart);
					cartId = cart.getId();
					cowA = spawnCow(world, 4.5, 8.5);
					cowB = spawnCow(world, -4.5, 10.5);
					var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
					player.teleport(world, 0.5, 114, 20.5, 180, 42);
					var stack = new ItemStack(HeadItems.item(HeadType.GOAT));
					player.setStackInHand(Hand.MAIN_HAND, stack);
					var tree = (HauntedTreeBlockEntity) world.getBlockEntity(TREE);
					check(tree != null, "Server tree missing after place");
					tree.hang(player, HeadType.GOAT, stack);
					check(tree.has(HeadType.GOAT), "Hang did not store the goat head");
					ready = true;
				});
			}
			if (!ready || client.currentScreen != null || client.getOverlay() != null) return;
			ticks++;
			if (ticks == 20) capture(client, "01-ready.png");
			if (ticks >= START && !startRequested) {
				startRequested = true;
				onServer(client, () -> {
					var world = client.getServer().getOverworld();
					world.setTimeOfDay(18000);
					var tree = (HauntedTreeBlockEntity) world.getBlockEntity(TREE);
					check(tree != null, "start: missing tree");
					tree.start(true);
					serverState = "haunting=" + tree.haunting() + " hung=" + tree.hung();
					check(tree.haunting() && tree.has(HeadType.GOAT), "start did not begin haunt " + serverState);
					hauntStarted = true;
				});
			}
			if (!hauntStarted) return;
			if (hauntAt == 0) hauntAt = ticks;
			if (liveAt == 0) {
				if (possessed(client)) {
					liveAt = ticks;
					capture(client, "02-possessed.png");
				} else {
					check(ticks < hauntAt + HauntRules.possessionReadyAt() + HauntRules.RISE_TICKS + 80,
						"Cart never came alive; " + ghostInfo(client));
				}
			}
			if (liveAt == 0) return;
			int elapsed = ticks - liveAt;
			Entity cart = client.world.getEntityById(cartId);
			check(cart instanceof MinecartEntity, "Client lost the minecart; " + ghostInfo(client));
			path.add(cart.getPos());
			if (elapsed <= 108 && elapsed % 4 == 0 && sequence < SEQUENCE) {
				sequence++;
				capture(client, String.format("sequence-%03d.png", sequence));
			}
			if (elapsed == 40) capture(client, "03-wander.png");
			if (elapsed == WANDER && !wanderChecked) {
				wanderChecked = true;
				assertWandered(client);
			}
			if (clientBoarded(client)) {
				if (boardAt == 0) {
					boardAt = ticks;
					if (elapsed <= WANDER) naturalBoard = true;
					frameCart(client, 7, 5.2, 28);
				}
			} else if (elapsed >= WANDER && elapsed < WANDER + BOARD_WAIT) {
				snapped = true;
				onServer(client, () -> {
					var world = client.getServer().getOverworld();
					var host = world.getEntityById(cartId);
					var cow = world.getEntityById(cowA);
					check(host != null && cow != null, "Snap missing cart or cow");
					if (cow.getVehicle() != host) {
						cow.refreshPositionAndAngles(host.getX(), host.getY(), host.getZ(), 0, 0);
						cow.setVelocity(Vec3d.ZERO);
					}
					snappedOnServer = true;
				});
			}
			if (elapsed == WANDER + BOARD_WAIT) {
				check(clientBoarded(client), "Client never saw the cow ride the cart; " + ghostInfo(client));
				onServer(client, () -> {
					var world = client.getServer().getOverworld();
					var host = world.getEntityById(cartId);
					var cow = world.getEntityById(cowA);
					check(host instanceof MinecartEntity minecart && minecart.hasPassengers(),
						"Server cart has no passenger after the bump");
					check(cow != null && cow.getVehicle() == host, "Server cow is not riding the cart");
					serverBoarded = true;
				});
			}
			if (boardAt > 0 && !boardedShot && ticks >= boardAt + 4) {
				boardedShot = true;
				frameCart(client, 7, 5.2, 28);
				capture(client, "04-boarded.png");
			}
			if (boardAt > 0 && ticks == boardAt + 20) frameCart(client, 7, 5.2, 28);
			if (boardAt > 0 && ticks == boardAt + 24 && !carryingShot) {
				carryingShot = true;
				capture(client, "05-carrying.png");
				assertStillRunning(client);
			}
			if (elapsed == WANDER + BOARD_WAIT + CARRY) {
				check(serverBoarded, "Missing server boarding verification");
				check(boardedShot, "Missing boarded screenshot");
				onServer(client, () -> {
					((HauntedTreeBlockEntity) client.getServer().getOverworld().getBlockEntity(TREE)).stop();
					serverStopped = true;
				});
			}
			if (elapsed >= WANDER + BOARD_WAIT + CARRY + HauntRules.RETURN_TICKS + 15 && serverStopped) {
				finish(client, "PASS client cart weaves in range and boards a cow, sequence=" + sequence
					+ " samples=" + path.size() + " naturalBoard=" + naturalBoard + " snapped=" + snappedOnServer);
			}
		} catch (Throwable e) { finish(client, "FAIL " + e); }
	}

	private boolean possessed(MinecraftClient client) {
		Entity cart = client.world.getEntityById(cartId);
		if (!(cart instanceof MinecartEntity)) return false;
		boolean held = !client.world.getEntitiesByClass(GhostHeadEntity.class, cart.getBoundingBox().expand(8),
			g -> g.head() == HeadType.GOAT && g.phase() == GhostHeadEntity.HELD).isEmpty();
		return held && ticks >= hauntAt + HauntRules.possessionReadyAt() + HauntRules.RISE_TICKS;
	}

	private boolean clientBoarded(MinecraftClient client) {
		Entity cart = client.world.getEntityById(cartId);
		Entity cow = client.world.getEntityById(cowA);
		if (cow == null) cow = client.world.getEntityById(cowB);
		return cart != null && cow != null && cow.getVehicle() == cart;
	}

	private void assertWandered(MinecraftClient client) {
		check(path.size() >= 20, "Not enough wander samples: " + path.size());
		double minX = path.getFirst().x, maxX = minX, minZ = path.getFirst().z, maxZ = minZ;
		double firstHeading = Double.NaN;
		double maxTurn = 0;
		int moving = 0, locked = 0;
		Entity cow = client.world.getEntityById(cowA);
		Vec3d prey = cow != null ? cow.getPos() : path.getFirst().add(4, 0, 2);
		for (int i = 1; i < path.size(); i++) {
			minX = Math.min(minX, path.get(i).x);
			maxX = Math.max(maxX, path.get(i).x);
			minZ = Math.min(minZ, path.get(i).z);
			maxZ = Math.max(maxZ, path.get(i).z);
			Vec3d step = path.get(i).subtract(path.get(i - 1));
			if (step.horizontalLengthSquared() < 0.002) continue;
			double heading = Math.atan2(step.z, step.x);
			if (Double.isNaN(firstHeading)) firstHeading = heading;
			maxTurn = Math.max(maxTurn, Math.abs(HauntRules.wrapAngle(heading - firstHeading)));
			moving++;
			double toPrey = Math.atan2(prey.z - path.get(i - 1).z, prey.x - path.get(i - 1).x);
			if (Math.abs(HauntRules.wrapAngle(heading - toPrey)) < 0.28) locked++;
		}
		double shortSide = Math.min(maxX - minX, maxZ - minZ);
		check(maxTurn > 0.35 || shortSide > 0.9,
			"Cart charged a line instead of weaving; turn=" + maxTurn + " span=" + shortSide);
		check(moving > 8, "Cart barely moved while possessed");
		check(locked < moving * 0.75, "Cart locked onto the cow instead of wandering; locked=" + locked + "/" + moving);
		onServer(client, () -> {
			var host = client.getServer().getOverworld().getEntityById(cartId);
			check(host != null && host.squaredDistanceTo(0.5, 100.1, 6.5) > 1,
				"Server cart never left its spawn");
		});
	}

	private void assertStillRunning(MinecraftClient client) {
		check(clientBoarded(client), "Cow left the cart while it should be carrying");
		Vec3d now = path.getLast();
		Vec3d then = path.get(Math.max(0, path.size() - 16));
		check(now.squaredDistanceTo(then) > 0.8, "Loaded cart stopped instead of running around");
	}

	private void frameCart(MinecraftClient client, double back, double up, float pitch) {
		onServer(client, () -> {
			var world = client.getServer().getOverworld();
			var host = world.getEntityById(cartId);
			var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
			if (host != null && player != null) {
				player.teleport(world, host.getX(), host.getY() + up, host.getZ() + back, 180, pitch);
			}
		});
	}

	private String ghostInfo(MinecraftClient client) {
		return client.world.getEntitiesByClass(GhostHeadEntity.class, new Box(TREE).expand(32),
			g -> g.head() == HeadType.GOAT).stream()
			.map(g -> "phase=" + g.phase() + " clock=" + g.clock() + " at=" + g.getBlockPos()).toList()
			+ " path=" + path.size() + " " + serverState;
	}

	private static int spawnCow(net.minecraft.server.world.ServerWorld world, double x, double z) {
		var cow = EntityType.COW.create(world);
		check(cow != null, "Could not create a cow");
		cow.setAiDisabled(true);
		cow.refreshPositionAndAngles(x, 100, z, 0, 0);
		world.spawnEntity(cow);
		return cow.getId();
	}

	private void onServer(MinecraftClient client, Runnable action) {
		client.getServer().execute(() -> { try { action.run(); } catch (Throwable e) { failure = e.toString(); } });
	}

	private void capture(MinecraftClient client, String name) {
		pendingScreenshots++;
		ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> client.execute(() -> {
			pendingScreenshots--;
			try {
				Path path = client.runDirectory.toPath().resolve("screenshots").resolve(name);
				check(Files.isRegularFile(path) && Files.size(path) > 0, "Screenshot was not saved: " + name + "; " + message.getString());
			} catch (Throwable e) { failure = e.toString(); }
		}));
	}

	private static void check(boolean ok, String reason) { if (!ok) throw new IllegalStateException(reason); }

	private void finish(MinecraftClient client, String result) {
		endingResult = result;
		endingSince = System.nanoTime();
	}

	private void complete(MinecraftClient client, String result) {
		finished = true;
		System.out.println("[CART-FX] " + result);
		try { Files.writeString(Path.of("result.txt"), result + "\n"); } catch (Exception e) { throw new RuntimeException(e); }
		client.scheduleStop();
	}
}
