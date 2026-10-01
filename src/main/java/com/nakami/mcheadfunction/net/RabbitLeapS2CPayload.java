package com.nakami.mcheadfunction.net;

import com.nakami.mcheadfunction.McHeadFunction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Vanilla velocity packets clamp each axis to 3.9, too low for a 100-block leap. */
public record RabbitLeapS2CPayload(double x, double y, double z) implements CustomPayload {
	public static final Id<RabbitLeapS2CPayload> ID = new Id<>(McHeadFunction.id("rabbit_leap"));
	public static final PacketCodec<RegistryByteBuf, RabbitLeapS2CPayload> CODEC = new PacketCodec<>() {
		public RabbitLeapS2CPayload decode(RegistryByteBuf b) { return new RabbitLeapS2CPayload(b.readDouble(), b.readDouble(), b.readDouble()); }
		public void encode(RegistryByteBuf b, RabbitLeapS2CPayload p) { b.writeDouble(p.x()); b.writeDouble(p.y()); b.writeDouble(p.z()); }
	};
	@Override public Id<? extends CustomPayload> getId() { return ID; }
}
