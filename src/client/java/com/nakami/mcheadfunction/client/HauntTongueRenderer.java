package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.haunt.HauntTongueEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class HauntTongueRenderer extends EntityRenderer<HauntTongueEntity> {
	private static final Identifier SKIN = Identifier.ofVanilla("textures/block/white_concrete.png");

	public HauntTongueRenderer(EntityRendererFactory.Context context) { super(context); }

	@Override public boolean shouldRender(HauntTongueEntity e, net.minecraft.client.render.Frustum f, double x, double y, double z) { return true; }

	@Override
	public Identifier getTexture(HauntTongueEntity entity) { return SKIN; }

	@Override
	public void render(HauntTongueEntity entity, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
		ChestTeethRenderer.render(entity, delta, matrices, vertices, light);
		Vec3d tip = entity.getLerpedPos(delta);
		Vec3d start = entity.mouth().subtract(tip);
		Vec3d direction = start.negate().normalize();
		Vec3d side = direction.crossProduct(new Vec3d(0, 1, 0));
		if (side.lengthSquared() < 0.001) side = new Vec3d(1, 0, 0);
		side = side.normalize();
		Vec3d up = side.crossProduct(direction).normalize();
		var buffer = vertices.getBuffer(RenderLayer.getEntitySolid(SKIN));
		for (int i = 0; i < 16; i++) {
			double a = i / 16.0, b = (i + 1) / 16.0;
			Vec3d pa = start.multiply(1 - a), pb = start.multiply(1 - b);
			for (int face = 0; face < 6; face++) {
				double ang = face * Math.PI / 3, next = (face + 1) * Math.PI / 3;
				Vec3d ra = side.multiply(Math.cos(ang)).add(up.multiply(Math.sin(ang))).multiply(0.05);
				Vec3d rb = side.multiply(Math.cos(next)).add(up.multiply(Math.sin(next))).multiply(0.05);
				vert(buffer, matrices, pa.add(ra), light);
				vert(buffer, matrices, pb.add(ra), light);
				vert(buffer, matrices, pb.add(rb), light);
				vert(buffer, matrices, pa.add(rb), light);
			}
		}
	}

	private static void vert(VertexConsumer buffer, MatrixStack matrices, Vec3d p, int light) {
		buffer.vertex(matrices.peek().getPositionMatrix(), (float) p.x, (float) p.y, (float) p.z)
			.color(0.85F, 0.28F, 0.4F, 1).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
	}
}
