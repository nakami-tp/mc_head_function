package com.nakami.mcheadfunction.net;

import com.nakami.mcheadfunction.McHeadFunction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Charge orientation and view-lock lifecycle, independent of the slower HUD updates. */
public record GoatChargeS2CPayload(boolean active, float yaw, float pitch) implements CustomPayload {
	public static final Id<GoatChargeS2CPayload> ID = new Id<>(McHeadFunction.id("goat_charge"));
	public static final PacketCodec<RegistryByteBuf, GoatChargeS2CPayload> CODEC = new PacketCodec<>() {
		public GoatChargeS2CPayload decode(RegistryByteBuf b) { return new GoatChargeS2CPayload(b.readBoolean(), b.readFloat(), b.readFloat()); }
		public void encode(RegistryByteBuf b, GoatChargeS2CPayload p) { b.writeBoolean(p.active()); b.writeFloat(p.yaw()); b.writeFloat(p.pitch()); }
	};
	@Override public Id<? extends CustomPayload> getId() { return ID; }
}
