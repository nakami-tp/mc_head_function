package com.nakami.mcheadfunction.net;

import com.nakami.mcheadfunction.McHeadFunction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Nearby viewers read heat from the worn head instead of a HUD. */
public record BlazeLookS2CPayload(int source, int heat, int cooldown) implements CustomPayload {
	public static final Id<BlazeLookS2CPayload> ID = new Id<>(McHeadFunction.id("blaze_look"));
	public static final PacketCodec<RegistryByteBuf, BlazeLookS2CPayload> CODEC = new PacketCodec<>() {
		public BlazeLookS2CPayload decode(RegistryByteBuf b) {
			return new BlazeLookS2CPayload(b.readVarInt(), b.readVarInt(), b.readVarInt());
		}
		public void encode(RegistryByteBuf b, BlazeLookS2CPayload p) {
			b.writeVarInt(p.source());
			b.writeVarInt(p.heat());
			b.writeVarInt(p.cooldown());
		}
	};

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
