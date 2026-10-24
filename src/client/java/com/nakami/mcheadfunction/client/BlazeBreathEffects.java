package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.blaze.BlazeFire;
import com.nakami.mcheadfunction.net.BlazeFlameS2CPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Moving, opaque pixel flames using the resource pack's vanilla flame sprite. Combat stays server-side. */
public final class BlazeBreathEffects {
	private BlazeBreathEffects() { }

	public static void receive(MinecraftClient client, BlazeFlameS2CPayload pulse) {
		if (client.world == null || client.player == null || client.player.squaredDistanceTo(pulse.origin()) > 48 * 48) return;
		Entity source = client.world.getEntityById(pulse.source());
		if (source == null) return;
		Vec3d forward = pulse.direction().normalize();
		Vec3d side = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();
		if (side.lengthSquared() < 0.01) side = new Vec3d(1, 0, 0);
		Vec3d up = side.crossProduct(forward).normalize();
		boolean firstPerson = source == client.getCameraEntity() && client.options.getPerspective().isFirstPerson();
		Vec3d mouth = pulse.wave() ? pulse.origin() : pulse.origin().add(forward.multiply(firstPerson ? 0.65 : 0.30))
			.add(up.multiply(firstPerson ? -0.40 : -0.14));
		var opening = ray(client.world, source, pulse.origin(), mouth);
		if (opening.getType() != HitResult.Type.MISS) return;
		var atlas = (SpriteAtlasTexture) client.getTextureManager().getTexture(SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE);
		Sprite sprite = atlas.getSprite(Identifier.ofVanilla("flame"));
		float heat = MathHelper.clamp((float) pulse.heat() / BlazeFire.MAX_HEAT, 0, 1);
		ParticlesMode setting = client.options.getParticles().getValue();
		int count = setting == ParticlesMode.MINIMAL ? 2 : setting == ParticlesMode.DECREASED ? 4 : 6;
		if (pulse.wave()) count += 2;
		var random = client.world.random;
		for (int i = 0; i < count; i++) {
			double spread = pulse.wave() ? 0.19 : 0.065;
			Vec3d start = mouth.add(side.multiply((random.nextDouble() - 0.5) * spread * 2))
				.add(up.multiply((random.nextDouble() - 0.5) * spread));
			// Uneven bursts make overlapping tongues; each keeps its launch direction when the player turns.
			double speed = pulse.wave() ? 0.16 + random.nextDouble() * 0.12
				: pulse.range() * (0.105 + random.nextDouble() * 0.025 + heat * 0.012);
			Vec3d velocity = forward.multiply(speed)
				.add(side.multiply((random.nextDouble() - 0.5) * (0.16 + heat * 0.06)))
				.add(up.multiply((random.nextDouble() - 0.5) * 0.12));
			// Distribute births within this tick so the stream does not become equally spaced beads.
			start = start.add(velocity.multiply((double) i / count));
			if (ray(client.world, source, pulse.origin(), start).getType() != HitResult.Type.MISS) continue;
			float size = (pulse.wave() ? 0.30F : 0.18F) + random.nextFloat() * 0.10F + heat * 0.10F;
			if (firstPerson && !pulse.wave()) size *= 0.65F;
			client.particleManager.addParticle(new Flame(client.world, source, sprite, start, velocity,
				pulse.origin(), forward, pulse.range(), size, pulse.wave()));
		}
	}

	private static net.minecraft.util.hit.BlockHitResult ray(ClientWorld world, Entity source, Vec3d from, Vec3d to) {
		return world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, source));
	}

	private static final class Flame extends SpriteBillboardParticle {
		private final Entity source;
		private final Vec3d origin, forward;
		private final double range;
		private final float peakSize;
		private final boolean wave;
		private int dying;

		Flame(ClientWorld world, Entity source, Sprite sprite, Vec3d start, Vec3d velocity,
				Vec3d origin, Vec3d forward, double range, float size, boolean wave) {
			super(world, start.x, start.y, start.z);
			this.source = source; this.origin = origin; this.forward = forward; this.range = range;
			this.peakSize = size; this.wave = wave;
			setSprite(sprite);
			setVelocity(velocity.x, velocity.y, velocity.z);
			maxAge = wave ? 12 : 16;
			collidesWithWorld = false;
		}

		@Override public void tick() {
			prevPosX = x; prevPosY = y; prevPosZ = z;
			if (++age >= maxAge || dying >= 4) { markDead(); return; }
			Vec3d from = new Vec3d(x, y, z);
			Vec3d step = new Vec3d(velocityX, velocityY, velocityZ);
			double remaining = range - from.subtract(origin).dotProduct(forward);
			double advance = step.dotProduct(forward);
			if (dying > 0 || remaining <= 0.001) {
				dying++;
				step = Vec3d.ZERO;
			} else if (advance > remaining) {
				step = step.multiply(remaining / advance);
				dying = 1;
			}
			var hit = ray(world, source, from, from.add(step));
			if (hit.getType() != HitResult.Type.MISS) {
				// A swept ray prevents fast particles tunnelling through thin walls or water.
				if (random.nextInt(4) == 0) {
					Vec3d normal = Vec3d.of(hit.getSide().getVector());
					Vec3d point = hit.getPos().add(normal.multiply(0.06));
					boolean wet = !world.getFluidState(hit.getBlockPos()).isEmpty();
					world.addParticle(wet ? ParticleTypes.SMOKE : ParticleTypes.SMALL_FLAME, point.x, point.y, point.z,
						normal.x * 0.045, 0.035 + normal.y * 0.025, normal.z * 0.045);
				}
				markDead(); return;
			}
			setPos(x + step.x, y + step.y, z + step.z);
			velocityX *= 0.96; velocityY = velocityY * 0.96 + (wave ? 0.002 : 0.004); velocityZ *= 0.96;
			float end = Math.max(dying / 4F, Math.max(0, (age - (maxAge - 5)) / 5F));
			// Keep crisp alpha-tested pixels. Warm and shrink at the tip instead of making a translucent fog.
			setColor(1, 1 - end * 0.48F, 1 - end * 0.72F);
		}

		@Override public float getSize(float tickDelta) {
			float time = age + tickDelta;
			float grow = Math.min(1, 0.40F + time * 0.13F);
			float fade = Math.min(1, Math.min((maxAge - time) / 4, dying > 0 ? (4 - dying - tickDelta) / 4 : 1));
			return peakSize * grow * Math.max(0, fade);
		}
		@Override public void buildGeometry(net.minecraft.client.render.VertexConsumer vertices, net.minecraft.client.render.Camera camera, float tickDelta) {
			if (!wave) {
				// Point the pixel flame tips along the projected jet, rather than lining up upright candles.
				var projected = new org.joml.Vector3f((float) forward.x, (float) forward.y, (float) forward.z)
					.rotate(new org.joml.Quaternionf(camera.getRotation()).conjugate());
				angle = projected.x * projected.x + projected.y * projected.y > 0.05F
					? (float) Math.atan2(-projected.x, projected.y) : 0;
				prevAngle = angle;
			}
			super.buildGeometry(vertices, camera, tickDelta);
		}
		@Override public int getBrightness(float tickDelta) { return LightmapTextureManager.MAX_LIGHT_COORDINATE; }
		@Override public ParticleTextureSheet getType() { return ParticleTextureSheet.PARTICLE_SHEET_OPAQUE; }
	}
}
