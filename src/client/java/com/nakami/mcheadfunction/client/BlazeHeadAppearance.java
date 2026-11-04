package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.blaze.BlazeLook;
import com.nakami.mcheadfunction.head.HeadLookups;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.head.PlayerHeadAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;

/** The worn blaze head itself shows heat: it brightens, throbs when ready, then goes out. */
public final class BlazeHeadAppearance {
	public static BlazeLook.Phase lastPhase = BlazeLook.Phase.IDLE;
	public static int highFrames;
	public static int coolingFrames;
	public static int renderedHigh;
	public static int renderedCooling;

	private BlazeHeadAppearance() {
	}

	public static void tick(MinecraftClient client) {
		if (client.world == null || client.player == null || client.isPaused()) {
			return;
		}
		for (var player : client.world.getPlayers()) {
			if (HeadLookups.worn(player) != HeadType.BLAZE) {
				continue;
			}
			var state = PlayerHeadAccess.state(player);
			if (BlazeLook.phase(state.blazeHeat, state.blazeCooldown) != BlazeLook.Phase.COOLING) {
				continue;
			}
			if (player.age % 2 != 0) {
				continue;
			}
			var head = player.getEyePos();
			var random = player.getRandom();
			client.world.addParticle(ParticleTypes.SMOKE,
				head.x + (random.nextFloat() - 0.5F) * 0.22,
				head.y + 0.08,
				head.z + (random.nextFloat() - 0.5F) * 0.22,
				0, 0.03, 0);
			if (BlazeLook.ash(state.blazeHeat, state.blazeCooldown) > 0.55F && player.age % 4 == 0) {
				client.world.addParticle(ParticleTypes.LARGE_SMOKE, head.x, head.y, head.z, 0, 0.02, 0);
			}
		}
	}

	public static float pulseScale(LivingEntity entity, float tickDelta) {
		Look look = look(entity, tickDelta);
		if (look == null || look.phase != BlazeLook.Phase.HIGH) {
			return 1;
		}
		return 1.04F + 0.05F * look.pulse;
	}

	public static VertexConsumerProvider wrap(LivingEntity entity, VertexConsumerProvider vertices, float tickDelta) {
		Look look = look(entity, tickDelta);
		if (look == null || look.phase == BlazeLook.Phase.IDLE) {
			return vertices;
		}
		note(look.phase, entity);
		return layer -> new Tint(vertices.getBuffer(layer), look.red, look.green, look.blue, look.fullBright);
	}

	public static void worn(LivingEntity entity, MatrixStack matrices, VertexConsumerProvider vertices, float tickDelta) {
		Look look = look(entity, tickDelta);
		if (look == null || look.phase != BlazeLook.Phase.HIGH) {
			return;
		}
		var player = (PlayerEntity) entity;
		float alpha = 0.22F + 0.38F * look.pulse;
		var aura = new Tint(vertices.getBuffer(
			RenderLayer.getEntityTranslucentEmissive(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)),
			1, 1, 0.72F, false);
		aura.alpha = alpha;
		matrices.push();
		matrices.scale(1.06F, 1.06F, 1.06F);
		MinecraftClient.getInstance().getItemRenderer().renderItem(
			player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD),
			ModelTransformationMode.NONE, 0xF000F0, OverlayTexture.DEFAULT_UV,
			matrices, ignored -> aura, player.getWorld(), player.getId());
		matrices.pop();
	}

	private static void note(BlazeLook.Phase phase, LivingEntity entity) {
		if (entity != MinecraftClient.getInstance().player) {
			return;
		}
		lastPhase = phase;
		if (phase == BlazeLook.Phase.HIGH) {
			highFrames++;
			renderedHigh++;
		}
		if (phase == BlazeLook.Phase.COOLING) {
			coolingFrames++;
			renderedCooling++;
		}
	}

	private static Look look(LivingEntity entity, float tickDelta) {
		if (!(entity instanceof PlayerEntity player) || HeadLookups.worn(player) != HeadType.BLAZE) {
			return null;
		}
		var state = PlayerHeadAccess.state(player);
		int heat = state.blazeHeat;
		int cooldown = state.blazeCooldown;
		BlazeLook.Phase phase = BlazeLook.phase(heat, cooldown);
		float pulse = BlazeLook.pulse(heat, cooldown, player.age + tickDelta);
		float glow = BlazeLook.glow(heat, cooldown);
		float ash = BlazeLook.ash(heat, cooldown);
		return switch (phase) {
			case IDLE -> new Look(phase, 1, 1, 1, 1, false);
			case HEATING -> new Look(phase, 1, 0.62F + 0.30F * glow, 0.18F + 0.22F * glow, 1, glow > 0.45F);
			case HIGH -> new Look(phase, 1, 0.92F + 0.08F * pulse, 0.55F + 0.40F * pulse, pulse, true);
			case COOLING -> {
				float remain = 1 - ash;
				yield new Look(phase, 0.18F + 0.55F * remain, 0.16F + 0.45F * remain, 0.14F + 0.35F * remain, 1, false);
			}
		};
	}

	private static final class Look {
		final BlazeLook.Phase phase;
		final float red, green, blue, pulse;
		final boolean fullBright;

		Look(BlazeLook.Phase phase, float red, float green, float blue, float pulse, boolean fullBright) {
			this.phase = phase;
			this.red = red;
			this.green = green;
			this.blue = blue;
			this.pulse = pulse;
			this.fullBright = fullBright;
		}
	}

	private static final class Tint implements VertexConsumer {
		private final VertexConsumer target;
		private final float r, g, b;
		private final boolean fullBright;
		float alpha = 1;

		Tint(VertexConsumer target, float r, float g, float b, boolean fullBright) {
			this.target = target;
			this.r = r;
			this.g = g;
			this.b = b;
			this.fullBright = fullBright;
		}

		@Override
		public VertexConsumer vertex(float x, float y, float z) {
			target.vertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int a) {
			target.color(
				MathHelper.clamp((int) (red * r), 0, 255),
				MathHelper.clamp((int) (green * g), 0, 255),
				MathHelper.clamp((int) (blue * b), 0, 255),
				MathHelper.clamp((int) (a * alpha), 0, 255));
			return this;
		}

		@Override
		public VertexConsumer texture(float u, float v) {
			target.texture(u, v);
			return this;
		}

		@Override
		public VertexConsumer overlay(int u, int v) {
			target.overlay(u, v);
			return this;
		}

		@Override
		public VertexConsumer light(int u, int v) {
			if (fullBright) {
				target.light(0xF0, 0xF0);
			} else {
				target.light(u, v);
			}
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			target.normal(x, y, z);
			return this;
		}
	}
}
