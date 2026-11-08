package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.net.CreeperLaunchS2CPayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** A brief back-mounted flame wake for bomb-assisted flight; particles stay at their birth positions. */
public final class CreeperLaunchEffects {
	private static final Map<Integer, Launch> launches = new HashMap<>();
	private static Object currentWorld;
	private static int received, rendered;
	private CreeperLaunchEffects() { }
	private static void world(MinecraftClient client) {
		if (currentWorld != client.world) {
			launches.clear(); currentWorld = client.world; received = rendered = 0;
		}
	}
	public static void receive(MinecraftClient client, CreeperLaunchS2CPayload payload) {
		world(client);
		if (client.world == null) return;
		launches.put(payload.player(), new Launch(payload.velocity().normalize()));
		received++;
	}
	public static void tick(MinecraftClient client) {
		world(client);
		if (client.world == null) return;
		var iterator = launches.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			var burst = entry.getValue();
			if (!(client.world.getEntityById(entry.getKey()) instanceof PlayerEntity player)
				|| !player.isAlive() || player.isSpectator() || player.hasVehicle() || player.isTouchingWater()
				|| burst.age >= 18 || burst.age > 3 && player.isOnGround()) {
				iterator.remove(); continue;
			}
			// Body yaw anchors the nozzle to the back even when the head looks sideways.
			double yaw = Math.toRadians(player.bodyYaw);
			Vec3d back = new Vec3d(Math.sin(yaw), 0, -Math.cos(yaw));
			Vec3d motion = player.getVelocity();
			// Keep the thrust direction through the short burst; gravity must not flip
			// the nozzle upward when the player starts falling. Old flames still trace the arc.
			Vec3d direction = burst.forward.multiply(-1);
			Vec3d root = player.getPos().add(0, player.getHeight() * .57, 0).add(back.multiply(.30));
			var atlas = (SpriteAtlasTexture) client.getTextureManager().getTexture(SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE);
			Sprite sprite = atlas.getSprite(Identifier.ofVanilla("flame"));
			float power = Math.min(1, (18 - burst.age) / 6F);
			for (int i = 0; i < 3; i++) {
				// Sub-tick births keep a fast moving player connected to the flame stream.
				Vec3d start = root.subtract(motion.multiply(i / 3.0)).add(direction.multiply(.05 * i));
				Vec3d velocity = direction.multiply(.10 + i * .025).add(0, .018, 0);
				client.particleManager.addParticle(new Exhaust(client.world, sprite, start, velocity, direction,
					(.23F - i * .025F) * power, i));
			}
			burst.age++;
		}
	}
	private static final class Launch {
		final Vec3d forward;
		int age;
		Launch(Vec3d forward) { this.forward = forward; }
	}
	private static final class Exhaust extends SpriteBillboardParticle {
		private final float peak;
		private final Vec3d direction;
		Exhaust(ClientWorld world, Sprite sprite, Vec3d start, Vec3d velocity, Vec3d direction, float peak, int index) {
			super(world, start.x, start.y, start.z);
			this.peak = peak; this.direction = direction;
			setSprite(sprite); setVelocity(velocity.x, velocity.y, velocity.z);
			maxAge = 8 + index; collidesWithWorld = true;
		}
		@Override public void tick() {
			prevPosX = x; prevPosY = y; prevPosZ = z;
			if (++age >= maxAge || !world.getFluidState(net.minecraft.util.math.BlockPos.ofFloored(x, y, z)).isEmpty()) { markDead(); return; }
			move(velocityX, velocityY, velocityZ);
			velocityX *= .9; velocityY = velocityY * .9 + .008; velocityZ *= .9;
			float fade = age / (float) maxAge;
			setColor(1, .95F - fade * .5F, .65F * (1 - fade));
		}
		@Override public float getSize(float delta) { return peak * Math.max(0, 1 - (age + delta) / maxAge); }
		@Override public void buildGeometry(net.minecraft.client.render.VertexConsumer vertices, net.minecraft.client.render.Camera camera, float delta) {
			var projected = new org.joml.Vector3f((float) direction.x, (float) direction.y, (float) direction.z)
				.rotate(new org.joml.Quaternionf(camera.getRotation()).conjugate());
			angle = projected.x * projected.x + projected.y * projected.y > .02F
				? (float) Math.atan2(-projected.x, projected.y) : 0;
			prevAngle = angle;
			super.buildGeometry(vertices, camera, delta);
			rendered++;
		}
		@Override public int getBrightness(float delta) { return LightmapTextureManager.MAX_LIGHT_COORDINATE; }
		@Override public ParticleTextureSheet getType() { return ParticleTextureSheet.PARTICLE_SHEET_OPAQUE; }
	}
	public static int receivedLaunches() { return received; }
	public static int renderedFlames() { return rendered; }
	public static int activeLaunches() { return launches.size(); }
}
