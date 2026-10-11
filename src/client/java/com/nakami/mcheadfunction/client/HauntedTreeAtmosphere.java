package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.haunt.HauntedTreeBlockEntity;
import com.nakami.mcheadfunction.rule.HauntRules;
import java.util.WeakHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Tick-paced atmosphere and depth-tested emissive geometry, independent of particle settings. */
final class HauntedTreeAtmosphere {
	private static final RenderLayer GLOW = RenderLayer.getEntityTranslucentEmissive(
		Identifier.ofVanilla("textures/block/white_concrete.png"));
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int TRANSITION_TICKS = 60;
	private final WeakHashMap<HauntedTreeBlockEntity, State> states = new WeakHashMap<>();

	State update(HauntedTreeBlockEntity tree, long time) {
		State state = states.computeIfAbsent(tree, key -> new State(tree, time));
		if (state.lastTick == time) return state;
		state.lastTick = time;
		int tier = HauntRules.tier(tree.hungCount());
		if (tier > state.tier || (tree.haunting() && !state.haunting)) state.awakened = time;
		if (tree.hungCount() > state.count) state.added = time;
		// Taking heads or ending the event immediately cancels the old surge.
		if (tier < state.tier || (!tree.haunting() && state.haunting)) state.awakened = time - TRANSITION_TICKS;
		state.tier = tier;
		state.count = tree.hungCount();
		state.haunting = tree.haunting();
		emit(tree, state, time);
		return state;
	}

	private static void emit(HauntedTreeBlockEntity tree, State state, long time) {
		var world = tree.getWorld();
		if (world == null || (state.tier == 0 && !state.haunting)) return;
		var pos = tree.getPos();
		// No catch-up burst after being offscreen; at most one emission batch per world tick.
		if (time % 3 == 0) {
			double a = world.random.nextDouble() * Math.PI * 2;
			double radius = 1 + world.random.nextDouble() * 2.4;
			world.addParticle(ParticleTypes.ASH, pos.getX() + Math.cos(a) * radius,
				pos.getY() + 1 + world.random.nextDouble() * 7, pos.getZ() + Math.sin(a) * radius, 0, -0.015, 0);
			world.addParticle(ParticleTypes.SMOKE, pos.getX() + Math.cos(a) * radius,
				pos.getY() + 0.2, pos.getZ() + Math.sin(a) * radius, -Math.cos(a) * 0.018, 0.025, -Math.sin(a) * 0.018);
		}
		// Preserve the original freely rising vanilla souls throughout the canopy.
		// Match the old 60 FPS density, but pace births by world ticks instead of frames.
		int tier = state.haunting ? Math.max(3, state.tier) : state.tier;
		double budget = 0.36 * tier;
		int births = (int) budget + (world.random.nextDouble() < budget % 1 ? 1 : 0);
		for (int i = 0; i < births; i++) {
			world.addParticle(tier >= 2 ? ParticleTypes.SOUL : ParticleTypes.SMOKE,
				pos.getX() + 0.5 + (world.random.nextDouble() - 0.5) * 3,
				pos.getY() + 2 + world.random.nextDouble() * 6,
				pos.getZ() + 0.5 + (world.random.nextDouble() - 0.5) * 3, 0, 0.01, 0);
		}
	}

	static void render(State state, float delta,
					MatrixStack matrices, VertexConsumerProvider vertices) {
		double time = (double) state.lastTick + delta;
		int tier = state.haunting ? Math.max(3, state.tier) : state.tier;
		if (tier == 0) return;
		var buffer = vertices.getBuffer(GLOW);
		float r = tier == 1 ? 0.35F : 0.14F;
		float g = tier == 1 ? 0.31F : 0.85F;
		float b = tier == 1 ? 0.28F : 0.76F;
		// Low, drifting patches leave the trunk and individual faces legible.
		for (int i = 0; i < 7; i++) {
			double a = i * Math.PI * 2 / 7 + time * 0.003;
			Vec3d p = new Vec3d(Math.cos(a) * 1.9, 0.12 + i * 0.025, Math.sin(a) * 1.9);
			disc(matrices, buffer, p, 1.5F, (tier == 1 ? 0.18F : 0.10F), r, g, b, false);
		}
		for (int i = 0; i < 9; i++) {
			double rise = (time * 0.006 + i / 9.0) % 1;
			double a = i * 2.4 + time * 0.005;
			Vec3d p = new Vec3d(Math.cos(a) * (2.2 - rise * 0.5), 0.35 + rise * 2.1,
				Math.sin(a) * (2.2 - rise * 0.5));
			disc(matrices, buffer, p, 0.55F + (float) rise * 0.45F,
				(float) Math.sin(rise * Math.PI) * 0.25F, 0.40F, 0.44F, 0.42F, true);
		}
		if (tier >= 2) {
			int wisps = state.haunting ? 5 : tier == 3 ? 4 : 2;
			for (int i = 0; i < wisps; i++) {
				for (int tail = 13; tail >= 0; tail--) {
					double t = time - tail * 1.4;
					double a = t * (state.haunting ? 0.042 : 0.026) + i * Math.PI * 2 / wisps;
					double radius = 2.25 + 0.6 * Math.sin(t * 0.017 + i);
					double y = 2.0 + i * 1.35 + Math.sin(t * 0.025 + i) * 0.65;
					float fade = 1 - tail / 14F;
					disc(matrices, buffer, new Vec3d(Math.cos(a) * radius, y, Math.sin(a) * radius),
						0.08F + fade * 0.09F, fade * 0.6F, 0.18F, 1, 0.83F, true);
				}
			}
		}
		double progress = (time - state.awakened) / TRANSITION_TICKS;
		if (progress >= 0 && progress < 1) {
			float strength = (float) Math.sin(Math.PI * progress);
			// A broken crown climbs from the roots and opens out as the tree awakens.
			ring(matrices, buffer, time, progress * 10.3, 0.8 + progress * 3.1,
				strength * 0.6F, r, g, b);
			ring(matrices, buffer, -time, 0.17, 0.5 + progress * 4.5,
				strength * 0.4F, r, g, b);
		}
	}

	static void headHalo(State state, double time, int slot, Vec3d center,
						MatrixStack matrices, VertexConsumerProvider vertices) {
		if (state.tier < 3 && !state.haunting) return;
		float breath = (float) (0.5 + 0.5 * Math.sin(time * 0.065 + slot * 1.7));
		float surge = MathHelper.clamp(1 - (float) (time - state.awakened) / TRANSITION_TICKS, 0, 1);
		var buffer = vertices.getBuffer(GLOW);
		disc(matrices, buffer, center, 0.68F + breath * 0.12F + surge * 0.15F,
			0.30F + breath * 0.15F + surge * 0.18F, 0.15F, 0.96F, 0.75F, true);
		// A small detached ember makes the aura read as a living flame rather than a bright item.
		disc(matrices, buffer, center.add(Math.sin(time * 0.06 + slot) * 0.15, 0.52 + breath * 0.12, 0),
			0.14F, 0.7F, 0.55F, 1, 0.86F, true);
	}

	private static void ring(MatrixStack matrices, VertexConsumer buffer, double time, double y,
							double radius, float alpha, float r, float g, float b) {
		for (int i = 0; i < 64; i++) {
			double a = i * Math.PI * 2 / 64;
			double n = (i + 1) * Math.PI * 2 / 64;
			float fade = (float) (0.45 + 0.55 * Math.sin(a * 3 + time * 0.08));
			if (fade <= 0) continue;
			double waveA = Math.sin(a * 5 + time * 0.1) * 0.09;
			double waveN = Math.sin(n * 5 + time * 0.1) * 0.09;
			vertex(buffer, matrices, new Vec3d(Math.cos(a) * radius, y + waveA, Math.sin(a) * radius), r, g, b, alpha * fade);
			vertex(buffer, matrices, new Vec3d(Math.cos(n) * radius, y + waveN, Math.sin(n) * radius), r, g, b, alpha * fade);
			vertex(buffer, matrices, new Vec3d(Math.cos(n) * (radius + 0.16), y + waveN + 0.12, Math.sin(n) * (radius + 0.16)), r, g, b, 0);
			vertex(buffer, matrices, new Vec3d(Math.cos(a) * (radius + 0.16), y + waveA + 0.12, Math.sin(a) * (radius + 0.16)), r, g, b, 0);
		}
	}

	/** Radial vertex alpha gives a soft halo without a texture, bloom shader or through-wall outline. */
	private static void disc(MatrixStack matrices, VertexConsumer buffer, Vec3d center, float radius,
							float alpha, float r, float g, float b, boolean billboard) {
		matrices.push();
		matrices.translate(center.x, center.y, center.z);
		if (billboard) matrices.multiply(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation());
		int bands = radius < 0.3F ? 1 : 3;
		int segments = radius < 0.3F ? 8 : 16;
		for (int band = 0; band < bands; band++) {
			float inner = band / (float) bands, outer = (band + 1) / (float) bands;
			float ai = alpha * (1 - inner) * (1 - inner);
			float ao = alpha * (1 - outer) * (1 - outer);
			for (int i = 0; i < segments; i++) {
				double a = i * Math.PI * 2 / segments, n = (i + 1) * Math.PI * 2 / segments;
				vertex(buffer, matrices, radial(a, radius * inner, billboard), r, g, b, ai);
				vertex(buffer, matrices, radial(n, radius * inner, billboard), r, g, b, ai);
				vertex(buffer, matrices, radial(n, radius * outer, billboard), r, g, b, ao);
				vertex(buffer, matrices, radial(a, radius * outer, billboard), r, g, b, ao);
			}
		}
		matrices.pop();
	}

	private static Vec3d radial(double angle, double radius, boolean billboard) {
		return billboard ? new Vec3d(Math.cos(angle) * radius, Math.sin(angle) * radius, 0)
			: new Vec3d(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
	}

	private static void vertex(VertexConsumer buffer, MatrixStack matrices, Vec3d p, float r, float g, float b, float alpha) {
		buffer.vertex(matrices.peek().getPositionMatrix(), (float) p.x, (float) p.y, (float) p.z)
			.color(r, g, b, alpha).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(FULL_BRIGHT).normal(0, 1, 0);
	}

	static final class State {
		private int tier, count;
		private boolean haunting;
		private long lastTick, awakened, added;
		State(HauntedTreeBlockEntity tree, long time) {
			tier = HauntRules.tier(tree.hungCount());
			count = tree.hungCount();
			haunting = tree.haunting();
			lastTick = time;
			awakened = time - TRANSITION_TICKS;
			added = time - TRANSITION_TICKS;
		}
		double swing(double time, int slot) {
			double intensity = haunting ? 0.23 : 0.10 + tier * 0.025;
			double pulse = Math.max(0, 1 - (time - added) / 24.0);
			return Math.sin((time + slot * 11) * 0.05) * intensity
				+ Math.sin((time - added) * 0.5 + slot) * pulse * 0.10;
		}
	}
}
