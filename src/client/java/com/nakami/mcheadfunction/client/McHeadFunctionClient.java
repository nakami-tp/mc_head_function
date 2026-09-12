package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.entity.ModEntities;
import com.nakami.mcheadfunction.net.HeadSkillC2SPayload;
import com.nakami.mcheadfunction.net.SonarS2CPayload;
import com.nakami.mcheadfunction.rule.HeadRules;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class McHeadFunctionClient implements ClientModInitializer {
	public static final KeyBinding SKILL_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
		"key.mc_head_function.head_skill",
		InputUtil.Type.KEYSYM,
		GLFW.GLFW_KEY_R,
		"key.categories.mc_head_function"
	));
	public static int sonarTicks;

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.THROWN_HEAD, HeadProjectileRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(HeadHud::render);
		ClientPlayNetworking.registerGlobalReceiver(com.nakami.mcheadfunction.net.HeadStatusS2CPayload.ID,
			(payload, context) -> context.client().execute(() -> HeadHud.status = payload));
		net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, tooltipType, lines) -> {
			var type = com.nakami.mcheadfunction.head.HeadItems.ofStack(stack);
			if (type != null) {
				lines.add(net.minecraft.text.Text.translatable("head.mc_head_function." + type.itemPath() + ".throw").formatted(net.minecraft.util.Formatting.GRAY));
				lines.add(net.minecraft.text.Text.translatable("head.mc_head_function." + type.itemPath() + ".wear", SKILL_KEY.getBoundKeyLocalizedText()).formatted(net.minecraft.util.Formatting.AQUA));
			}
		});
		EntityRendererRegistry.register(ModEntities.HEAD_AMMO, HeadAmmoRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(SonarS2CPayload.ID, (payload, context) ->
			context.client().execute(() -> sonarTicks = HeadRules.SONAR_TICKS)
		);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (sonarTicks > 0) {
				sonarTicks--;
				if (client.player != null && client.world != null && sonarTicks % 2 == 0) {
					double radius = (HeadRules.SONAR_TICKS - sonarTicks) % 25 / 25.0 * HeadRules.SONAR_RANGE;
					for (int i = 0; i < 32; i++) {
						double angle = i * Math.PI * 2 / 32;
						client.world.addParticle(net.minecraft.particle.ParticleTypes.END_ROD,
							client.player.getX() + Math.cos(angle) * radius, client.player.getY() + 0.3,
							client.player.getZ() + Math.sin(angle) * radius, 0, 0.005, 0);
					}
				}
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(new SkillKeyTracker());
		WorldRenderEvents.AFTER_ENTITIES.register(SonarWorldRender::afterEntities);
	}

	private static final class SkillKeyTracker implements ClientTickEvents.EndTick {
		private boolean last;

		@Override
		public void onEndTick(net.minecraft.client.MinecraftClient client) {
			if (client.player == null) {
				last = false;
				HeadHud.status = null;
				sonarTicks = 0;
				return;
			}
			boolean pressed = client.currentScreen == null && client.isWindowFocused() && SKILL_KEY.isPressed();
			if (pressed != last) {
				ClientPlayNetworking.send(new HeadSkillC2SPayload(pressed));
				last = pressed;
			}
		}
	}
}
