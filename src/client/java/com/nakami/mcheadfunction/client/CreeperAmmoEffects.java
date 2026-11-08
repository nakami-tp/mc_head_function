package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.entity.HeadAmmoEntity;
import com.nakami.mcheadfunction.net.CreeperBlastS2CPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Short, depth-tested pixel wakes and surface-aligned pressure waves, even on minimal particles. */
public final class CreeperAmmoEffects {
	public static final RenderLayer GLOW = RenderLayer.getEntityTranslucentEmissive(
		Identifier.ofVanilla("textures/block/white_concrete.png"));
	private static final List<Blast> blasts = new ArrayList<>();
	private static final Map<Integer, List<Vec3d>> trails = new HashMap<>();
	private static Object currentWorld;
	private static long renderedBlastAt = Long.MIN_VALUE;
	private static int receivedBlasts, renderedWakes, renderedAttachments;
	private CreeperAmmoEffects() { }

	private static void world(MinecraftClient client) {
		if (currentWorld != client.world) {
			blasts.clear(); trails.clear(); currentWorld = client.world;
			receivedBlasts = renderedWakes = renderedAttachments = 0;
			renderedBlastAt = Long.MIN_VALUE;
		}
	}
	public static void receive(MinecraftClient client, CreeperBlastS2CPayload payload) {
		world(client);
		if (client.world == null) return;
		if (blasts.size() >= 32) blasts.removeFirst();
		blasts.add(new Blast(payload));
		receivedBlasts++;
	}
	public static void tick(MinecraftClient client) {
		world(client);
		if (client.world == null) return;
		for (var blast : blasts) blast.age++;
		blasts.removeIf(b -> b.age >= 14);
		trails.entrySet().removeIf(e -> !(client.world.getEntityById(e.getKey()) instanceof HeadAmmoEntity ammo) || ammo.isPlanted());
		for (var entity : client.world.getEntities()) {
			if (!(entity instanceof HeadAmmoEntity ammo) || ammo.isPlanted()
				|| client.player == null || ammo.squaredDistanceTo(client.player) > 48 * 48) continue;
			var points = trails.computeIfAbsent(ammo.getId(), key -> new ArrayList<>());
			points.add(ammo.getPos().add(0, ammo.getHeight() / 2, 0));
			if (points.size() > 6) points.removeFirst();
		}
	}

	/** In the ammo's surface-local space, +Y is the contact normal. */
	public static void attachment(MatrixStack matrices, VertexConsumer buffer, HeadAmmoEntity ammo, float delta) {
		float age = ammo.plantedTicks() + delta;
		boolean urgent = age >= 160;
		float pulse = (float) Math.pow(0.5 + 0.5 * Math.cos(age * Math.PI * 2 / (age >= 180 ? 5 : urgent ? 10 : 30)), 3);
		int color = urgent ? 0xFFAD37 : 0x9BFF49;
		// Four separated brackets leave the actual creeper face readable.
		for (int x : new int[] {-1, 1}) for (int z : new int[] {-1, 1}) {
			quad(buffer, matrices, new Vec3d(x * .15, .085, z * .21), new Vec3d(.06, 0, 0),
				new Vec3d(0, 0, .012), .5F + pulse * .5F, color);
			quad(buffer, matrices, new Vec3d(x * .21, .085, z * .15), new Vec3d(.012, 0, 0),
				new Vec3d(0, 0, .06), .5F + pulse * .5F, color);
		}
		// A single quick seating pulse when it sticks, not a permanent range indicator.
		if (age < 8) ring(buffer, matrices, new Vec3d(0, -.085, 0), new Vec3d(1, 0, 0),
			new Vec3d(0, 0, 1), .26 + age * .035, .025, (1 - age / 8) * .7F, color);
		renderedAttachments++;
	}

	public static void afterEntities(WorldRenderContext context) {
		if (context.matrixStack() == null || context.consumers() == null) return;
		var matrices = context.matrixStack();
		var buffer = context.consumers().getBuffer(GLOW);
		var camera = context.camera().getPos();
		float delta = context.tickCounter().getTickDelta(false);
		var rotation = context.camera().getRotation();
		var r = new org.joml.Vector3f(1, 0, 0).rotate(rotation);
		var u = new org.joml.Vector3f(0, 1, 0).rotate(rotation);
		Vec3d right = new Vec3d(r.x, r.y, r.z), up = new Vec3d(u.x, u.y, u.z);
		for (var entry : trails.entrySet()) {
			if (!(context.world().getEntityById(entry.getKey()) instanceof HeadAmmoEntity ammo) || ammo.isPlanted()) continue;
			var points = entry.getValue();
			Vec3d tip = ammo.getLerpedPos(delta).add(0, ammo.getHeight() / 2, 0);
			for (int i = 0; i < points.size(); i++) {
				float t = (i + 1F) / points.size();
				Vec3d next = i == points.size() - 1 ? tip : points.get(i + 1);
				for (int j = 0; j < 3; j++) {
					Vec3d point = points.get(i).lerp(next, j / 3.0).subtract(camera);
					quad(buffer, matrices, point, right.multiply(.012 + .025 * t), up.multiply(.012 + .025 * t),
						t * .65F, i == points.size() - 1 ? 0xFFE3A0 : 0x9BFF49);
				}
			}
			renderedWakes++;
		}
		for (var blast : blasts) {
			float age = blast.age + delta;
			Vec3d center = blast.payload.origin().subtract(camera);
			Vec3d normal = blast.payload.face() < 0 ? new Vec3d(0, 1, 0)
				: Vec3d.of(Direction.byId(blast.payload.face()).getVector());
			Vec3d tangent = Math.abs(normal.y) > .5 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
			Vec3d side = normal.crossProduct(tangent);
			if (age < 4) {
				float fade = 1 - age / 4;
				quad(buffer, matrices, center.add(normal.multiply(.12)), right.multiply(.32 + age * .07),
					up.multiply(.32 + age * .07), fade, 0xFFF4B9);
				for (int i = 0; i < 8; i++) {
					double a = i * Math.PI / 4;
					Vec3d ray = right.multiply(Math.cos(a)).add(up.multiply(Math.sin(a)));
					Vec3d width = right.multiply(-Math.sin(a)).add(up.multiply(Math.cos(a)));
					quad(buffer, matrices, center.add(ray.multiply(.28 + age * .13)), ray.multiply(.18 + age * .04),
						width.multiply(.045 * fade), fade, i % 2 == 0 ? 0xFFB44B : 0xFFF4B9);
				}
			}
			if (age < 9) {
				double radius = .2 + 2.0 * (1 - Math.pow(1 - age / 9, 2));
				ring(buffer, matrices, center.add(normal.multiply(.08 + age * .015)), tangent, side,
					radius, .12 * (1 - age / 10), (1 - age / 9) * .95F, 0xB6FF58);
				ring(buffer, matrices, center.add(normal.multiply(.12 + age * .025)), tangent, side,
					radius * .78, .055 * (1 - age / 10), (1 - age / 9) * .8F, 0xFFB44B);
				if (blast.payload.face() < 0) ring(buffer, matrices, center, tangent, normal,
					radius, .035, (1 - age / 9) * .5F, 0xFFB44B);
			}
			// Deterministic outward debris: the normal component makes a wall blast kick out from the wall.
			for (int i = 0; i < 18; i++) {
				double angle = i * 2.39996;
				double distance = age * (.095 + i % 4 * .022);
				Vec3d point = center.add(tangent.multiply(Math.cos(angle) * distance))
					.add(side.multiply(Math.sin(angle) * distance))
					.add(normal.multiply(.1 + age * (.055 + i % 3 * .025)))
					.add(0, -age * age * .0025, 0);
				double size = (.04 + i % 3 * .016) * (1 - age / 14);
				quad(buffer, matrices, point, right.multiply(size), up.multiply(size), 1 - age / 14,
					i % 3 == 0 ? 0xB6FF58 : 0xFFB44B);
			}
			renderedBlastAt = context.world().getTime();
		}
	}
	private static void ring(VertexConsumer buffer, MatrixStack matrices, Vec3d center, Vec3d u, Vec3d v,
			double radius, double width, float alpha, int color) {
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI * 2 / 24, b = (i + 1) * Math.PI * 2 / 24;
			Vec3d p = u.multiply(Math.cos(a)).add(v.multiply(Math.sin(a)));
			Vec3d q = u.multiply(Math.cos(b)).add(v.multiply(Math.sin(b)));
			vertex(buffer, matrices, center.add(p.multiply(radius - width)), alpha, color);
			vertex(buffer, matrices, center.add(p.multiply(radius + width)), alpha, color);
			vertex(buffer, matrices, center.add(q.multiply(radius + width)), alpha, color);
			vertex(buffer, matrices, center.add(q.multiply(radius - width)), alpha, color);
		}
	}
	private static void quad(VertexConsumer buffer, MatrixStack matrices, Vec3d center, Vec3d u, Vec3d v, float alpha, int color) {
		vertex(buffer, matrices, center.subtract(u).subtract(v), alpha, color);
		vertex(buffer, matrices, center.add(u).subtract(v), alpha, color);
		vertex(buffer, matrices, center.add(u).add(v), alpha, color);
		vertex(buffer, matrices, center.subtract(u).add(v), alpha, color);
	}
	private static void vertex(VertexConsumer buffer, MatrixStack matrices, Vec3d p, float alpha, int color) {
		buffer.vertex(matrices.peek().getPositionMatrix(), (float) p.x, (float) p.y, (float) p.z)
			.color((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, Math.max(0, alpha))
			.texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 1, 0);
	}
	public static int receivedBlasts() { return receivedBlasts; }
	public static int activeBlasts() { return blasts.size(); }
	public static long renderedBlastAt() { return renderedBlastAt; }
	public static int renderedWakes() { return renderedWakes; }
	public static int renderedAttachments() { return renderedAttachments; }
	private static final class Blast {
		final CreeperBlastS2CPayload payload;
		int age;
		Blast(CreeperBlastS2CPayload payload) { this.payload = payload; }
	}
}
