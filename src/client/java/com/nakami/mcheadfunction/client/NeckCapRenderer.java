package com.nakami.mcheadfunction.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * Draws a small flesh-colored plate where the hidden head used to sit.
 */
public final class NeckCapRenderer {
	private static final Identifier TEXTURE = Identifier.of("minecraft", "textures/block/netherrack.png");

	private NeckCapRenderer() {
	}

	public static void render(LivingEntity entity, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		matrices.push();
		matrices.translate(0.0, entity.getStandingEyeHeight() - 0.12, 0.0);
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntitySolid(TEXTURE));
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		float s = 0.22F;
		float h = 0.05F;
		quad(buffer, matrix, -s, 0, -s, s, 0, -s, s, 0, s, -s, 0, s, light);
		quad(buffer, matrix, -s, -h, -s, -s, -h, s, s, -h, s, s, -h, -s, light);
		matrices.pop();
	}

	private static void quad(VertexConsumer buffer, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, int light) {
		vertex(buffer, matrix, x1, y1, z1, 0, 0, light);
		vertex(buffer, matrix, x2, y2, z2, 1, 0, light);
		vertex(buffer, matrix, x3, y3, z3, 1, 1, light);
		vertex(buffer, matrix, x4, y4, z4, 0, 1, light);
	}

	private static void vertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z, float u, float v, int light) {
		buffer.vertex(matrix, x, y, z).color(1.0F, 1.0F, 1.0F, 1.0F).texture(u, v).overlay(0).light(light).normal(0, 1, 0);
	}
}
