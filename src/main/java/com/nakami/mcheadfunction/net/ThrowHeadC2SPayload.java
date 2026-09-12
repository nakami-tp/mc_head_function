package com.nakami.mcheadfunction.net;

import com.nakami.mcheadfunction.McHeadFunction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record ThrowHeadC2SPayload() implements CustomPayload {
	public static final CustomPayload.Id<ThrowHeadC2SPayload> ID = new CustomPayload.Id<>(McHeadFunction.id("throw_head"));
	public static final PacketCodec<RegistryByteBuf, ThrowHeadC2SPayload> CODEC = PacketCodec.unit(new ThrowHeadC2SPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
