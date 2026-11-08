package com.nakami.mcheadfunction.validation.mixin;

import com.nakami.mcheadfunction.validation.FearClientValidation;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EndermanEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class FearRenderProbe {
    @Inject(method="render", at=@At(value="INVOKE", target="Lnet/minecraft/client/render/entity/LivingEntityRenderer;getRenderLayer(Lnet/minecraft/entity/LivingEntity;ZZZ)Lnet/minecraft/client/render/RenderLayer;"))
    private void observe(LivingEntity entity,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider vertices,int light,CallbackInfo ci) {
        FearClientValidation.observeMob(entity, matrices.peek().getPositionMatrix().m30());
        var renderer = (LivingEntityRenderer<?, ?>) (Object) this;
        if (renderer.getModel() instanceof EndermanEntityModel<?> model) {
            FearClientValidation.observeEnderman(entity, model.head.pivotY);
        }
    }
}
