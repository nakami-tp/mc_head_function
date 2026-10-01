package com.nakami.mcheadfunction.net;

import com.nakami.mcheadfunction.McHeadFunction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record GoatImpactS2CPayload(int duration) implements CustomPayload {
	public static final CustomPayload.Id<GoatImpactS2CPayload> ID = new CustomPayload.Id<>(McHeadFunction.id("goat_impact"));
	public static final PacketCodec<RegistryByteBuf, GoatImpactS2CPayload> CODEC = PacketCodec.tuple(net.minecraft.network.codec.PacketCodecs.VAR_INT, GoatImpactS2CPayload::duration, GoatImpactS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
