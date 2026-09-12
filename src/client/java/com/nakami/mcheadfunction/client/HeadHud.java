package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.head.HeadLookups;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.net.HeadStatusS2CPayload;
import com.nakami.mcheadfunction.rule.HeadRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Arm;

/** A small recovery meter beside the hotbar; ability descriptions stay in item tooltips. */
public final class HeadHud {
	public static HeadStatusS2CPayload status;

	private HeadHud() {
	}

	public static void render(DrawContext draw, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null || status == null || client.options.hudHidden
			|| client.player.isSpectator() || client.currentScreen != null) {
			return;
		}
		HeadType type = HeadLookups.worn(client.player);
		if (type == null) {
			return;
		}

		int remaining;
		int total;
		switch (type) {
			case CHARGED_CREEPER -> {
				total = HeadRules.lightningChargeNeeded(client.world.isThundering());
				remaining = total - status.lightning();
			}
			case FROG -> {
				total = HeadRules.FROG_COOLDOWN_TICKS;
				remaining = status.frog();
			}
			case LLAMA -> {
				total = HeadRules.LLAMA_COOLDOWN_TICKS;
				remaining = status.llama();
			}
			case ENDERMAN -> {
				total = HeadRules.ENDERMAN_DODGE_COOLDOWN_TICKS;
				remaining = status.dodge();
			}
			case CREEPER -> {
				total = HeadRules.CREEPER_MAX_CHARGES * HeadRules.CREEPER_RECHARGE_TICKS;
				remaining = status.charges() >= HeadRules.CREEPER_MAX_CHARGES ? 0
					: (HeadRules.CREEPER_MAX_CHARGES - status.charges()) * HeadRules.CREEPER_RECHARGE_TICKS - status.recharge();
			}
			default -> {
				return;
			}
		}
		if (remaining <= 0) {
			return;
		}

		float progress = Math.clamp(1F - (float) remaining / total, 0F, 1F);
		int accent = switch (type) {
			case ENDERMAN -> 0xFFBB88FF;
			case CHARGED_CREEPER -> 0xFF75E7FF;
			default -> 0xFF9ED79A;
		};
		// Match the vanilla hotbar anchor and leave room for its left-side auxiliary slot.
		int reserved = 0;
		if (client.player.getMainArm() == Arm.RIGHT && !client.player.getOffHandStack().isEmpty()) {
			reserved = 29;
		} else if (client.player.getMainArm() == Arm.LEFT
			&& client.options.getAttackIndicator().getValue() == AttackIndicator.HOTBAR) {
			reserved = 22;
		}
		int x = draw.getScaledWindowWidth() / 2 - 91 - reserved - 10;
		int y = draw.getScaledWindowHeight() - 20;
		if (x < 2) {
			return;
		}
		draw.fill(x, y, x + 5, y + 18, 0xC0182028);
		draw.fill(x + 1, y + 1, x + 4, y + 17, 0xFF394550);
		int filled = Math.round(16 * progress);
		if (filled > 0) {
			draw.fill(x + 1, y + 17 - filled, x + 4, y + 17, accent);
		}
		if (type == HeadType.CREEPER) {
			// Two segments make the two ammo charges visible without text or an icon.
			draw.fill(x + 1, y + 9, x + 4, y + 10, 0xC0182028);
		}
	}
}
