package com.nakami.mcheadfunction.net;

import com.nakami.mcheadfunction.McHeadFunction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record HeadSkillC2SPayload(boolean pressed) implements CustomPayload {
	public static final CustomPayload.Id<HeadSkillC2SPayload> ID = new CustomPayload.Id<>(McHeadFunction.id("head_skill"));
	public static final PacketCodec<RegistryByteBuf, HeadSkillC2SPayload> CODEC =
		PacketCodec.tuple(PacketCodecs.BOOL, HeadSkillC2SPayload::pressed, HeadSkillC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
