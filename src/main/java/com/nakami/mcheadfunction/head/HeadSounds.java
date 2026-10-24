package com.nakami.mcheadfunction.head;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** Short, positional cues. Continuous actions call these at explicit, bounded intervals. */
public final class HeadSounds {
	public static final SoundEvent PUNCH_ONE = register("beheading.punch_one");
	public static final SoundEvent PUNCH_TWO = register("beheading.punch_two");
	public static final SoundEvent PUNCH_FINISH = register("beheading.punch_finish");

	private static SoundEvent register(String path) {
		var id = com.nakami.mcheadfunction.McHeadFunction.id(path);
		return net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}

	public static void register() { }

	/** One impact per accepted punch, including the finisher; no overlapping weak-hit cue. */
	public static void punch(Entity target, int hit) {
		SoundEvent sound = switch (hit) {
			case 1 -> PUNCH_ONE;
			case 2 -> PUNCH_TWO;
			case 3 -> PUNCH_FINISH;
			default -> throw new IllegalArgumentException("Invalid punch: " + hit);
		};
		play(target, sound, hit == 3 ? 1.0F : 0.85F, 1.0F);
	}

	private HeadSounds() {
	}

	public static void play(Entity source, SoundEvent sound, float volume, float pitch) {
		if (!source.getWorld().isClient()) {
			float variation = (source.getRandom().nextFloat() - 0.5F) * 0.08F;
			source.getWorld().playSound(null, source.getX(), source.getY(), source.getZ(), sound,
				SoundCategory.PLAYERS, volume, pitch + variation);
		}
	}

	public static void launch(Entity source, HeadType type) {
		switch (type) {
			case ENDERMAN -> play(source, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.4F, 1.35F);
			case CHARGED_CREEPER -> play(source, SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), 0.45F, 1.65F);
			case CREEPER -> play(source, SoundEvents.ENTITY_CREEPER_PRIMED, 0.4F, 1.6F);
			case BLAZE -> play(source, SoundEvents.ITEM_FIRECHARGE_USE, 0.45F, 1.05F);
			case IRON_GOLEM -> play(source, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 0.5F, 0.8F);
			case GOAT -> play(source, SoundEvents.ENTITY_GOAT_PREPARE_RAM, 0.45F, 1.15F);
			case BAT -> play(source, SoundEvents.ENTITY_BAT_TAKEOFF, 0.4F, 1.3F);
			case BEE, CHICKEN -> play(source, SoundEvents.ENTITY_BAT_TAKEOFF, 0.25F, 1.65F);
			default -> play(source, SoundEvents.ENTITY_SNOWBALL_THROW, 0.35F, switch (type) {
				case ZOMBIE, COW, ARMADILLO -> 0.65F;
				case RABBIT, FOX, FROG -> 1.35F;
				default -> 0.95F;
			});
		}
	}

	public static void impact(Entity source, HeadType type) {
		switch (type) {
			// Their explosions already supply a loud impact. Do not stack another blast.
			case CREEPER, CHARGED_CREEPER -> { }
			case IRON_GOLEM -> play(source, SoundEvents.BLOCK_ANVIL_LAND, 0.3F, 0.7F);
			case GOAT -> play(source, SoundEvents.ENTITY_GOAT_RAM_IMPACT, 0.65F, 0.9F);
			case ENDERMAN -> play(source, SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, 0.3F, 0.7F);
			case BLAZE -> play(source, SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.25F, 1.4F);
			case FROG -> play(source, SoundEvents.ENTITY_SLIME_SQUISH_SMALL, 0.35F, 1.25F);
			case SHEEP, RABBIT, FOX, WOLF, CHICKEN, BEE, BAT -> play(source, SoundEvents.BLOCK_WOOL_FALL, 0.45F, 1.1F);
			case ARMADILLO -> play(source, SoundEvents.BLOCK_DRIPSTONE_BLOCK_HIT, 0.45F, 0.8F);
			default -> play(source, SoundEvents.BLOCK_WOOD_FALL, 0.45F, 0.85F);
		}
	}

	public static void swallow(Entity source) {
		play(source, SoundEvents.ENTITY_GENERIC_EAT, 0.95F, 0.82F);
		play(source, SoundEvents.ENTITY_PIG_AMBIENT, 0.8F, 1.2F);
	}

	public static void chew(Entity source) {
		play(source, SoundEvents.ENTITY_GENERIC_EAT, 0.9F, 0.7F);
	}

	public static void gulp(Entity source) {
		play(source, SoundEvents.ENTITY_GENERIC_DRINK, 0.75F, 0.86F);
		play(source, SoundEvents.ENTITY_PIG_AMBIENT, 0.45F, 0.75F);
	}

	public static void belly(Entity source) {
		play(source, SoundEvents.ENTITY_SLIME_SQUISH, 0.6F, 0.52F);
	}

	public static void stuffedOink(Entity source) {
		play(source, SoundEvents.ENTITY_PIG_AMBIENT, 0.3F, 0.52F);
	}

	public static void spit(Entity source) {
		play(source, SoundEvents.ENTITY_PLAYER_BURP, 0.95F, 0.6F);
		play(source, SoundEvents.ENTITY_PIG_HURT, 0.45F, 1.35F);
		play(source, SoundEvents.ENTITY_SLIME_SQUISH, 0.45F, 1.3F);
	}

	/** Recovery notifications go only to the wearer, not everyone nearby. */
	public static void ready(ServerPlayerEntity player, HeadType type, boolean full) {
		SoundEvent sound = type == HeadType.ENDERMAN ? SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME : SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
		player.playSoundToPlayer(sound, SoundCategory.PLAYERS, 0.18F, full ? 1.4F : 1.05F);
	}
}
