package com.nakami.mcheadfunction.net;

import com.nakami.mcheadfunction.throwing.ThrowHandler;
import com.nakami.mcheadfunction.wear.SkillHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class ModNetworking {
	private ModNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(ThrowHeadC2SPayload.ID, ThrowHeadC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(HeadSkillC2SPayload.ID, HeadSkillC2SPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(HeadStatusS2CPayload.ID, HeadStatusS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SonarS2CPayload.ID, SonarS2CPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ThrowHeadC2SPayload.ID, (payload, context) ->
			context.server().execute(() -> ThrowHandler.throwHeldHead(context.player()))
		);
		ServerPlayNetworking.registerGlobalReceiver(HeadSkillC2SPayload.ID, (payload, context) ->
			context.server().execute(() -> SkillHandler.onSkill(context.player(), payload.pressed()))
		);
	}
}
