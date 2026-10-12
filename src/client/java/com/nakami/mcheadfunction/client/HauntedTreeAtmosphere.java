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
import net.minecraft.util.math.Vec3d;

/** Vanilla sprites and sparse, hard-edged embers; no bloom, ribbons or circular geometry. */
final class HauntedTreeAtmosphere {
	private static final RenderLayer FLAME = RenderLayer.getEntityTranslucentEmissive(
		Identifier.ofVanilla("textures/particle/soul_fire_flame.png"));
	private static final RenderLayer EMBER = RenderLayer.getEntityTranslucentEmissive(
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
		if (time % 3 == 0) {
			double x = world.random.nextDouble() * 5 - 2.5;
			double z = world.random.nextDouble() * 5 - 2.5;
			world.addParticle(ParticleTypes.ASH, pos.getX() + x,
				pos.getY() + 1 + world.random.nextDouble() * 7, pos.getZ() + z, 0, -0.015, 0);
			world.addParticle(ParticleTypes.SMOKE, pos.getX() + x,
				pos.getY() + 0.2, pos.getZ() + z, 0, 0.025, 0);
		}
		// Keep the original SOUL distribution and its approximate density at 60 FPS.
		// Births are tick-paced, so high frame rates cannot flood the canopy.
		int tier = state.haunting ? Math.max(3, state.tier) : state.tier;
		double budget = 0.36 * tier;
		int births = (int) budget + (world.random.nextDouble() < budget % 1 ? 1 : 0);
		for (int i = 0; i < births; i++) {
			world.addParticle(tier >= 2 ? ParticleTypes.SOUL : ParticleTypes.SMOKE,
				pos.getX() + 0.5 + (world.random.nextDouble() - 0.5) * 3,
				pos.getY() + 2 + world.random.nextDouble() * 6,
				pos.getZ() + 0.5 + (world.random.nextDouble() - 0.5) * 3, 0, 0.01, 0);
		}
		long elapsed = time - state.awakened;
		if (elapsed >= 0 && elapsed < TRANSITION_TICKS && elapsed % 3 == 0) {
			for (int i = 0; i < 4; i++) {
				world.addParticle(tier >= 2 ? ParticleTypes.SOUL : ParticleTypes.LARGE_SMOKE,
					pos.getX() + world.random.nextDouble() * 3 - 1.5,
					pos.getY() + 0.3 + elapsed / 6.0,
					pos.getZ() + world.random.nextDouble() * 3 - 1.5, 0, 0.035, 0);
			}
		}
	}

	static void render(State state, float delta, MatrixStack matrices, VertexConsumerProvider vertices) {
		double elapsed = (double) state.lastTick + delta - state.awakened;
		if (elapsed < 0 || elapsed >= TRANSITION_TICKS || (state.tier < 2 && !state.haunting)) return;
		// Staggered short-lived flames climb the trunk, never joining into a ring or trail.
		var buffer = vertices.getBuffer(FLAME);
		for (int i = 0; i < 24; i++) {
			double age = elapsed - i * 1.8;
			if (age < 0 || age > 14) continue;
			double x = (((i * 7) % 11) - 5) * 0.28;
			double z = (((i * 5 + 3) % 13) - 6) * 0.24;
			double y = 0.35 + i * 0.38 + age * 0.025;
			float size = age < 9 ? 0.35F : 0.22F;
			sprite(matrices, buffer, new Vec3d(x, y, z), size, 1, 1, 1, 0.9F, true);
		}
	}

	static void headEmbers(State state, double time, int slot, Vec3d center,
			MatrixStack matrices, VertexConsumerProvider vertices) {
		if (state.tier < 3 && !state.haunting) return;
		// A tiny vanilla soul flame leaves the face unobscured. Stepped sizes mimic particles.
		int frame = (int) Math.floor(time / 4 + slot * 3);
		float size = 0.26F + Math.floorMod(frame, 3) * 0.035F;
		sprite(matrices, vertices.getBuffer(FLAME), center.add(0, 0.39, 0), size,
			1, 1, 1, 0.85F, true);
		var buffer = vertices.getBuffer(EMBER);
		for (int i = 0; i < 2; i++) {
			double phase = (time + slot * 13 + i * 23) % 46;
			if (phase > 30) continue;
			double x = i == 0 ? -0.31 : 0.32;
			double y = -0.10 + phase * 0.019;
			float pixel = phase < 20 ? 0.055F : 0.035F;
			sprite(matrices, buffer, center.add(x, y, (slot % 3 - 1) * 0.12), pixel,
				0.42F, 0.82F, 0.79F, 0.85F, false);
		}
	}

	/** One nearest-filtered, depth-tested quad, just like a vanilla particle. */
	private static void sprite(MatrixStack matrices, VertexConsumer buffer, Vec3d center,
			float size, float r, float g, float b, float alpha, boolean textured) {
		matrices.push();
		matrices.translate(center.x, center.y, center.z);
		matrices.multiply(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation());
		float uv = textured ? 1 : 0;
		vertex(buffer, matrices, -size / 2, -size / 2, 0, uv, r, g, b, alpha);
		vertex(buffer, matrices, size / 2, -size / 2, uv, uv, r, g, b, alpha);
		vertex(buffer, matrices, size / 2, size / 2, uv, 0, r, g, b, alpha);
		vertex(buffer, matrices, -size / 2, size / 2, 0, 0, r, g, b, alpha);
		matrices.pop();
	}

	private static void vertex(VertexConsumer buffer, MatrixStack matrices, float x, float y,
			float u, float v, float r, float g, float b, float alpha) {
		buffer.vertex(matrices.peek().getPositionMatrix(), x, y, 0)
			.color(r, g, b, alpha).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(FULL_BRIGHT).normal(0, 1, 0);
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
			double intensity = haunting ? 0.18 : 0.10 + tier * 0.015;
			double pulse = Math.max(0, 1 - (time - added) / 24.0);
			return Math.sin((time + slot * 11) * 0.05) * intensity
				+ Math.sin((time - added) * 0.5 + slot) * pulse * 0.07;
		}
	}
}
