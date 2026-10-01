package com.nakami.mcheadfunction.net;

import com.nakami.mcheadfunction.McHeadFunction;
import com.nakami.mcheadfunction.head.HeadType;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record HeadProgressS2CPayload(int[] points, int rabbitChain, int goatCooldown, int goatTicks) implements CustomPayload {
	public static final Id<HeadProgressS2CPayload> ID = new Id<>(McHeadFunction.id("head_progress"));
	public static final PacketCodec<RegistryByteBuf, HeadProgressS2CPayload> CODEC = new PacketCodec<>() {
		public HeadProgressS2CPayload decode(RegistryByteBuf b) {
			int[] points = new int[HeadType.values().length];
			for (int i = 0; i < points.length; i++) points[i] = b.readVarInt();
			return new HeadProgressS2CPayload(points, b.readVarInt(), b.readVarInt(), b.readVarInt());
		}
		public void encode(RegistryByteBuf b, HeadProgressS2CPayload p) {
			for (int value : p.points()) b.writeVarInt(value);
			b.writeVarInt(p.rabbitChain()); b.writeVarInt(p.goatCooldown()); b.writeVarInt(p.goatTicks());
		}
	};
	@Override public Id<? extends CustomPayload> getId() { return ID; }
}
