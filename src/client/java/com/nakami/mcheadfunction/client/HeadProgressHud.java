package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.net.HeadProgressS2CPayload;
import com.nakami.mcheadfunction.rule.HeadDepthRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

public final class HeadProgressHud {
	public static HeadProgressS2CPayload progress;
	private HeadProgressHud() { }
	public static void render(DrawContext draw, RenderTickCounter counter) {
		var client = MinecraftClient.getInstance();
		if (progress == null || client.player == null || client.options.hudHidden || client.currentScreen != null || client.player.isSpectator()) return;
		HeadType type = HeadLookups.worn(client.player);
		if (type == null) type = HeadItems.ofStack(client.player.getMainHandStack());
		if (type == null) return;
		int xp = progress.points()[type.ordinal()];
		int x = 8, y = 8;
		Text label = Text.translatable("mastery.mc_head_function.progress", HeadItems.item(type).getName(), xp, HeadDepthRules.MASTERY_MAX);
		draw.drawTextWithShadow(client.textRenderer, label, x, y, xp >= HeadDepthRules.MASTERY_MAX ? 0xFFE29A : 0xD5E7CC);
		draw.fill(x, y + 11, x + 100, y + 14, 0xA0303030);
		draw.fill(x, y + 11, x + Math.min(100, xp / 10), y + 14, 0xFFE0B35C);
		Text state = null;
		if (HeadLookups.worn(client.player) == HeadType.RABBIT) state = Text.translatable("hud.mc_head_function.rabbit", progress.rabbitChain(), HeadDepthRules.rabbitHeight(Math.max(1, progress.rabbitChain())));
		if (HeadLookups.worn(client.player) == HeadType.GOAT) state = progress.goatTicks() > 0
			? Text.translatable("hud.mc_head_function.charging", client.options.leftKey.getBoundKeyLocalizedText(), client.options.rightKey.getBoundKeyLocalizedText())
			: Text.translatable("hud.mc_head_function.goat_ready", Math.max(0, progress.goatCooldown() / 20));
		if (state != null) draw.drawTextWithShadow(client.textRenderer, state, x, y + 19, 0xFFFFFF);
	}
}
