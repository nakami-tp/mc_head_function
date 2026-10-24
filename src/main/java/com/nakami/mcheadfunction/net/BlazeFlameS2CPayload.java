package com.nakami.mcheadfunction.net;

import com.nakami.mcheadfunction.McHeadFunction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.Vec3d;

/** One emission, not a persistent emitter: stopping or losing tracking cannot leave fire running. */
public record BlazeFlameS2CPayload(int source, Vec3d origin, Vec3d direction, float range, int heat, boolean wave) implements CustomPayload {
	public static final Id<BlazeFlameS2CPayload> ID = new Id<>(McHeadFunction.id("blaze_flame"));
	public static final PacketCodec<RegistryByteBuf, BlazeFlameS2CPayload> CODEC = new PacketCodec<>() {
		public BlazeFlameS2CPayload decode(RegistryByteBuf b) {
			return new BlazeFlameS2CPayload(b.readVarInt(), b.readVec3d(), b.readVec3d(), b.readFloat(), b.readVarInt(), b.readBoolean());
		}
		public void encode(RegistryByteBuf b, BlazeFlameS2CPayload p) {
			b.writeVarInt(p.source()); b.writeVec3d(p.origin()); b.writeVec3d(p.direction());
			b.writeFloat(p.range()); b.writeVarInt(p.heat()); b.writeBoolean(p.wave());
		}
	};
	@Override public Id<? extends CustomPayload> getId() { return ID; }
}
