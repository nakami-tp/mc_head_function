package com.nakami.mcheadfunction.net;

import com.nakami.mcheadfunction.McHeadFunction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.Vec3d;

/** One emission, not a persistent emitter: stopping or losing tracking cannot leave fire running. */
public record BlazeFlameS2CPayload(int source, Vec3d origin, Vec3d direction, float range, int heat, Kind kind) implements CustomPayload {
	public enum Kind {
		BREATH, BURST, FUSE, PILLAR, IMPACT,
		CORE_CHARGE, CORE_LAUNCH, CORE_FLY, CORE_BLAST, CORE_FIZZLE;
		public boolean core() { return ordinal() >= CORE_CHARGE.ordinal(); }
	}
	public static final Id<BlazeFlameS2CPayload> ID = new Id<>(McHeadFunction.id("blaze_flame"));
	public static final PacketCodec<RegistryByteBuf, BlazeFlameS2CPayload> CODEC = new PacketCodec<>() {
		public BlazeFlameS2CPayload decode(RegistryByteBuf b) {
			return new BlazeFlameS2CPayload(b.readVarInt(), b.readVec3d(), b.readVec3d(), b.readFloat(), b.readVarInt(), b.readEnumConstant(Kind.class));
		}
		public void encode(RegistryByteBuf b, BlazeFlameS2CPayload p) {
			b.writeVarInt(p.source()); b.writeVec3d(p.origin()); b.writeVec3d(p.direction());
			b.writeFloat(p.range()); b.writeVarInt(p.heat()); b.writeEnumConstant(p.kind());
		}
	};
	@Override public Id<? extends CustomPayload> getId() { return ID; }
}
