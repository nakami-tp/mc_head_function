package com.nakami.mcheadfunction.validation.mixin;

import com.nakami.mcheadfunction.validation.BeheadingClientValidation;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.EndermanEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the real model after animation, immediately before rendering and features. */
@Mixin(LivingEntityRenderer.class)
public abstract class BeheadingRenderProbe {
    @Shadow protected EntityModel<?> model;
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;getRenderLayer(Lnet/minecraft/entity/LivingEntity;ZZZ)Lnet/minecraft/client/render/RenderLayer;"))
    private void checkHead(LivingEntity entity, float yaw, float delta, MatrixStack matrices,
            VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (entity instanceof net.minecraft.entity.player.PlayerEntity && model instanceof BipedEntityModel<?> playerModel) {
            BeheadingClientValidation.observePlayer(playerModel.rightArm.pitch, entity.getHandSwingProgress(delta));
        }
        if (entity instanceof EndermanEntity && model instanceof BipedEntityModel<?> biped) {
            BeheadingClientValidation.observe(entity, biped.head.visible, biped.hat.visible, biped.head.roll);
        }
    }
}
