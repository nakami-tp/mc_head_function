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
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
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
		EntityRendererRegistry.register(ModEntities.THROWN_HEAD, FlyingItemEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.HEAD_AMMO, FlyingItemEntityRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(SonarS2CPayload.ID, (payload, context) ->
			context.client().execute(() -> sonarTicks = HeadRules.SONAR_TICKS)
		);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (sonarTicks > 0) {
				sonarTicks--;
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
				return;
			}
			boolean pressed = SKILL_KEY.isPressed();
			if (pressed != last) {
				ClientPlayNetworking.send(new HeadSkillC2SPayload(pressed));
				last = pressed;
			}
		}
	}
}
