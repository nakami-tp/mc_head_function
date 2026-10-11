package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.haunt.HauntTongueEntity;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.model.*;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/** Temporary fangs on the lower rim and moving lid of a feeding chest. */
public final class ChestTeethRenderer {
	private static final Identifier TOOTH = Identifier.ofVanilla("textures/block/white_concrete.png");
	private static final ModelPart LOWER = teeth(false), UPPER = teeth(true);
	private ChestTeethRenderer() { }
	private static ModelPart teeth(boolean upper) {
		var data = new ModelData();
		var builder = ModelPartBuilder.create().uv(0, 0);
		for (int i = 0; i < 6; i++) {
			float height = i == 0 || i == 5 ? 3 : 2;
			builder.cuboid(2 + i * 2, upper ? -height : 10, upper ? 12.5F : 13.5F, 1.25F, height, 1.25F);
		}
		data.getRoot().addChild("teeth", builder, upper ? ModelTransform.pivot(0, 9, 1) : ModelTransform.NONE);
		return TexturedModelData.of(data, 16, 16).createModel().getChild("teeth");
	}
	public static void render(HauntTongueEntity tongue, float delta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
		var state = tongue.getWorld().getBlockState(tongue.chest());
		if (!(state.getBlock() instanceof ChestBlock)) return;
		Vec3d offset = Vec3d.of(tongue.chest()).subtract(tongue.getLerpedPos(delta));
		matrices.push();
		matrices.translate(offset.x + 0.5, offset.y + 0.5, offset.z + 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-state.get(ChestBlock.FACING).asRotation()));
		matrices.translate(-0.5, -0.5, -0.5);
		float closed = 1 - tongue.lidProgress(delta);
		UPPER.pitch = -(1 - closed * closed * closed) * (float) Math.PI / 2;
		var buffer = vertices.getBuffer(RenderLayer.getEntitySolid(TOOTH));
		LOWER.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV);
		UPPER.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV);
		matrices.pop();
	}
}
