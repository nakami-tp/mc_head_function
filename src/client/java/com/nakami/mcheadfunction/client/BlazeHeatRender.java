package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.head.HeadLookups;
import com.nakami.mcheadfunction.head.HeadType;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.RotationAxis;

/** Solid orbiting rods are stable at any particle setting; first-person uses the heat HUD. */
public final class BlazeHeatRender {
	private static final ItemStack ROD = new ItemStack(Items.BLAZE_ROD);
	private BlazeHeatRender() { }
	public static void render(WorldRenderContext context) {
		var client = MinecraftClient.getInstance();
		var player = client.player;
		var status = HeadHud.status;
		if (player == null || status == null || status.blazeHeat() == 0 || player.isSpectator()
			|| client.options.getPerspective().isFirstPerson() || HeadLookups.worn(player) != HeadType.BLAZE) return;
		var matrices = context.matrixStack(); var consumers = context.consumers();
		if (matrices == null || consumers == null) return;
		float delta = context.tickCounter().getTickDelta(false);
		var origin = player.getLerpedPos(delta).subtract(context.camera().getPos());
		for (int i = 0; i < 3; i++) {
			double angle = (player.age + delta) * 0.035 + i * Math.PI * 2 / 3;
			matrices.push();
			matrices.translate(origin.x + Math.cos(angle) * 0.85, origin.y + 1.5, origin.z + Math.sin(angle) * 0.85);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float)-angle));
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45));
			matrices.scale(0.75F, 0.75F, 0.75F);
			int light = status.blazeHeat() >= (i + 1) * 20 ? LightmapTextureManager.MAX_LIGHT_COORDINATE : LightmapTextureManager.pack(3, 3);
			client.getItemRenderer().renderItem(ROD, ModelTransformationMode.FIXED, light, OverlayTexture.DEFAULT_UV,
				matrices, consumers, player.getWorld(), i);
			matrices.pop();
		}
	}
}
