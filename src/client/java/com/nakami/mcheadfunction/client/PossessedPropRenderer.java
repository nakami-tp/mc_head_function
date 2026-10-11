package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.haunt.PossessedPropEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public final class PossessedPropRenderer extends EntityRenderer<PossessedPropEntity> {
	public PossessedPropRenderer(EntityRendererFactory.Context context) { super(context); }

	@Override
	public void render(PossessedPropEntity entity, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
		matrices.push();
		var state = entity.block();
		if (!(state.getBlock() instanceof net.minecraft.block.LanternBlock)) {
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) Math.sin((entity.age + delta) * 0.12) * 8));
		}
		if (state.getRenderType() == net.minecraft.block.BlockRenderType.ENTITYBLOCK_ANIMATED) {
			MinecraftClient.getInstance().getItemRenderer().renderItem(state.getBlock().asItem().getDefaultStack(),
				ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV, matrices, vertices, entity.getWorld(), entity.getId());
		} else {
			matrices.translate(-0.5, 0, -0.5);
			MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(state, matrices, vertices, light, OverlayTexture.DEFAULT_UV);
		}
		matrices.pop();
		super.render(entity, yaw, delta, matrices, vertices, light);
	}

	@Override
	public Identifier getTexture(PossessedPropEntity entity) { return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE; }
}
