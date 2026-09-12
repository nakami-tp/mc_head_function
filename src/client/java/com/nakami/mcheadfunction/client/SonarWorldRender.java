package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.rule.HeadRules;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;

public final class SonarWorldRender {
	private SonarWorldRender() {
	}

	public static void afterEntities(WorldRenderContext context) {
		if (McHeadFunctionClient.sonarTicks <= 0) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || context.world() == null) {
			return;
		}
		Vec3d camera = context.camera().getPos();
		MatrixStack matrices = context.matrixStack();
		VertexConsumerProvider consumers = context.consumers();
		if (matrices == null || consumers == null) {
			return;
		}
		VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
		World world = context.world();
		BlockPos center = client.player.getBlockPos();
		int range = HeadRules.SONAR_RANGE;
		for (BlockPos pos : BlockPos.iterate(center.add(-range, -range, -range), center.add(range, range, range))) {
			if (!world.getBlockState(pos).isOpaqueFullCube(world, pos)) {
				continue;
			}
			boolean caveFace = false;
			for (var direction : net.minecraft.util.math.Direction.values()) {
				BlockPos neighbor = pos.offset(direction);
				if (world.getBlockState(neighbor).isAir() && neighbor.getY() < world.getSeaLevel()) {
					caveFace = true;
					break;
				}
			}
			if (!caveFace) {
				continue;
			}
			drawBox(matrices, lines, Box.from(Vec3d.of(pos)).offset(-camera.x, -camera.y, -camera.z), 0.4F, 0.85F, 1.0F, 0.35F);
		}
		for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class, client.player.getBoundingBox().expand(range), LivingEntity::isAlive)) {
			if (living == client.player) {
				continue;
			}
			drawBox(matrices, lines, living.getBoundingBox().offset(-camera.x, -camera.y, -camera.z), 1.0F, 0.85F, 0.2F, 0.7F);
		}
	}

	private static void drawBox(MatrixStack matrices, VertexConsumer lines, Box box, float r, float g, float b, float a) {
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		float x1 = (float) box.minX;
		float y1 = (float) box.minY;
		float z1 = (float) box.minZ;
		float x2 = (float) box.maxX;
		float y2 = (float) box.maxY;
		float z2 = (float) box.maxZ;
		line(matrix, lines, x1, y1, z1, x2, y1, z1, r, g, b, a);
		line(matrix, lines, x2, y1, z1, x2, y1, z2, r, g, b, a);
		line(matrix, lines, x2, y1, z2, x1, y1, z2, r, g, b, a);
		line(matrix, lines, x1, y1, z2, x1, y1, z1, r, g, b, a);
		line(matrix, lines, x1, y2, z1, x2, y2, z1, r, g, b, a);
		line(matrix, lines, x2, y2, z1, x2, y2, z2, r, g, b, a);
		line(matrix, lines, x2, y2, z2, x1, y2, z2, r, g, b, a);
		line(matrix, lines, x1, y2, z2, x1, y2, z1, r, g, b, a);
		line(matrix, lines, x1, y1, z1, x1, y2, z1, r, g, b, a);
		line(matrix, lines, x2, y1, z1, x2, y2, z1, r, g, b, a);
		line(matrix, lines, x2, y1, z2, x2, y2, z2, r, g, b, a);
		line(matrix, lines, x1, y1, z2, x1, y2, z2, r, g, b, a);
	}

	private static void line(Matrix4f matrix, VertexConsumer lines, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
		lines.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(0, 1, 0);
		lines.vertex(matrix, x2, y2, z2).color(r, g, b, a).normal(0, 1, 0);
	}
}
