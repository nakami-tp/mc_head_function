package com.nakami.mcheadfunction.net;

import com.nakami.mcheadfunction.McHeadFunction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record RabbitJumpC2SPayload() implements CustomPayload {
	public static final Id<RabbitJumpC2SPayload> ID = new Id<>(McHeadFunction.id("rabbit_jump"));
	public static final PacketCodec<RegistryByteBuf, RabbitJumpC2SPayload> CODEC = PacketCodec.unit(new RabbitJumpC2SPayload());
	@Override public Id<? extends CustomPayload> getId() { return ID; }
}
