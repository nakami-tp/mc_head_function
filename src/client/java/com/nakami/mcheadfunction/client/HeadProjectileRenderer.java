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
		boolean giant = entity.isGiantRoller();
		float roll = -entity.getRollDistance(delta) / (ThrownHeadEntity.GOLEM_SIZE / 2);
		// Lowest rotated vertex of the head cuboid and its protruding nose.
		double sin = Math.sin(roll), cos = Math.cos(roll);
		double bodyBottom = -2.5 * Math.abs(cos) + Math.min(1.5 * sin, -2.5 * sin);
		double noseBottom = Math.min(-2.5 * cos, -0.5 * cos) + Math.min(2.5 * sin, 1.5 * sin);
		double support = -Math.min(bodyBottom, noseBottom);
		matrices.translate(0, (giant ? support : 0.36) + (active && type == HeadType.ENDERMAN ? Math.sin(time * 0.3) * 0.08 : 0), 0);
		var velocity = entity.getVelocity();
		float facing = active && (type == HeadType.ZOMBIE || giant) ? -entity.getYaw(delta)
			: (float) Math.toDegrees(Math.atan2(velocity.x, velocity.z));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 + facing));
		float pitch = active ? switch (type) {
			case IRON_GOLEM -> (float) Math.toDegrees(roll);
			case ZOMBIE -> entity.getBiteTicks() > 0
				? (float) Math.sin(Math.max(0, entity.getBiteTicks() - delta) / 6.0 * Math.PI) * 22
				: (float) Math.max(-12, Math.min(12, -velocity.y * 45));
			case ENDERMAN -> (float) Math.sin(time * 0.5) * 10;
			default -> 0;
		} : time * 16;
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
		if (giant) matrices.scale(ThrownHeadEntity.GOLEM_SCALE, ThrownHeadEntity.GOLEM_SCALE, ThrownHeadEntity.GOLEM_SCALE);
		items.renderItem(entity.getStack(), ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV,
			matrices, vertices, entity.getWorld(), entity.getId());
		matrices.pop();
		super.render(entity, yaw, delta, matrices, vertices, light);
	}
	@Override public Identifier getTexture(ThrownHeadEntity entity) { return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE; }
}
