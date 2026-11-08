package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.net.GoatEffectsS2CPayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

/** Brief network-fed visual lean. Expiry also handles stopping and losing tracking. */
public final class GoatDriftPose {
	private static final Map<Integer, Pose> poses = new HashMap<>();
	private static ClientWorld world;
	private GoatDriftPose() { }
	private static void world(MinecraftClient client) {
		if (world != client.world) { poses.clear(); world = client.world; }
	}
	public static void receive(MinecraftClient client, GoatEffectsS2CPayload pulse) {
		world(client);
		if (world == null || pulse.kind() == GoatEffectsS2CPayload.HIT) return;
		var pose = poses.computeIfAbsent(pulse.source(), id -> new Pose());
		pose.target = pulse.kind() == GoatEffectsS2CPayload.DRIFT ? pulse.steering() : 0;
		pose.remaining = 4;
	}
	public static void tick(MinecraftClient client) {
		world(client);
		if (world == null) return;
		var entries = poses.entrySet().iterator();
		while (entries.hasNext()) {
			var entry = entries.next();
			var pose = entry.getValue();
			var entity = world.getEntityById(entry.getKey());
			if (entity == null || !entity.isAlive()) { entries.remove(); continue; }
			if (--pose.remaining <= 0) pose.target = 0;
			pose.previous = pose.amount;
			pose.amount += (pose.target - pose.amount) * .32F;
			if (pose.target == 0 && Math.abs(pose.amount) < .01) entries.remove();
		}
	}
	public static float lean(LivingEntity entity, float delta) {
		var pose = poses.get(entity.getId());
		return entity.getWorld() == world && pose != null ? MathHelper.lerp(delta, pose.previous, pose.amount) * 18F : 0;
	}
	public static float cameraRoll(float delta) {
		var client = MinecraftClient.getInstance();
		return client.player != null && client.options.getPerspective().isFirstPerson() ? lean(client.player, delta) / 6 : 0;
	}
	private static final class Pose {
		float amount, previous, target;
		int remaining;
	}
}
