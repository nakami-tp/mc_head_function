package com.nakami.mcheadfunction.net;

import com.nakami.mcheadfunction.McHeadFunction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record SonarS2CPayload(int duration) implements CustomPayload {
	public static final CustomPayload.Id<SonarS2CPayload> ID = new CustomPayload.Id<>(McHeadFunction.id("sonar"));
	public static final PacketCodec<RegistryByteBuf, SonarS2CPayload> CODEC = PacketCodec.tuple(net.minecraft.network.codec.PacketCodecs.VAR_INT, SonarS2CPayload::duration, SonarS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
