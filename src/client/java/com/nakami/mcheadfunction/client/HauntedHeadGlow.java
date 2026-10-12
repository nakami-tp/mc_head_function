package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.head.HeadType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/** Emissive skin and a thin inverted hull follow the actual head, including ears and horns. */
final class HauntedHeadGlow extends RenderLayer {
	private static final RenderLayer EDGE = of("haunted_head_edge",
		VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 1536,
		false, true, MultiPhaseParameters.builder()
			.program(ENTITY_TRANSLUCENT_EMISSIVE_PROGRAM)
			.texture(new Texture(Identifier.ofVanilla("textures/block/white_concrete.png"), false, false))
			.transparency(TRANSLUCENT_TRANSPARENCY).cull(ENABLE_CULLING)
			.writeMaskState(COLOR_MASK).overlay(ENABLE_OVERLAY_COLOR).build(false));

	private HauntedHeadGlow() {
		super("haunted_head_glow", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
			VertexFormat.DrawMode.QUADS, 1536, false, true, () -> {}, () -> {});
	}

	static void render(ItemStack stack, HeadType type, int slot, double time, World world,
			MatrixStack matrices, VertexConsumerProvider vertices) {
		var items = MinecraftClient.getInstance().getItemRenderer();
		// Vanilla skulls use entity textures; the custom baked heads use the item atlas.
		Identifier texture = switch (type) {
			case ZOMBIE -> Identifier.ofVanilla("textures/entity/zombie/zombie.png");
			case CREEPER -> Identifier.ofVanilla("textures/entity/creeper/creeper.png");
			default -> SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
		};
		var skin = vertices.getBuffer(RenderLayer.getEntityTranslucentEmissive(texture));
		items.renderItem(stack, ModelTransformationMode.FIXED, 0xF000F0, OverlayTexture.DEFAULT_UV,
			matrices, ignored -> skin, world, slot);

		float pulse = (float) (0.5 + 0.5 * Math.sin(time * 0.08 + slot * 1.3));
		matrices.push();
		float scale = 1.10F + pulse * 0.035F;
		matrices.scale(scale, scale, scale);
		var hull = new InvertedHull(vertices.getBuffer(EDGE), 0.60F + pulse * 0.25F);
		items.renderItem(stack, ModelTransformationMode.FIXED, 0xF000F0, OverlayTexture.DEFAULT_UV,
			matrices, ignored -> hull, world, slot);
		matrices.pop();
	}

	/** Reverse each mesh quad; culling and the opaque head depth leave only its outer edge. */
	private static final class InvertedHull implements VertexConsumer {
		private final VertexConsumer target;
		private final float alpha;
		private final float[] positions = new float[12];
		private int count;

		InvertedHull(VertexConsumer target, float alpha) { this.target = target; this.alpha = alpha; }

		@Override public VertexConsumer vertex(float x, float y, float z) {
			int offset = count++ * 3;
			positions[offset] = x; positions[offset + 1] = y; positions[offset + 2] = z;
			if (count == 4) {
				for (int i = 3; i >= 0; i--) {
					target.vertex(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2])
						.color(0.48F, 0.95F, 0.89F, alpha).texture(0, 0)
						.overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 1, 0);
				}
				count = 0;
			}
			return this;
		}
		@Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
		@Override public VertexConsumer texture(float u, float v) { return this; }
		@Override public VertexConsumer overlay(int u, int v) { return this; }
		@Override public VertexConsumer light(int u, int v) { return this; }
		@Override public VertexConsumer normal(float x, float y, float z) { return this; }
	}
}
