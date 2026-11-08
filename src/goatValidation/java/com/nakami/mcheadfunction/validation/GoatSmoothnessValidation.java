package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.net.GoatChargeS2CPayload;
import com.nakami.mcheadfunction.net.HeadSkillC2SPayload;
import com.nakami.mcheadfunction.client.GoatChargeControl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** First/third-person continuity, model-facing and camera-collision regression. */
public final class GoatSmoothnessValidation {
	public static final boolean ENABLED = "1".equals(System.getenv("MHF_GOAT_SMOOTHNESS"));
	private static final String VIEW = System.getenv().getOrDefault("MHF_GOAT_PERSPECTIVE", "first");
	private static final Camera probe = new Camera();
	private static final List<String> frames = new ArrayList<>();
	private static final List<Frame> samples = new ArrayList<>();
	private static int tick, packets, corrections, velocityPackets;
	private static float beforeYaw, normalJump, driftJump, stopJump;
	private static Vec3d beforePosition;
	private static double normalPositionJump, driftPositionJump, stopPositionJump;
	private static float bodyYaw = Float.NaN;
	private static volatile boolean wallReady;
	private static volatile String wallFailure;
	private static double clippedDistance = Double.POSITIVE_INFINITY;
	private static int collisionFrames;
	private static boolean cameraAvoidsWall = true;
	private static double wallPenetration;
	private static boolean probing, stopping;
	private GoatSmoothnessValidation() { }
	public static void register() {
		if (!ENABLED) return;
		try {
			Files.deleteIfExists(Path.of("smoothness-frames.csv"));
			Files.deleteIfExists(Path.of("smoothness-metrics.txt"));
		} catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
		frames.add("timeNs,tick,yaw,x,y,z,bodyYaw");
		WorldRenderEvents.END.register(context -> {
			if (thirdPerson() && tick >= 140 && tick <= 144) {
				var client = MinecraftClient.getInstance();
				var camera = context.camera();
				var eye = client.player.getLerpedPos(camera.getLastTickDelta()).add(0, client.player.getStandingEyeHeight(), 0);
				clippedDistance = Math.min(clippedDistance, camera.getPos().distanceTo(eye));
				var blockPos = net.minecraft.util.math.BlockPos.ofFloored(camera.getPos());
				for (var box : client.world.getBlockState(blockPos).getCollisionShape(client.world, blockPos).getBoundingBoxes()) {
					var bounds = box.offset(blockPos);
					var point = camera.getPos();
					if (!bounds.contains(point)) continue;
					double depth = Math.min(Math.min(point.x - bounds.minX, bounds.maxX - point.x),
						Math.min(Math.min(point.y - bounds.minY, bounds.maxY - point.y), Math.min(point.z - bounds.minZ, bounds.maxZ - point.z)));
					wallPenetration = Math.max(wallPenetration, depth);
					// Vanilla measures its +/-0.1 corner-ray hit distances from the
					// eye center, allowing a small overlap at the wall face.
					cameraAvoidsWall &= depth < .05;
				}
				collisionFrames++;
			}
			if (tick < 45 || tick > 130) return;
			var camera = context.camera();
			var p = camera.getPos();
			long now = System.nanoTime();
			samples.add(new Frame(now, tick, camera.getYaw(), bodyYaw));
			frames.add(now + "," + tick + "," + camera.getYaw() + "," + p.x + "," + p.y + "," + p.z + "," + bodyYaw);
		});
	}
	public static String tick(MinecraftClient client, int time) throws java.io.IOException {
		tick = time;
		if (time == 20) press();
		if (time == 30) {
			probe.update(client.world, client.player, thirdPerson(), inverse(), .5F);
			for (int i = 0; i < 64; i++) probe.updateEyeHeight();
		}
		if (time == 50) client.options.rightKey.setPressed(true);
		if (time == 90) client.options.sneakKey.setPressed(true);
		if (time == 70 || time == 105) net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,
			"goat-" + VIEW + (time == 70 ? "-turn.png" : "-drift-smooth.png"), client.getFramebuffer(), message -> {});
		if (time == 120) {
			client.options.rightKey.setPressed(false); client.options.sneakKey.setPressed(false);
			client.getServer().execute(() -> com.nakami.mcheadfunction.wear.GoatCharge.stop(
				client.getServer().getPlayerManager().getPlayerList().getFirst()));
		}
		if (thirdPerson() && time == 123) client.getServer().execute(() -> {
			try {
				var world = client.getServer().getOverworld();
				var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
				com.nakami.mcheadfunction.wear.GoatCharge.stop(player);
				com.nakami.mcheadfunction.head.PlayerHeadAccess.state(player).goatCooldown = 0;
				player.setVelocity(Vec3d.ZERO);
				player.teleport(world, .5, 100, 60.5, 0, 0);
				int wallZ = inverse() ? 63 : 58;
				for (int x = -2; x <= 2; x++) for (int y = 100; y <= 104; y++)
					world.setBlockState(new net.minecraft.util.math.BlockPos(x, y, wallZ), net.minecraft.block.Blocks.STONE.getDefaultState());
				wallReady = true;
			} catch (Throwable failure) { wallFailure = failure.toString(); }
		});
		if (thirdPerson() && time == 138) {
			if (!wallReady || wallFailure != null) throw new IllegalStateException("Camera wall fixture not ready: " + wallFailure);
			press();
		}
		if (thirdPerson() && time == 145) client.getServer().execute(() -> com.nakami.mcheadfunction.wear.GoatCharge.stop(
			client.getServer().getPlayerManager().getPlayerList().getFirst()));
		if (time != (thirdPerson() ? 148 : 135)) return null;
		Files.write(Path.of("smoothness-frames.csv"), frames);
		double normalStalls = stalls(60, 85, false), driftStalls = stalls(98, 115, false);
		double bodyStalls = thirdPerson() ? stalls(98, 115, true) : 0;
		String detail = "perspective=" + VIEW + " normalPacketJump=" + normalJump + " driftPacketJump=" + driftJump + " packets=" + packets
			+ " stopJump=" + stopJump + " normalStalls=" + normalStalls + " driftStalls=" + driftStalls
			+ " normalOrbitJump=" + normalPositionJump + " driftOrbitJump=" + driftPositionJump + " stopOrbitJump=" + stopPositionJump + " bodyStalls=" + bodyStalls
			+ (thirdPerson() ? " clippedDistance=" + clippedDistance + " cameraAvoidsWall=" + cameraAvoidsWall + " wallPenetration=" + wallPenetration : "")
			+ " corrections=" + corrections + " velocityPackets=" + velocityPackets + " renderedFrames=" + (frames.size() - 1);
		Files.writeString(Path.of("smoothness-metrics.txt"), detail + "\n");
		if (packets < 40 || frames.size() < 80) return "FAIL missing smoothness samples: " + detail;
		if (normalJump > .25 || driftJump > .25 || stopJump > .25) return "FAIL camera jumps when orientation packets arrive: " + detail;
		if (normalPositionJump > .02 || driftPositionJump > .02 || stopPositionJump > .02) return "FAIL orbit position jumps when orientation packets arrive: " + detail;
		if (normalStalls > .25 || driftStalls > .25 || bodyStalls > .25) return "FAIL steering stalls between network updates: " + detail;
		if (thirdPerson() && (collisionFrames < 2 || clippedDistance >= 3.5 || !cameraAvoidsWall)) return "FAIL third-person camera must still avoid walls: " + detail;
		return "PASS smooth camera orientation, orbit and body: " + detail;
	}
	public static Perspective perspective() {
		return !ENABLED ? Perspective.FIRST_PERSON : switch (VIEW) {
			case "back" -> Perspective.THIRD_PERSON_BACK;
			case "front" -> Perspective.THIRD_PERSON_FRONT;
			default -> Perspective.FIRST_PERSON;
		};
	}
	private static boolean thirdPerson() { return perspective() != Perspective.FIRST_PERSON; }
	private static boolean inverse() { return perspective() == Perspective.THIRD_PERSON_FRONT; }
	public static void observeBody(net.minecraft.entity.LivingEntity entity, org.joml.Matrix4f matrix) {
		if (ENABLED && thirdPerson() && entity == MinecraftClient.getInstance().player)
			bodyYaw = (float) Math.toDegrees(Math.atan2(-matrix.m02(), matrix.m00()));
	}
	private static double stalls(int from, int until, boolean body) {
		int count = 0, stalled = 0;
		for (int i = 1; i < samples.size(); i++) {
			var a = samples.get(i - 1); var b = samples.get(i);
			if (a.tick < from || b.tick > until) continue;
			double seconds = (b.time - a.time) / 1e9;
			if (seconds < .005 || seconds > .1) continue;
			float first = body ? a.bodyYaw : a.yaw, second = body ? b.bodyYaw : b.yaw;
			if (!Float.isFinite(first) || !Float.isFinite(second)) continue;
			count++;
			if (Math.abs(MathHelper.wrapDegrees(second - first)) / seconds < 1) stalled++;
		}
		return count < 20 ? 1 : stalled / (double) count;
	}
	private record Frame(long time, int tick, float yaw, float bodyYaw) { }
	private static void press() {
		ClientPlayNetworking.send(new HeadSkillC2SPayload(true)); ClientPlayNetworking.send(new HeadSkillC2SPayload(false));
	}
	public static void beforeOrientation(GoatChargeS2CPayload payload) {
		stopping = !payload.active() && GoatChargeControl.active();
		probing = ENABLED && (payload.active() || stopping) && tick >= 55 && tick <= 121;
		if (!probing) return;
		var client = MinecraftClient.getInstance();
		probe.update(client.world, client.player, thirdPerson(), inverse(), .5F);
		beforeYaw = probe.getYaw();
		beforePosition = probe.getPos();
	}
	public static void afterOrientation() {
		if (!probing) return;
		var client = MinecraftClient.getInstance();
		// Compare the real camera at identical simulation time, isolating a
		// network snap from normal between-frame movement or a low frame rate.
		probe.update(client.world, client.player, thirdPerson(), inverse(), .5F);
		float jump = Math.abs(MathHelper.wrapDegrees(probe.getYaw() - beforeYaw));
		double positionJump = probe.getPos().distanceTo(beforePosition);
		if (stopping) stopPositionJump = Math.max(stopPositionJump, positionJump);
		else if (tick < 90) normalPositionJump = Math.max(normalPositionJump, positionJump);
		else driftPositionJump = Math.max(driftPositionJump, positionJump);
		if (stopping) stopJump = Math.max(stopJump, jump);
		else if (tick < 90) normalJump = Math.max(normalJump, jump); else driftJump = Math.max(driftJump, jump);
		packets++;
	}
	public static void correction() { if (ENABLED && tick >= 45 && tick <= 120) corrections++; }
	public static void velocity() { if (ENABLED && tick >= 45 && tick <= 120) velocityPackets++; }
}
