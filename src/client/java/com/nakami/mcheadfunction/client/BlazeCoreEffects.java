package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.net.BlazeFlameS2CPayload;
import com.nakami.mcheadfunction.net.BlazeFlameS2CPayload.Kind;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.particle.*;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

/** Volumetric, depth-tested cores survive minimal particles; every transient expires without a stop packet. */
public final class BlazeCoreEffects {
	private static final RenderLayer GLOW = RenderLayer.getEntityTranslucentEmissive(Identifier.of("mc_head_function", "textures/effect/white.png"));
	private static final RenderLayer FIRE = RenderLayer.getEntityTranslucentEmissive(SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE);
	private static final Map<Integer, Pulse> charges = new HashMap<>(), flights = new HashMap<>();
	private static final List<Pulse> bursts = new ArrayList<>();
	private static final EnumMap<Kind, Integer> rendered = new EnumMap<>(Kind.class);
	private static Object currentWorld;
	private BlazeCoreEffects() { }
	private static final class Pulse {
		final BlazeFlameS2CPayload payload;
		final Vec3d previous;
		int age;
		Pulse(BlazeFlameS2CPayload payload, Vec3d previous) { this.payload = payload; this.previous = previous; }
	}
	private record FireBall(Vec3d center, double radius, float time, float alpha, int count) { }
	private record Tongue(Vec3d center, double size, float alpha) { }
	private static void world(MinecraftClient client) {
		if (currentWorld != client.world) {
			charges.clear(); flights.clear(); bursts.clear(); rendered.clear(); currentWorld = client.world;
		}
	}
	public static void receive(MinecraftClient client, BlazeFlameS2CPayload payload) {
		world(client);
		if (client.world == null) return;
		switch (payload.kind()) {
			case CORE_CHARGE -> charges.put(payload.source(), new Pulse(payload, payload.origin()));
			case CORE_FLY -> {
				var old = flights.get(payload.source());
				flights.put(payload.source(), new Pulse(payload, old == null
					? payload.origin().subtract(payload.direction().multiply(payload.range())) : old.payload.origin()));
			}
			case CORE_LAUNCH, CORE_BLAST -> {
				charges.remove(payload.source());
				if (payload.kind() == Kind.CORE_BLAST) flights.remove(payload.source());
				if (bursts.size() >= 32) bursts.removeFirst();
				bursts.add(new Pulse(payload, payload.origin()));
			}
			case CORE_FIZZLE -> { charges.remove(payload.source()); flights.remove(payload.source()); }
			default -> { return; }
		}
		if (payload.kind() == Kind.CORE_CHARGE || payload.kind() == Kind.CORE_FLY || payload.kind() == Kind.CORE_BLAST)
			sparks(client, payload);
	}
	public static void tick(MinecraftClient client) {
		world(client);
		charges.values().removeIf(p -> ++p.age > 3);
		flights.values().removeIf(p -> ++p.age > 3);
		bursts.removeIf(p -> ++p.age >= (p.payload.kind() == Kind.CORE_BLAST ? 16 : 6));
	}
	private static void sparks(MinecraftClient client, BlazeFlameS2CPayload pulse) {
		var atlas = (SpriteAtlasTexture) client.getTextureManager().getTexture(SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE);
		Sprite sprite = atlas.getSprite(Identifier.ofVanilla("flame"));
		boolean minimal = client.options.getParticles().getValue() == ParticlesMode.MINIMAL;
		int count = pulse.kind() == Kind.CORE_BLAST ? (minimal ? 32 : 88) : minimal ? 3 : 7;
		var random = client.world.random;
		for (int i = 0; i < count; i++) {
			double a = random.nextDouble() * Math.PI * 2, y = random.nextDouble() * 2 - 1;
			double horizontal = Math.sqrt(1 - y * y);
			Vec3d radial = new Vec3d(Math.cos(a) * horizontal, y, Math.sin(a) * horizontal);
			Vec3d start = pulse.origin(), velocity;
			int life; float size;
			if (pulse.kind() == Kind.CORE_CHARGE) {
				start = start.add(radial.multiply(1.3 + random.nextDouble() * 0.8));
				velocity = pulse.origin().subtract(start).multiply(0.19);
				life = 5; size = 0.12F + pulse.range() * 0.06F;
				if (client.player != null && pulse.source() == client.player.getId() && client.options.getPerspective().isFirstPerson()) {
					if (start.subtract(client.gameRenderer.getCamera().getPos()).dotProduct(pulse.direction()) < 0.75) continue;
					size *= 0.6F;
				}
			} else if (pulse.kind() == Kind.CORE_FLY) {
				start = start.subtract(pulse.direction().multiply(random.nextDouble() * 1.5)).add(radial.multiply(0.22));
				velocity = pulse.direction().multiply(-0.12).add(radial.multiply(0.035));
				life = 9; size = 0.32F;
			} else {
				velocity = radial.multiply((0.25 + random.nextDouble() * 0.45) * pulse.range() / 5);
				life = 13; size = 0.45F + random.nextFloat() * 0.25F;
			}
			client.particleManager.addParticle(new Spark(client.world, sprite, start, velocity, life, size));
		}
	}
	public static void afterEntities(WorldRenderContext context) {
		if (context.matrixStack() == null || context.consumers() == null) return;
		var client = MinecraftClient.getInstance();
		var matrices = context.matrixStack(); var buffer = context.consumers().getBuffer(GLOW);
		var fireballs = new ArrayList<FireBall>();
		var tongues = new ArrayList<Tongue>();
		var atlas = (SpriteAtlasTexture) client.getTextureManager().getTexture(SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE);
		var sprite = atlas.getSprite(Identifier.ofVanilla("flame"));
		Vec3d camera = context.camera().getPos(); float delta = context.tickCounter().getTickDelta(false);
		for (var pulse : charges.values()) {
			var p = pulse.payload;
			Vec3d center = p.origin();
			boolean firstPerson = client.player != null && p.source() == client.player.getId() && client.options.getPerspective().isFirstPerson();
			if (firstPerson) center = center.add(0, -0.4, 0);
			float time = p.heat() + delta;
			double radius = (0.12 + p.range() * 0.48) * (1 + Math.sin(time * 0.85) * 0.06);
			if (firstPerson) radius *= 0.65;
			sphere(buffer, matrices, center.subtract(camera), radius * 0.55, time, 1);
			fireballs.add(new FireBall(center, radius, time, 1, 24));
			Vec3d side = p.direction().crossProduct(new Vec3d(0, 1, 0)).normalize();
			if (side.lengthSquared() < 0.01) side = new Vec3d(1, 0, 0);
			Vec3d up = side.crossProduct(p.direction()).normalize();
			for (int i = 0; i < 12; i++) {
				double angle = i * Math.PI / 6 + time * 0.17;
				double distance = radius + 0.18 + (1 - ((time + i * 3) % 12) / 12) * 0.8;
				Vec3d point = center.subtract(camera).add(side.multiply(Math.cos(angle) * distance)).add(up.multiply(Math.sin(angle) * distance));
				quad(buffer, matrices, point, side.multiply(0.055), up.multiply(0.055), 0.9F, 0xFFD74A);
			}
			rendered.merge(Kind.CORE_CHARGE, 1, Integer::sum);
		}
		for (var pulse : flights.values()) {
			var p = pulse.payload;
			Vec3d center = pulse.previous.lerp(p.origin(), delta).subtract(camera);
			sphere(buffer, matrices, center, 0.35, context.world().getTime() + delta, 1);
			fireballs.add(new FireBall(center.add(camera), 0.70, context.world().getTime() + delta, 1, 24));
			// Overlapping fire tongues keep the wake thick and connected at full projectile speed.
			for (int i = 1; i <= 7; i++) fireballs.add(new FireBall(
				center.add(camera).subtract(p.direction().multiply(i * 0.42)),
				0.65 * (1 - i / 8.0), i + delta, (1 - i / 8F), 8));
			rendered.merge(Kind.CORE_FLY, 1, Integer::sum);
		}
		for (var pulse : bursts) {
			var p = pulse.payload; float age = pulse.age + delta;
			Vec3d center = p.origin().subtract(camera);
			if (p.kind() == Kind.CORE_LAUNCH) {
				Vec3d side = p.direction().crossProduct(new Vec3d(0, 1, 0)).normalize();
				if (side.lengthSquared() < 0.01) side = new Vec3d(1, 0, 0);
				ring(buffer, matrices, center.add(p.direction().multiply(age * 0.4)), side, side.crossProduct(p.direction()),
					0.2 + age * 0.18, 0.09, Math.max(0, 1 - age / 6), 0xFFE19C);
			} else {
				float fade = Math.max(0, 1 - age / 12);
				double scale = p.range() / 5;
				if (age < 4) sphere(buffer, matrices, center, (0.6 + age * 0.28) * scale, age * 2, 1 - age / 4);
				if (age < 11) fireballs.add(new FireBall(p.origin(), (0.7 + Math.min(1, age / 4) * 3.6) * scale, age * 2, fade, 64));
				var floor = context.world().raycast(new RaycastContext(p.origin(), p.origin().add(0, -4, 0),
					RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, client.player));
				double groundY = floor.getType() == HitResult.Type.MISS ? p.origin().y : floor.getPos().y + 0.08;
				Vec3d ground = new Vec3d(p.origin().x, groundY, p.origin().z).subtract(camera);
				double radius = 0.3 + (p.range() - 0.3) * Math.min(1, age / 10);
				ring(buffer, matrices, ground, new Vec3d(1, 0, 0), new Vec3d(0, 0, 1), radius, 0.22 * scale,
					Math.max(0, 1 - age / 16), 0xFFB02E);
				ring(buffer, matrices, ground.add(0, 0.16, 0), new Vec3d(1, 0, 0), new Vec3d(0, 0, 1), radius * 0.9, 0.07 * scale,
					Math.max(0, 1 - age / 13), 0xFFF1A5);
				int tonguesInRing = (int) (32 * scale);
				if (age < 13) for (int i = 0; i < tonguesInRing; i++) {
					double a = i * Math.PI * 2 / tonguesInRing;
					Vec3d point = ground.add(camera).add(Math.cos(a) * radius, 0.25, Math.sin(a) * radius);
					if (visible(context, p.origin(), point)) tongues.add(new Tongue(point, (0.42 + (i % 3) * 0.08) * scale, Math.max(0, 1 - age / 13)));
				}
			}
			rendered.merge(p.kind(), 1, Integer::sum);
		}
		// Finish the white-hot geometry before switching the shared provider to the flame atlas.
		var fire = context.consumers().getBuffer(FIRE);
		for (var ball : fireballs) fireball(context, fire, sprite, ball.center, ball.radius, ball.time, ball.alpha, ball.count);
		for (var tongue : tongues) flame(context, fire, sprite, tongue.center, tongue.size, tongue.alpha);
	}
	private static boolean visible(WorldRenderContext context, Vec3d from, Vec3d to) {
		return context.world().raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
			RaycastContext.FluidHandling.ANY, MinecraftClient.getInstance().player)).getType() == HitResult.Type.MISS;
	}
	private static void fireball(WorldRenderContext context, VertexConsumer buffer, Sprite sprite,
			Vec3d center, double radius, float time, float alpha, int count) {
		for (int i = 0; i < count; i++) {
			double y = 1 - (i + 0.5) * 2 / count, a = i * 2.39996 + time * 0.07;
			double horizontal = Math.sqrt(1 - y * y);
			Vec3d point = center.add(Math.cos(a) * horizontal * radius * 0.65,
				y * radius * 0.65, Math.sin(a) * horizontal * radius * 0.65);
			if (visible(context, center, point)) flame(context, buffer, sprite, point, radius * (0.38 + i % 3 * 0.07), alpha);
		}
	}
	private static void flame(WorldRenderContext context, VertexConsumer buffer, Sprite sprite, Vec3d point, double size, float alpha) {
		var rotation = context.camera().getRotation();
		var right = new org.joml.Vector3f(1, 0, 0).rotate(rotation);
		var up = new org.joml.Vector3f(0, 1, 0).rotate(rotation);
		Vec3d u = new Vec3d(right.x, right.y, right.z).multiply(size), v = new Vec3d(up.x, up.y, up.z).multiply(size);
		Vec3d center = point.subtract(context.camera().getPos());
		Vec3d[] points = {center.subtract(u).subtract(v), center.add(u).subtract(v), center.add(u).add(v), center.subtract(u).add(v)};
		for (int i = 0; i < 4; i++) {
			var p = points[i];
			buffer.vertex(context.matrixStack().peek().getPositionMatrix(), (float) p.x, (float) p.y, (float) p.z)
				.color(1F, 1F, 1F, Math.max(0, alpha))
				.texture(i == 1 || i == 2 ? sprite.getMaxU() : sprite.getMinU(), i < 2 ? sprite.getMaxV() : sprite.getMinV())
				.overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 1, 0);
		}
	}
	private static void sphere(VertexConsumer buffer, MatrixStack matrices, Vec3d center, double radius, float time, float alpha) {
		for (int y = 0; y < 6; y++) for (int x = 0; x < 12; x++) {
			int color = (x + y * 3 + (int) (time / 2)) % 7 < 2 ? 0xFFB32C : (x + y) % 3 == 0 ? 0xFFFFFF : 0xFFF5B0;
			for (int corner : new int[]{0, 3, 2, 1}) {
				double latitude = -Math.PI / 2 + (y + (corner >= 2 ? 1 : 0)) * Math.PI / 6;
				double angle = (x + (corner == 1 || corner == 2 ? 1 : 0)) * Math.PI / 6;
				double r = radius * (1 + 0.035 * Math.sin(angle * 5 + time));
				vertex(buffer, matrices, center.add(Math.cos(latitude) * Math.cos(angle) * r, Math.sin(latitude) * r, Math.cos(latitude) * Math.sin(angle) * r), alpha, color);
			}
		}
	}
	private static void ring(VertexConsumer buffer, MatrixStack matrices, Vec3d center, Vec3d u, Vec3d v, double radius, double width, float alpha, int color) {
		if (alpha <= 0) return;
		for (int i = 0; i < 40; i++) {
			double a = i * Math.PI / 20, b = (i + 1) * Math.PI / 20;
			Vec3d p = u.multiply(Math.cos(a)).add(v.multiply(Math.sin(a))), q = u.multiply(Math.cos(b)).add(v.multiply(Math.sin(b)));
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
	public static int rendered(Kind kind) { return rendered.getOrDefault(kind, 0); }
	public static boolean active() { return !charges.isEmpty() || !flights.isEmpty() || !bursts.isEmpty(); }
	private static final class Spark extends SpriteBillboardParticle {
		private final float peak;
		Spark(ClientWorld world, Sprite sprite, Vec3d start, Vec3d velocity, int life, float size) {
			super(world, start.x, start.y, start.z);
			setSprite(sprite); setVelocity(velocity.x, velocity.y, velocity.z); maxAge = life; peak = size;
			collidesWithWorld = true;
		}
		@Override public void tick() {
			prevPosX = x; prevPosY = y; prevPosZ = z;
			if (++age >= maxAge || !world.getFluidState(BlockPos.ofFloored(x, y, z)).isEmpty()) { markDead(); return; }
			move(velocityX, velocityY, velocityZ); velocityX *= 0.93; velocityY *= 0.93; velocityZ *= 0.93;
			setColor(1, 1 - age / (float) maxAge * 0.5F, 0.5F * (1 - age / (float) maxAge));
		}
		@Override public float getSize(float delta) { return peak * Math.max(0, 1 - (age + delta) / maxAge); }
		@Override public int getBrightness(float delta) { return LightmapTextureManager.MAX_LIGHT_COORDINATE; }
		@Override public ParticleTextureSheet getType() { return ParticleTextureSheet.PARTICLE_SHEET_OPAQUE; }
	}
}
