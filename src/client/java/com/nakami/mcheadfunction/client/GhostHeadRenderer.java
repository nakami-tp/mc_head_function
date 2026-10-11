package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.haunt.GhostHeadEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public final class GhostHeadRenderer extends EntityRenderer<GhostHeadEntity> {
	private static final RenderLayer GLOW = RenderLayer.getEntityTranslucentEmissive(
		Identifier.ofVanilla("textures/block/white_concrete.png"));
	private static final int FULL_BRIGHT = 0xF000F0;
	private final net.minecraft.client.render.item.ItemRenderer items;

	public GhostHeadRenderer(EntityRendererFactory.Context context) {
		super(context);
		items = context.getItemRenderer();
	}

	@Override
	public void render(GhostHeadEntity entity, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
		if (!entity.visibleSplit()) return;
		float scale = entity.displayScale(delta);
		if (scale < 0.04F) return;
		matrices.push();
		float bob = entity.phase() == GhostHeadEntity.ENTER ? 0
			: (float) Math.sin((entity.age + delta) * 0.08) * 0.06F;
		matrices.translate(0, 0.22 + bob, 0);
		halo(matrices, vertices, 0.55F + scale * 0.25F, 0.22F + (1 - scale) * 0.35F);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((entity.age + delta) * 1.4F));
		matrices.scale(0.82F * scale, 0.82F * scale, 0.82F * scale);
		items.renderItem(entity.stack(), ModelTransformationMode.FIXED, FULL_BRIGHT, OverlayTexture.DEFAULT_UV,
			matrices, vertices, entity.getWorld(), entity.getId());
		matrices.pop();
		if (entity.getWorld().isClient() && entity.age % 4 == 0) {
			entity.getWorld().addParticle(ParticleTypes.SOUL, entity.getX(), entity.getY() + 0.15, entity.getZ(), 0, 0.008, 0);
		}
		super.render(entity, yaw, delta, matrices, vertices, light);
	}

	private static void halo(MatrixStack matrices, VertexConsumerProvider vertices, float radius, float alpha) {
		var buffer = vertices.getBuffer(GLOW);
		matrices.push();
		matrices.multiply(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation());
		int segments = 12;
		for (int i = 0; i < segments; i++) {
			double a = i * Math.PI * 2 / segments, n = (i + 1) * Math.PI * 2 / segments;
			vertex(buffer, matrices, 0, 0, 0.18F, 0.95F, 0.78F, alpha);
			vertex(buffer, matrices, Math.cos(a) * radius, Math.sin(a) * radius, 0.18F, 0.95F, 0.78F, 0);
			vertex(buffer, matrices, Math.cos(n) * radius, Math.sin(n) * radius, 0.18F, 0.95F, 0.78F, 0);
			vertex(buffer, matrices, 0, 0, 0.18F, 0.95F, 0.78F, alpha);
		}
		matrices.pop();
	}

	private static void vertex(VertexConsumer buffer, MatrixStack matrices, double x, double y, float r, float g, float b, float a) {
		buffer.vertex(matrices.peek().getPositionMatrix(), (float) x, (float) y, 0)
			.color(r, g, b, a).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(FULL_BRIGHT).normal(0, 1, 0);
	}

	@Override
	public Identifier getTexture(GhostHeadEntity entity) { return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE; }
}
