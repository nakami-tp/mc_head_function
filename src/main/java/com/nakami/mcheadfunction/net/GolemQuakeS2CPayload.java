package com.nakami.mcheadfunction.net;

import com.nakami.mcheadfunction.McHeadFunction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record GolemQuakeS2CPayload(float strength) implements CustomPayload {
	public static final Id<GolemQuakeS2CPayload> ID = new Id<>(McHeadFunction.id("golem_quake"));
	public static final PacketCodec<RegistryByteBuf, GolemQuakeS2CPayload> CODEC = PacketCodec.tuple(
		PacketCodecs.FLOAT, GolemQuakeS2CPayload::strength, GolemQuakeS2CPayload::new);
	@Override public Id<? extends CustomPayload> getId() { return ID; }
}
