package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.net.GoatEffectsS2CPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Low, short-lived hoof wakes, outside-turn skid dust and a directional ram burst. */
public final class GoatChargeEffects {
	private static ClientWorld currentWorld;
	private static final int[] received = new int[3];
	private static int drawnDust, drawnHits;
	private GoatChargeEffects() { }
	public static void reset() {
		currentWorld = null; java.util.Arrays.fill(received, 0); drawnDust = drawnHits = 0;
	}
	public static void receive(MinecraftClient client, GoatEffectsS2CPayload pulse) {
		if (client.world == null || client.player == null || pulse.kind() < 0 || pulse.kind() > 2) return;
		if (currentWorld != client.world) {
			reset(); currentWorld = client.world;
		}
		if (client.player.squaredDistanceTo(pulse.origin()) > 48 * 48) return;
		received[pulse.kind()]++;
		Vec3d forward = pulse.direction().normalize();
		Vec3d right = new Vec3d(-forward.z, 0, forward.x);
		boolean minimal = client.options.getParticles().getValue() == ParticlesMode.MINIMAL;
		if (pulse.kind() == GoatEffectsS2CPayload.HIT) {
			// A tight white contact flash and chips thrown along the impact direction.
			int count = minimal ? 6 : 12;
			for (int i = 0; i < count; i++) {
				double angle = i * Math.PI * 2 / count;
				Vec3d radial = right.multiply(Math.cos(angle)).add(0, Math.sin(angle), 0);
				add(client, pulse.origin().add(0, .9, 0).add(radial.multiply(.12)),
					forward.multiply(.10).add(radial.multiply(.14)), .13F, 8, true);
			}
			for (int i = 0; i < (minimal ? 8 : 14); i++) {
				double angle = i * Math.PI * 2 / (minimal ? 8 : 14);
				Vec3d radial = new Vec3d(Math.cos(angle), 0, Math.sin(angle));
				Vec3d floor = pulse.origin().add(0, .08, 0);
				add(client, floor.add(radial.multiply(.2)), radial.multiply(.14).add(0, .025, 0), .24F, 12, false);
			}
			return;
		}
		var source = client.world.getEntityById(pulse.source());
		if (source == null || !source.isAlive()) return;
		Vec3d feet = source.getPos().add(0, .09, 0).subtract(forward.multiply(.30));
		boolean drifting = pulse.kind() == GoatEffectsS2CPayload.DRIFT;
		Vec3d outside = right.multiply(-Math.signum(pulse.steering()));
		int samples = minimal ? 1 : 2;
		for (int i = 0; i < samples; i++) {
			Vec3d root = feet.subtract(forward.multiply(i * .22));
			for (int hoof : new int[] {-1, 1}) {
				Vec3d start = root.add(right.multiply(hoof * .23));
				Vec3d velocity = forward.multiply(drifting ? -.08 : -.035)
					.add(drifting ? outside.multiply(.12) : right.multiply(hoof * .02)).add(0, .015, 0);
				add(client, start, velocity, drifting ? .34F : .20F, drifting ? 15 : 10, false);
			}
			if (drifting) add(client, root.add(outside.multiply(.38)), outside.multiply(.18).add(forward.multiply(-.08)).add(0, .025, 0), .42F, 16, false);
		}
	}
	private static void add(MinecraftClient client, Vec3d origin, Vec3d velocity, float size, int lifetime, boolean hit) {
		// Direct particles preserve a small core cue even at the minimal setting.
		client.particleManager.addParticle(new Puff(client.world, origin, velocity, size, lifetime, hit));
	}
	private static final class Puff extends SpriteBillboardParticle {
		private final float peak;
		private final boolean hit;
		private final SpriteAtlasTexture atlas;
		Puff(ClientWorld world, Vec3d origin, Vec3d velocity, float size, int lifetime, boolean hit) {
			super(world, origin.x, origin.y, origin.z);
			this.peak = size; this.hit = hit;
			atlas = (SpriteAtlasTexture) MinecraftClient.getInstance().getTextureManager().getTexture(SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE);
			setSprite(atlas.getSprite(Identifier.ofVanilla(hit ? "critical_hit" : "generic_7")));
			setVelocity(velocity.x, velocity.y, velocity.z);
			maxAge = lifetime; collidesWithWorld = true;
			setColor(hit ? 1F : .85F, hit ? .96F : .84F, hit ? .79F : .81F);
			setAlpha(hit ? 1 : .6F);
		}
		@Override public void tick() {
			prevPosX = x; prevPosY = y; prevPosZ = z;
			if (++age >= maxAge) { markDead(); return; }
			move(velocityX, velocityY, velocityZ);
			velocityX *= .86; velocityY *= .9; velocityZ *= .86;
			float progress = age / (float) maxAge;
			setAlpha((hit ? 1 : .6F) * (1 - progress));
			if (!hit) setSprite(atlas.getSprite(Identifier.ofVanilla("generic_" + Math.max(0, 7 - age * 8 / maxAge))));
		}
		@Override public float getSize(float delta) {
			float progress = Math.min(1, (age + delta) / maxAge);
			return peak * (hit ? 1 - progress : .65F + progress * .8F);
		}
		@Override public void buildGeometry(net.minecraft.client.render.VertexConsumer vertices, net.minecraft.client.render.Camera camera, float delta) {
			super.buildGeometry(vertices, camera, delta);
			if (hit) drawnHits++; else drawnDust++;
		}
		@Override public int getBrightness(float delta) { return hit ? LightmapTextureManager.MAX_LIGHT_COORDINATE : super.getBrightness(delta); }
		@Override public ParticleTextureSheet getType() { return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT; }
	}
	public static int received(int kind) { return received[kind]; }
	public static int drawnDust() { return drawnDust; }
	public static int drawnHits() { return drawnHits; }
}
