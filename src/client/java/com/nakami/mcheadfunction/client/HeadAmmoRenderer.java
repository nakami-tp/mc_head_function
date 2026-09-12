package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.entity.HeadAmmoEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public final class HeadAmmoRenderer extends EntityRenderer<HeadAmmoEntity> {
	private final ItemRenderer items;
	public HeadAmmoRenderer(EntityRendererFactory.Context context) {
		super(context);
		items = context.getItemRenderer();
		shadowRadius = 0.18F;
	}
	@Override public void render(HeadAmmoEntity entity, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
		float time = entity.age + delta;
		matrices.push();
		matrices.translate(0, 0.2, 0);
		float pulse = 0.7F + 0.055F * (float) Math.sin(time * 0.7);
		matrices.scale(pulse, pulse, pulse);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 5));
		if (!entity.hasNoGravity()) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(time * 18));
		items.renderItem(entity.getStack(), ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV,
			matrices, vertices, entity.getWorld(), entity.getId());
		matrices.pop();
		super.render(entity, yaw, delta, matrices, vertices, light);
	}
	@Override public Identifier getTexture(HeadAmmoEntity entity) { return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE; }
}
