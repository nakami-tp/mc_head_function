package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.haunt.HauntTreeShape;
import com.nakami.mcheadfunction.haunt.HauntedTreeBlockEntity;
import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.rule.HauntRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public final class HauntedTreeRenderer implements BlockEntityRenderer<HauntedTreeBlockEntity> {
	private static final Identifier BARK = Identifier.ofVanilla("textures/block/dark_oak_log.png");
	private static final Identifier CHAIN = Identifier.ofVanilla("textures/block/chain.png");

	private final HauntedTreeAtmosphere atmosphere = new HauntedTreeAtmosphere();

	public HauntedTreeRenderer(BlockEntityRendererFactory.Context context) { }

	@Override
	public boolean rendersOutsideBoundingBox(HauntedTreeBlockEntity tree) { return true; }

	@Override
	public int getRenderDistance() { return 96; }

	@Override
	public void render(HauntedTreeBlockEntity tree, float delta, MatrixStack matrices, VertexConsumerProvider vertices, int light, int overlay) {
		var world = tree.getWorld();
		long time = world == null ? 0 : world.getTime();
		double frameTime = (double) time + delta;
		var bark = vertices.getBuffer(RenderLayer.getEntitySolid(BARK));
		for (var bone : HauntTreeShape.bones()) tube(matrices, bark, bone.from(), bone.to(), bone.radius(), light, 0.35F, 0.22F, 0.14F);
		int hung = tree.hungCount();
		var atmosphereState = atmosphere.update(tree, time);
		boolean glow = HauntRules.headsGlow(hung, tree.haunting());
		int headLight = glow ? 0xF000F0 : light;
		for (int i = 0; i < HauntRules.SLOTS; i++) {
			var type = tree.slot(i);
			if (type == null) continue;
			Vec3d hang = HauntTreeShape.hangOffset(i);
			double swing = atmosphereState.swing(frameTime, i);
			Vec3d branch = hang.add(0, 0.55, 0);
			tube(matrices, vertices.getBuffer(RenderLayer.getEntityCutout(CHAIN)), branch, hang.add(swing, 0, 0), 0.02F, light, 0.45F, 0.45F, 0.5F);
			matrices.push();
			matrices.translate(hang.x + swing, hang.y - 0.15, hang.z);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (i * 20 + Math.sin((time + delta) * 0.03) * 6)));
			matrices.scale(0.7F, 0.7F, 0.7F);
			MinecraftClient.getInstance().getItemRenderer().renderItem(new ItemStack(HeadItems.item(type)),
				ModelTransformationMode.FIXED, headLight, OverlayTexture.DEFAULT_UV, matrices, vertices, world, i);
			matrices.pop();
			HauntedTreeAtmosphere.headHalo(atmosphereState, frameTime, i, hang.add(swing, -0.15, 0), matrices, vertices);
		}
		HauntedTreeAtmosphere.render(atmosphereState, delta, matrices, vertices);
	}

	private static void tube(MatrixStack matrices, VertexConsumer buffer, Vec3d from, Vec3d to, float radius, int light, float r, float g, float b) {
		Vec3d dir = to.subtract(from);
		if (dir.lengthSquared() < 1.0E-6) return;
		dir = dir.normalize();
		Vec3d side = dir.crossProduct(new Vec3d(0, 1, 0));
		if (side.lengthSquared() < 0.001) side = new Vec3d(1, 0, 0);
		side = side.normalize();
		Vec3d up = side.crossProduct(dir).normalize();
		for (int face = 0; face < 6; face++) {
			double a = face * Math.PI / 3, n = (face + 1) * Math.PI / 3;
			Vec3d ra = side.multiply(Math.cos(a)).add(up.multiply(Math.sin(a))).multiply(radius);
			Vec3d rb = side.multiply(Math.cos(n)).add(up.multiply(Math.sin(n))).multiply(radius);
			vert(buffer, matrices, from.add(ra), r, g, b, light);
			vert(buffer, matrices, to.add(ra), r * 0.7F, g * 0.7F, b * 0.7F, light);
			vert(buffer, matrices, to.add(rb), r * 0.7F, g * 0.7F, b * 0.7F, light);
			vert(buffer, matrices, from.add(rb), r, g, b, light);
		}
	}

	private static void vert(VertexConsumer buffer, MatrixStack matrices, Vec3d p, float r, float g, float b, int light) {
		buffer.vertex(matrices.peek().getPositionMatrix(), (float) p.x, (float) p.y, (float) p.z)
			.color(r, g, b, 1).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
	}
}
