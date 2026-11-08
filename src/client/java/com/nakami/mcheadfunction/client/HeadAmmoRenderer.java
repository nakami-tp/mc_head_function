package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.entity.HeadAmmoEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.model.SkullEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public final class HeadAmmoRenderer extends EntityRenderer<HeadAmmoEntity> {
	private final SkullEntityModel model = new SkullEntityModel(SkullEntityModel.getSkullTexturedModelData().createModel());
	private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/creeper/creeper.png");
	public HeadAmmoRenderer(EntityRendererFactory.Context context) {
		super(context);
		shadowRadius = 0.18F;
	}
	@Override public void render(HeadAmmoEntity entity, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
		float time = entity.age + delta;
		matrices.push();
		matrices.translate(0, entity.getHeight() / 2, 0);
		if (entity.isPlanted()) {
			// Local +Y points away from the contacted surface; keep the base fixed.
			matrices.multiply(entity.attachedFace().getRotationQuaternion());
			CreeperAmmoEffects.attachment(matrices, vertices.getBuffer(CreeperAmmoEffects.GLOW), entity, delta);
			matrices.translate(0, -0.025, 0);
			matrices.scale(0.8F, 0.4F, 0.8F);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
		} else {
			matrices.scale(0.7F, 0.7F, 0.7F);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 5));
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(time * 18));
		}
		// Center the skull explicitly so its luminous rim follows the face on every surface.
		matrices.translate(0, -0.25, 0);
		matrices.scale(1, -1, -1);
		model.setHeadRotation(0, 0, 0);
		model.render(matrices, vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light, OverlayTexture.DEFAULT_UV, -1);
		matrices.pop();
		super.render(entity, yaw, delta, matrices, vertices, light);
	}
	@Override public Identifier getTexture(HeadAmmoEntity entity) { return TEXTURE; }
}
