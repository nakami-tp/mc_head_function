package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.entity.ThrownHeadEntity;
import com.nakami.mcheadfunction.head.HeadType;
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

/** World-oriented tumbling heads, with rolling, hopping and hovering active poses. */
public final class HeadProjectileRenderer extends EntityRenderer<ThrownHeadEntity> {
	private final ItemRenderer items;
	public HeadProjectileRenderer(EntityRendererFactory.Context context) {
		super(context);
		items = context.getItemRenderer();
		shadowRadius = 0.25F;
	}
	@Override public void render(ThrownHeadEntity entity, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
		matrices.push();
		float time = entity.age + delta;
		boolean active = entity.isActive();
		HeadType type = entity.getHeadType();
		matrices.translate(0, 0.25 + (active && type == HeadType.ENDERMAN ? Math.sin(time * 0.3) * 0.08 : 0), 0);
		var velocity = entity.getVelocity();
		float facing = (float) Math.toDegrees(Math.atan2(velocity.x, velocity.z));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 + facing));
		float pitch = active ? switch (type) {
			case IRON_GOLEM -> time * 24;
			case ZOMBIE -> (float) Math.sin(time * 0.6) * 18;
			case ENDERMAN -> (float) Math.sin(time * 0.5) * 10;
			default -> 0;
		} : time * 16;
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
		items.renderItem(entity.getStack(), ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV,
			matrices, vertices, entity.getWorld(), entity.getId());
		matrices.pop();
		super.render(entity, yaw, delta, matrices, vertices, light);
	}
	@Override public Identifier getTexture(ThrownHeadEntity entity) { return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE; }
}
