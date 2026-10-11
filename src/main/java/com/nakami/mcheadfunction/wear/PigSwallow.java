package com.nakami.mcheadfunction.wear;

import com.nakami.mcheadfunction.entity.RecycledHeadItemEntity;
import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.HeadSounds;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.head.PigStuffedAccess;
import com.nakami.mcheadfunction.head.PlayerHeadAccess;
import com.nakami.mcheadfunction.head.PlayerHeadState;
import com.nakami.mcheadfunction.progression.HeadMastery;
import com.nakami.mcheadfunction.rule.HeadRules;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class PigSwallow {
	private static final Identifier SLOW = Identifier.of("mc_head_function", "pig_stuffed");
	private static final Identifier JUMP = Identifier.of("mc_head_function", "pig_stuffed_jump");

	private PigSwallow() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayerEntity player) spit(player, true);
		});
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) return;
			PlayerHeadState from = PlayerHeadAccess.state(oldPlayer);
			if (from.stuffedTicks <= 0) return;
			PlayerHeadState to = PlayerHeadAccess.state(newPlayer);
			to.stuffedTicks = from.stuffedTicks;
			to.savedFood = from.savedFood;
			to.savedSaturation = from.savedSaturation;
			sync(newPlayer);
			applySlow(newPlayer);
		});
	}

	public static boolean trySwallow(ServerPlayerEntity player) {
		if (!player.isAlive() || player.isSpectator()) return false;
		PlayerHeadState state = PlayerHeadAccess.state(player);
		if (state.stuffedTicks > 0) return false;
		ItemStack stack = player.getMainHandStack();
		if (HeadItems.ofStack(stack) != HeadType.PIG) return false;
		if (!player.getAbilities().creativeMode) stack.decrement(1);
		var hunger = player.getHungerManager();
		state.savedFood = hunger.getFoodLevel();
		state.savedSaturation = hunger.getSaturationLevel();
		hunger.setFoodLevel(20);
		hunger.setSaturationLevel(20);
		state.stuffedTicks = HeadRules.PIG_STUFFED_TICKS;
		sync(player);
		applySlow(player);
		HeadMastery.practice(player, HeadType.PIG);
		HeadSounds.swallow(player);
		crumbs(player, 10, true);
		return true;
	}

	public static void tick(ServerPlayerEntity player) {
		PlayerHeadState state = PlayerHeadAccess.state(player);
		if (state.stuffedTicks <= 0) {
			clearSlow(player);
			sync(player);
			return;
		}
		state.stuffedTicks--;
		sync(player);
		applySlow(player);
		if (state.stuffedTicks == HeadRules.PIG_STUFFED_TICKS - 5) HeadSounds.chew(player);
		if (state.stuffedTicks == HeadRules.PIG_STUFFED_TICKS - 10) {
			HeadSounds.gulp(player);
			crumbs(player, 6, true);
		}
		if (state.stuffedTicks == HeadRules.PIG_STUFFED_TICKS - 16) {
			HeadSounds.belly(player);
			crumbs(player, 4, false);
		}
		if (state.stuffedTicks > 0 && state.stuffedTicks % 25 == 0) HeadSounds.stuffedOink(player);
		if (state.stuffedTicks == 0) spit(player, false);
	}

	public static void spit(ServerPlayerEntity player, boolean death) {
		PlayerHeadState state = PlayerHeadAccess.state(player);
		if (state.savedFood < 0 && state.stuffedTicks <= 0) return;
		int food = state.savedFood;
		float saturation = state.savedSaturation;
		state.stuffedTicks = 0;
		state.savedFood = -1;
		state.savedSaturation = 0;
		sync(player);
		clearSlow(player);
		if (!death) {
			var hunger = player.getHungerManager();
			if (food >= 0) {
				hunger.setFoodLevel(food);
				hunger.setSaturationLevel(Math.min(food, saturation));
			}
		}
		if (!(player.getWorld() instanceof ServerWorld world)) return;
		if (!death) HeadSounds.spit(player);
		Vec3d look = player.getRotationVec(1);
		Vec3d mouth = player.getEyePos().add(look.multiply(0.28)).add(0, -0.12, 0);
		if (!player.isInLava() && player.getY() >= world.getBottomY()) {
			RecycledHeadItemEntity item = new RecycledHeadItemEntity(world, mouth.x, mouth.y, mouth.z,
				new ItemStack(HeadItems.item(HeadType.PIG)));
			item.setPickupDelay(10);
			item.setVelocity(look.x * 0.38, 0.32, look.z * 0.38);
			world.spawnEntity(item);
		}
		crumbs(player, 12, false);
		world.spawnParticles(ParticleTypes.CLOUD, mouth.x, mouth.y, mouth.z, 8, 0.15, 0.1, 0.15, 0.04);
	}

	private static void sync(PlayerEntity player) {
		((PigStuffedAccess) player).mhf$setStuffedTicks(PlayerHeadAccess.state(player).stuffedTicks);
	}

	private static void applySlow(PlayerEntity player) {
		replace(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SLOW, HeadRules.PIG_SPEED_FACTOR);
		replace(player, EntityAttributes.GENERIC_JUMP_STRENGTH, JUMP, HeadRules.PIG_JUMP_FACTOR);
	}

	private static void clearSlow(PlayerEntity player) {
		remove(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SLOW);
		remove(player, EntityAttributes.GENERIC_JUMP_STRENGTH, JUMP);
	}

	private static void replace(PlayerEntity player, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute, Identifier id, double value) {
		var instance = player.getAttributeInstance(attribute);
		if (instance == null) return;
		instance.removeModifier(id);
		instance.addTemporaryModifier(new EntityAttributeModifier(id, value, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	private static void remove(PlayerEntity player, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute, Identifier id) {
		var instance = player.getAttributeInstance(attribute);
		if (instance != null) instance.removeModifier(id);
	}

	private static void crumbs(ServerPlayerEntity player, int count, boolean mouth) {
		if (!(player.getWorld() instanceof ServerWorld world)) return;
		Vec3d look = player.getRotationVec(1);
		Vec3d at = mouth
			? player.getEyePos().add(look.multiply(0.25)).add(0, -0.1, 0)
			: player.getPos().add(0, 0.85, 0).add(look.multiply(0.2));
		world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(HeadItems.item(HeadType.PIG))),
			at.x, at.y, at.z, count, 0.12, 0.08, 0.12, 0.06);
		if (mouth) world.spawnParticles(ParticleTypes.HEART, at.x, at.y + 0.15, at.z, 2, 0.15, 0.1, 0.15, 0);
	}
}
