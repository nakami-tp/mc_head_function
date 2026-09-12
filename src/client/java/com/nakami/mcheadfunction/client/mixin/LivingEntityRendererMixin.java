package com.nakami.mcheadfunction.client.mixin;

import com.nakami.mcheadfunction.client.NeckCapRenderer;
import com.nakami.mcheadfunction.head.HeadlessAccess;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {
	@Shadow
	protected M model;

	@Inject(method = "render", at = @At("HEAD"))
	private void mhf$hideHead(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
		setHeadVisible(entity, !HeadlessAccess.isHeadless(entity));
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void mhf$restoreHead(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
		if (HeadlessAccess.isHeadless(entity)) {
			NeckCapRenderer.render(entity, matrices, consumers, light);
		}
		setHeadVisible(entity, true);
	}

	private void setHeadVisible(T entity, boolean visible) {
		if (model instanceof BipedEntityModel<?> biped) {
			biped.head.visible = visible;
			biped.hat.visible = visible;
		}
		if (model instanceof AnimalModelAccessor animal) {
			animal.mhf$getHeadParts().forEach(part -> part.visible = visible);
		}
		if (model instanceof SinglePartEntityModel<?> single) {
			hideNamedHeads(single.getPart(), visible);
		}
	}

	private static void hideNamedHeads(ModelPart root, boolean visible) {
		for (Map.Entry<String, ModelPart> entry : ((ModelPartAccessor) (Object) root).mhf$children().entrySet()) {
			String name = entry.getKey().toLowerCase(Locale.ROOT);
			ModelPart child = entry.getValue();
			if (isHeadName(name)) {
				child.visible = visible;
			}
			hideNamedHeads(child, visible);
		}
	}

	private static boolean isHeadName(String name) {
		return name.equals("head")
			|| name.equals("hat")
			|| name.equals("skull")
			|| name.equals("headwear")
			|| name.contains("head");
	}
}
