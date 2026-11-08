package com.nakami.mcheadfunction.validation.mixin;

import com.nakami.mcheadfunction.client.CreeperGrenadeRender;
import com.nakami.mcheadfunction.entity.ThrownHeadEntity;
import com.nakami.mcheadfunction.validation.FearClientValidation;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreeperGrenadeRender.class)
public class GrenadeRenderProbe {
    @Inject(method="head(Lcom/nakami/mcheadfunction/entity/ThrownHeadEntity;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IF)V",at=@At("RETURN"),remap=false)
    private static void observe(ThrownHeadEntity grenade,MatrixStack matrices,VertexConsumerProvider vertices,int light,float tickDelta,CallbackInfo ci) {
        FearClientValidation.observeHead(grenade);
    }
}
