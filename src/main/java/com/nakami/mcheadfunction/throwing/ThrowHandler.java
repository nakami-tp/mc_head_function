package com.nakami.mcheadfunction.throwing;

import com.nakami.mcheadfunction.entity.ThrownHeadEntity;
import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.HeadType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class ThrowHandler {
	private ThrowHandler() {
	}

	public static void throwHeldHead(ServerPlayerEntity player) {
		ItemStack stack = player.getMainHandStack();
		HeadType type = HeadItems.ofStack(stack);
		if (type == null) {
			return;
		}
		spawn(player, type, type.throwStyle, 1.4F, type.throwStyle == HeadType.ThrowStyle.DEFAULT);
		if (!player.getAbilities().creativeMode) {
			stack.decrement(1);
		}
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.6F, 0.9F);
	}

	public static void knockOff(PlayerEntity player, HeadType type) {
		spawn(player, type, HeadType.ThrowStyle.KNOCK_OFF, 0.85F, false);
	}

	private static void spawn(PlayerEntity player, HeadType type, HeadType.ThrowStyle style, float speed, boolean gravityArc) {
		World world = player.getWorld();
		ThrownHeadEntity thrown = new ThrownHeadEntity(world, player, type, style);
		Vec3d look = player.getRotationVec(1.0F);
		thrown.setPosition(player.getX(), player.getEyeY() - 0.1, player.getZ());
		if (gravityArc) {
			thrown.setVelocity(look.x, look.y + 0.12, look.z, speed, 1.0F);
			thrown.setNoGravity(false);
		} else {
			float launch = switch (style) {
				case CHARGED_PIERCE, CREEPER_BLAST -> 2.6F;
				case BLAZE_TRAIL, GOAT_RAM -> 1.8F;
				case ENDERMAN_EAT -> 1.6F;
				default -> speed;
			};
			thrown.setVelocity(look.x, look.y, look.z, launch, 0.0F);
			thrown.setNoGravity(style != HeadType.ThrowStyle.KNOCK_OFF && style != HeadType.ThrowStyle.ZOMBIE_HOP && style != HeadType.ThrowStyle.GOLEM_ROLL);
		}
		world.spawnEntity(thrown);
	}
}
