package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.blaze.BlazeFire;
import com.nakami.mcheadfunction.net.BlazeFlameS2CPayload;
import com.nakami.mcheadfunction.net.BlazeFlameS2CPayload.Kind;
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

/** Directional fire jets and brief ground eruptions. Combat stays server-side. */
public final class BlazeBreathEffects {
	private BlazeBreathEffects() { }

	public static void receive(MinecraftClient client, BlazeFlameS2CPayload pulse) {
		if (client.world == null || client.player == null || client.player.squaredDistanceTo(pulse.origin()) > 48 * 48) return;
		if (pulse.kind().core()) { BlazeCoreEffects.receive(client, pulse); return; }
		Entity source = client.world.getEntityById(pulse.source());
		if (source == null) return;
		Kind kind = pulse.kind();
		boolean jet = kind == Kind.BREATH || kind == Kind.BURST;
		Vec3d forward = pulse.direction().normalize();
		Vec3d side = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();
		if (side.lengthSquared() < 0.01) side = new Vec3d(1, 0, 0);
		Vec3d up = side.crossProduct(forward).normalize();
		boolean firstPerson = source == client.getCameraEntity() && client.options.getPerspective().isFirstPerson();
		Vec3d mouth = jet ? pulse.origin().add(forward.multiply(firstPerson ? 0.65 : 0.30))
			.add(up.multiply(firstPerson ? -0.40 : -0.14)) : pulse.origin();
		if (ray(client.world, source, pulse.origin(), mouth).getType() != HitResult.Type.MISS) return;
		var atlas = (SpriteAtlasTexture) client.getTextureManager().getTexture(SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE);
		Sprite sprite = atlas.getSprite(Identifier.ofVanilla("flame"));
		float heat = MathHelper.clamp((float) pulse.heat() / BlazeFire.MAX_HEAT, 0, 1);
		float charge = BlazeFire.highHeatCharge(pulse.heat());
		ParticlesMode setting = client.options.getParticles().getValue();
		int count = switch (kind) {
			case BREATH -> setting == ParticlesMode.MINIMAL ? 3 : 7;
			case BURST -> setting == ParticlesMode.MINIMAL ? 48 : setting == ParticlesMode.DECREASED ? 72 : 100;
			case PILLAR -> setting == ParticlesMode.MINIMAL ? 8 : 12;
			case FUSE -> 4;
			case IMPACT -> setting == ParticlesMode.MINIMAL ? 6 : 12;
			default -> throw new IllegalArgumentException("Not a breath event: " + kind);
		};
		var random = client.world.random;
		for (int i = 0; i < count; i++) {
			double spread = jet ? 0.08 : kind == Kind.PILLAR ? 0.38 : 0.22;
			Vec3d start = mouth.add(side.multiply((random.nextDouble() - 0.5) * spread * 2))
				.add(up.multiply((random.nextDouble() - 0.5) * spread));
			if (kind == Kind.PILLAR) start = start.add(forward.multiply(pulse.range() * i / count * 0.92));
			if (kind == Kind.FUSE) start = new Vec3d(start.x, mouth.y + 0.08, start.z);
			double speed = switch (kind) {
				case BREATH -> pulse.range() * (0.14 + random.nextDouble() * 0.025 + heat * 0.008);
				case BURST -> pulse.range() / 6 * (0.55 + random.nextDouble() * 0.50);
				case PILLAR -> 0.025 + random.nextDouble() * 0.025;
				case FUSE -> 0.04;
				case IMPACT -> 0.04 + random.nextDouble() * 0.08;
				default -> throw new IllegalArgumentException("Not a breath event: " + kind);
			};
			double lateral = switch (kind) {
				case BURST -> (3.0 + charge * 0.4) / 6 * 2;
				case IMPACT -> 0.8;
				case PILLAR -> 0.10;
				default -> 0.09;
			};
			double vertical = kind == Kind.IMPACT ? 0.65 : kind == Kind.BURST ? 0.26 : 0.06;
			Vec3d velocity = forward.multiply(speed)
				.add(side.multiply((random.nextDouble() - 0.5) * lateral))
				.add(up.multiply((random.nextDouble() - 0.5) * vertical));
			// Keep a dense leading edge and a slower, connected tail in the single release pulse.
			start = start.add(velocity.multiply((double) i / count * (jet ? 0.65 : 0.25)));
			if (ray(client.world, source, pulse.origin(), start).getType() != HitResult.Type.MISS) continue;
			float size = switch (kind) {
				case BREATH -> 0.22F + heat * 0.10F;
				case BURST -> 0.48F + charge * 0.22F;
				case PILLAR -> 0.56F;
				case FUSE -> 0.30F;
				case IMPACT -> 0.26F;
				default -> throw new IllegalArgumentException("Not a breath event: " + kind);
			};
			size += random.nextFloat() * 0.12F;
			if (firstPerson && jet) size *= kind == Kind.BURST ? 0.8F : 0.65F;
			client.particleManager.addParticle(new Flame(client.world, source, sprite, start, velocity,
				pulse.origin(), forward, pulse.range(), size, kind, heat));
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
		private final Kind kind;
		private final float heat;
		private int dying;

		Flame(ClientWorld world, Entity source, Sprite sprite, Vec3d start, Vec3d velocity,
				Vec3d origin, Vec3d forward, double range, float size, Kind kind, float heat) {
			super(world, start.x, start.y, start.z);
			this.source = source; this.origin = origin; this.forward = forward; this.range = range;
			this.peakSize = size; this.kind = kind; this.heat = heat;
			setSprite(sprite); setVelocity(velocity.x, velocity.y, velocity.z);
			maxAge = switch (kind) {
				case BREATH -> 13;
				case BURST -> 12;
				case PILLAR -> 10;
				case FUSE -> 4;
				case IMPACT -> 7;
				default -> throw new IllegalArgumentException("Not a breath event: " + kind);
			};
			collidesWithWorld = false;
		}

		@Override public void tick() {
			prevPosX = x; prevPosY = y; prevPosZ = z;
			if (++age >= maxAge || dying >= 3) { markDead(); return; }
			Vec3d from = new Vec3d(x, y, z);
			Vec3d step = new Vec3d(velocityX, velocityY, velocityZ);
			double remaining = range - from.subtract(origin).dotProduct(forward);
			double advance = step.dotProduct(forward);
			if (dying > 0 || remaining <= 0.001) {
				dying++; step = Vec3d.ZERO;
			} else if (advance > remaining) {
				step = step.multiply(remaining / advance); dying = 1;
			}
			var hit = ray(world, source, from, from.add(step));
			if (hit.getType() != HitResult.Type.MISS) {
				if (random.nextInt(4) == 0) {
					Vec3d normal = Vec3d.of(hit.getSide().getVector());
					Vec3d point = hit.getPos().add(normal.multiply(0.06));
					boolean wet = !world.getFluidState(hit.getBlockPos()).isEmpty();
					world.addParticle(wet ? ParticleTypes.SMOKE : ParticleTypes.SMALL_FLAME, point.x, point.y, point.z,
						normal.x * 0.065, 0.035 + normal.y * 0.025, normal.z * 0.065);
				}
				markDead(); return;
			}
			setPos(x + step.x, y + step.y, z + step.z);
			// Pressure holds the jet straight; only the last few ticks curl up into embers.
			boolean embers = age > maxAge - 4;
			double drag = embers ? 0.78 : kind == Kind.BREATH ? 0.99 : 1;
			velocityX *= drag; velocityY = velocityY * drag + (embers ? 0.008 : 0); velocityZ *= drag;
			float end = Math.max(dying / 3F, Math.max(0, (age - (maxAge - 4)) / 4F));
			setColor(1, MathHelper.clamp(0.60F + heat * 0.40F - end * 0.5F, 0, 1),
				MathHelper.clamp(0.10F + heat * 0.65F - end * 0.7F, 0, 1));
		}

		@Override public float getSize(float tickDelta) {
			float time = age + tickDelta;
			float grow = Math.min(1, 0.65F + time * 0.16F);
			float fade = Math.min(1, Math.min((maxAge - time) / 3, dying > 0 ? (3 - dying - tickDelta) / 3 : 1));
			return peakSize * grow * Math.max(0, fade);
		}
		@Override public void buildGeometry(net.minecraft.client.render.VertexConsumer vertices, net.minecraft.client.render.Camera camera, float tickDelta) {
			if (kind == Kind.BREATH || kind == Kind.BURST) {
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
