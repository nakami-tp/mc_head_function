package com.nakami.mcheadfunction.client.mixin;

import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.net.ThrowHeadC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow protected abstract boolean doAttack();
	@Shadow
	public ClientPlayerEntity player;

	@Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
	private void mhf$throwHead(CallbackInfoReturnable<Boolean> cir) {
		if (com.nakami.mcheadfunction.client.CreeperCookingControl.activeInput()) { cir.setReturnValue(true); return; }
        if (player == null || !HeadItems.isHead(player.getMainHandStack())) {
			return;
		}
		if (player.getMainHandStack().isOf(net.minecraft.item.Items.CREEPER_HEAD)) {
            com.nakami.mcheadfunction.client.CreeperCookingControl.begin();
            cir.setReturnValue(true);
            return;
        }
        ClientPlayNetworking.send(new ThrowHeadC2SPayload());
		player.swingHand(player.getActiveHand());
		cir.setReturnValue(true);
	}

	@Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
	private void mhf$noMineWithHead(boolean breaking, CallbackInfo ci) {
		if (player != null && (HeadItems.isHead(player.getMainHandStack()) || com.nakami.mcheadfunction.client.CreeperCookingControl.activeInput())) {
			ci.cancel();
		}
	}
    @Inject(method = "handleBlockBreaking", at = @At("TAIL"))
    private void mhf$holdZombieAttack(boolean breaking, CallbackInfo ci) {
        var client = (MinecraftClient) (Object) this;
        if (!breaking || player == null || client.interactionManager == null
            || client.currentScreen != null || client.getOverlay() != null || !client.isWindowFocused()
            || player.isUsingItem() || com.nakami.mcheadfunction.wear.GoatCharge.isStunned(player)
            || !com.nakami.mcheadfunction.wear.ZombieBlood.unarmed(player)
            || player.getAttackCooldownProgress(0) < 1) return;
        if (client.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hit && hit.getEntity().isAlive()) {
            // Reuse vanilla targeting, attack packets and swing; at most one fully charged hit per tick.
            // The first click already reset the cooldown, so it cannot also repeat in this tick.
            doAttack();
        }
    }

}
