package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.entity.FrogTongueEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

/** Solid segmented tube: visible even with particles disabled, from either perspective. */
public final class FrogTongueRenderer extends EntityRenderer<FrogTongueEntity> {
	private static final Identifier SKIN = Identifier.ofVanilla("textures/block/white_concrete.png");
	public FrogTongueRenderer(EntityRendererFactory.Context context) { super(context); }
	@Override public boolean shouldRender(FrogTongueEntity e, Frustum f, double x, double y, double z) { return true; }
	@Override public Identifier getTexture(FrogTongueEntity e) { return SKIN; }
	@Override public void render(FrogTongueEntity e, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
		var owner = e.owner();
		if (owner == null) return;
		// The tip can sit inside an opaque target. Sample the exposed mouth, not that block's interior.
		light = WorldRenderer.getLightmapCoordinates(e.getWorld(), BlockPos.ofFloored(FrogTongueEntity.mouth(owner, delta)));
		Vec3d tip = e.getLerpedPos(delta);
		Vec3d start = FrogTongueEntity.mouth(owner, delta).subtract(tip);
		Vec3d direction = start.negate().normalize();
		Vec3d side = direction.crossProduct(new Vec3d(0, 1, 0));
		if (side.lengthSquared() < 0.001) side = new Vec3d(1, 0, 0);
		side = side.normalize();
		Vec3d up = side.crossProduct(direction).normalize();
		var buffer = vertices.getBuffer(RenderLayer.getEntitySolid(SKIN));
		double time = e.elapsed() + delta;
		for (int i = 0; i < 24; i++) {
			double a = i / 24.0, b = (i + 1) / 24.0;
			Vec3d pa = curve(start, a, time), pb = curve(start, b, time);
			for (int face = 0; face < 8; face++) {
				double angle = face * Math.PI / 4, next = (face + 1) * Math.PI / 4;
				Vec3d ra = side.multiply(Math.cos(angle)).add(up.multiply(Math.sin(angle)));
				Vec3d rb = side.multiply(Math.cos(next)).add(up.multiply(Math.sin(next)));
				float shade = 0.78F + 0.22F * (float) Math.sin(angle);
				vertex(buffer, matrices, pa.add(ra.multiply(radius(a))), shade, 0, 0, light);
				vertex(buffer, matrices, pb.add(ra.multiply(radius(b))), shade, 0, 1, light);
				vertex(buffer, matrices, pb.add(rb.multiply(radius(b))), shade, 1, 1, light);
				vertex(buffer, matrices, pa.add(rb.multiply(radius(a))), shade, 1, 0, light);
			}
		}
		matrices.push();
		if (!e.carriedBlock().isAir()) {
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) Math.sin(time * 0.6) * 12));
			matrices.scale(0.7F, 0.7F, 0.7F);
			if (e.carriedBlock().getRenderType() == net.minecraft.block.BlockRenderType.ENTITYBLOCK_ANIMATED) {
				MinecraftClient.getInstance().getItemRenderer().renderItem(e.carriedBlock().getBlock().asItem().getDefaultStack(),
					ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV, matrices, vertices, e.getWorld(), e.getId());
			} else {
				matrices.translate(-0.5, -0.5, -0.5);
				MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(e.carriedBlock(), matrices, vertices, light, OverlayTexture.DEFAULT_UV);
			}
		} else if (!e.carriedItem().isEmpty()) {
			MinecraftClient.getInstance().getItemRenderer().renderItem(e.carriedItem(), ModelTransformationMode.GROUND, light,
				OverlayTexture.DEFAULT_UV, matrices, vertices, e.getWorld(), e.getId());
		}
		matrices.pop();
	}
	private static double radius(double t) { return 0.055 + 0.055 * Math.pow(t, 8); }
	private static Vec3d curve(Vec3d start, double t, double time) {
		double slack = time <= FrogTongueEntity.EXTEND + FrogTongueEntity.HOLD ? 0.13 : 0.035;
		return start.multiply(1 - t).add(Math.sin(t * Math.PI * 3 - time * 0.65) * Math.sin(t * Math.PI) * slack,
			-Math.sin(t * Math.PI) * Math.min(0.35, start.length() * 0.025), 0);
	}
	private static void vertex(VertexConsumer buffer, MatrixStack matrices, Vec3d p, float shade, float u, float v, int light) {
		buffer.vertex(matrices.peek().getPositionMatrix(), (float) p.x, (float) p.y, (float) p.z)
			.color(shade, shade * 0.32F, shade * 0.47F, 1).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
	}
}
