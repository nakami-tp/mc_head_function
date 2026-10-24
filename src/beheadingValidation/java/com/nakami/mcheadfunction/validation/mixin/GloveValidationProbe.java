package com.nakami.mcheadfunction.validation.mixin;

import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.validation.BeheadingClientValidation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class GloveValidationProbe {
    @Inject(method = "renderArmHoldingItem", at = @At("HEAD"))
    private void punch(MatrixStack matrices, VertexConsumerProvider vertices, int light, float equip, float swing, Arm arm, CallbackInfo ci) {
        var player = MinecraftClient.getInstance().player;
        if (player != null && player.getMainHandStack().isOf(HeadItems.BEHEADING_GLOVE)) {
            BeheadingClientValidation.observeArm(arm, equip, swing, matrices.peek().getPositionMatrix().m30(), matrices.peek().getPositionMatrix().m32());
        }
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"))
    private void noFlatGlove(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean left,
            MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        if (stack.isOf(HeadItems.BEHEADING_GLOVE)) BeheadingClientValidation.flatGloveFrames++;
    }
}
