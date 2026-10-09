package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.haunt.GhostHeadEntity;
import net.minecraft.client.render.OverlayTexture;
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
	private final net.minecraft.client.render.item.ItemRenderer items;

	public GhostHeadRenderer(EntityRendererFactory.Context context) {
		super(context);
		items = context.getItemRenderer();
	}

	@Override
	public void render(GhostHeadEntity entity, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
		matrices.push();
		float bob = (float) Math.sin((entity.age + delta) * 0.15) * 0.08F;
		matrices.translate(0, 0.25 + bob, 0);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((entity.age + delta) * 4));
		matrices.scale(0.85F, 0.85F, 0.85F);
		items.renderItem(entity.stack(), ModelTransformationMode.FIXED, 0xF000F0, OverlayTexture.DEFAULT_UV,
			matrices, vertices, entity.getWorld(), entity.getId());
		matrices.pop();
		if (entity.getWorld().isClient() && entity.age % 2 == 0) {
			entity.getWorld().addParticle(ParticleTypes.SOUL, entity.getX(), entity.getY() + 0.2, entity.getZ(), 0, 0.01, 0);
		}
		super.render(entity, yaw, delta, matrices, vertices, light);
	}

	@Override
	public Identifier getTexture(GhostHeadEntity entity) { return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE; }
}
