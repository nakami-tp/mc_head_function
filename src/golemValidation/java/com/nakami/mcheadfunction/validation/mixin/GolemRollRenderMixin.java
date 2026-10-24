package com.nakami.mcheadfunction.validation.mixin;

import com.nakami.mcheadfunction.client.HeadProjectileRenderer;
import com.nakami.mcheadfunction.entity.ThrownHeadEntity;
import com.nakami.mcheadfunction.validation.GolemClientValidation;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Check the actual item-render transform, removing the incoming camera transform. */
@Mixin(HeadProjectileRenderer.class)
public abstract class GolemRollRenderMixin {
	@Unique private Matrix4f before;
	@Unique private float delta;
	@Unique private Quaternionf previous;
	@Unique private net.minecraft.util.math.Vec3d previousPosition;
	@Unique private int previousId = -1;
	@Unique private float previousDistance;

	@Inject(method = "render(Lcom/nakami/mcheadfunction/entity/ThrownHeadEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"), remap = false)
	private void start(ThrownHeadEntity head, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
		this.before = new Matrix4f(matrices.peek().getPositionMatrix()).invert();
		this.delta = delta;
	}

	@Redirect(method = "render(Lcom/nakami/mcheadfunction/entity/ThrownHeadEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/ItemRenderer;renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;IILnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;I)V"))
	private void checkRoll(ItemRenderer renderer, ItemStack stack, ModelTransformationMode mode, int light, int overlay,
		MatrixStack matrices, VertexConsumerProvider vertices, World world, int seed) {
		if (world.getEntityById(seed) instanceof ThrownHeadEntity head && head.isGiantRoller() && head.getRollDistance(delta) > 1) {
			Quaternionf rotation = before.mul(matrices.peek().getPositionMatrix()).getUnnormalizedRotation(new Quaternionf()).normalize();
			var position = head.getLerpedPos(delta);
			float distance = head.getRollDistance(delta);
			if (seed == previousId && distance - previousDistance > 0.04F && position.distanceTo(previousPosition) > 0.02) {
				var movement = position.subtract(previousPosition).multiply(1, 0, 1).normalize();
				// For forward rolling, the instantaneous motion of the top points along travel.
				Vector3f top = new Quaternionf(rotation).mul(new Quaternionf(previous).conjugate()).transform(new Vector3f(0, 1, 0));
				Vector3f forward = new Vector3f(top.x, 0, top.z).normalize();
				float alignment = forward.dot((float) movement.x, 0, (float) movement.z);
				GolemClientValidation.recordRollAlignment(alignment);
				previous = rotation; previousPosition = position; previousDistance = distance;
			} else if (seed != previousId) {
				previousId = seed; previous = rotation; previousPosition = position; previousDistance = distance;
			}
		}
		renderer.renderItem(stack, mode, light, overlay, matrices, vertices, world, seed);
	}
}
