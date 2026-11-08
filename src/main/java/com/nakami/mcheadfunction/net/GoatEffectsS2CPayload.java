package com.nakami.mcheadfunction.net;

import com.nakami.mcheadfunction.McHeadFunction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.Vec3d;

/** One emission; stopping a charge cannot leave a client-side emitter running. */
public record GoatEffectsS2CPayload(int source, int kind, Vec3d origin, Vec3d direction, int steering) implements CustomPayload {
	public static final int RUN = 0, DRIFT = 1, HIT = 2;
	public static final Id<GoatEffectsS2CPayload> ID = new Id<>(McHeadFunction.id("goat_effects"));
	public static final PacketCodec<RegistryByteBuf, GoatEffectsS2CPayload> CODEC = new PacketCodec<>() {
		public GoatEffectsS2CPayload decode(RegistryByteBuf b) {
			return new GoatEffectsS2CPayload(b.readVarInt(), b.readVarInt(), b.readVec3d(), b.readVec3d(), b.readByte());
		}
		public void encode(RegistryByteBuf b, GoatEffectsS2CPayload p) {
			b.writeVarInt(p.source()); b.writeVarInt(p.kind()); b.writeVec3d(p.origin()); b.writeVec3d(p.direction()); b.writeByte(p.steering());
		}
	};
	@Override public Id<? extends CustomPayload> getId() { return ID; }
}
