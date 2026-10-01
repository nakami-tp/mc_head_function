package com.nakami.mcheadfunction.net;

import com.nakami.mcheadfunction.McHeadFunction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** -1 left, 0 straight, 1 right. The server determines the turn rate. */
public record GoatSteerC2SPayload(int direction) implements CustomPayload {
	public static final Id<GoatSteerC2SPayload> ID = new Id<>(McHeadFunction.id("goat_steer"));
	public static final PacketCodec<RegistryByteBuf, GoatSteerC2SPayload> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, GoatSteerC2SPayload::direction, GoatSteerC2SPayload::new);
	@Override public Id<? extends CustomPayload> getId() { return ID; }
}
