package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.rule.HeadRules;
import com.nakami.mcheadfunction.wear.FrogMouth;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

/** First-person chew: crumbs at the camera, a small nod, extra drip between bites. */
public final class FrogMouthEffects {
	private FrogMouthEffects() {
	}

	public static LivingEntity held(MinecraftClient client) {
		return client.player == null ? null : FrogMouth.held(client.player);
	}

	public static void tick(MinecraftClient client) {
		if (client.player == null || client.world == null) return;
		LivingEntity held = held(client);
		if (held == null) return;
		Vec3d look = client.player.getRotationVec(1);
		Vec3d mouth = client.player.getEyePos().add(look.multiply(0.28)).add(0, -0.1, 0);
		boolean bite = held.hurtTime > 4;
		if (bite || client.world.random.nextFloat() < 0.4F) {
			ItemStack crumb = crumb(held);
			int n = bite ? 3 : 1;
			for (int i = 0; i < n; i++) {
				client.world.addParticle(new ItemStackParticleEffect(ParticleTypes.ITEM, crumb),
					mouth.x, mouth.y, mouth.z,
					(client.world.random.nextFloat() - 0.5) * 0.14,
					0.04 + client.world.random.nextFloat() * 0.06,
					(client.world.random.nextFloat() - 0.5) * 0.14);
			}
		}
		if (client.player.age % 3 == 0) {
			client.world.addParticle(ParticleTypes.FALLING_HONEY,
				mouth.x, mouth.y - 0.06, mouth.z, 0, -0.02, 0);
		}
	}

	public static float pitch(float delta) {
		var client = MinecraftClient.getInstance();
		LivingEntity held = held(client);
		if (client.player != null && com.nakami.mcheadfunction.wear.FrogProjectile.occupied(client.player)
			&& client.options.getPerspective().isFirstPerson()) return (float) Math.sin((client.player.age + delta) * 2.4) * .3F;
		if (held == null || client.player == null || !client.options.getPerspective().isFirstPerson()) return 0;
		return HeadRules.frogChewPitch(client.player.age + delta, Math.max(0, held.hurtTime - delta));
	}

	public static float yaw(float delta) {
		var client = MinecraftClient.getInstance();
		LivingEntity held = held(client);
		if (client.player != null && com.nakami.mcheadfunction.wear.FrogProjectile.occupied(client.player)
			&& client.options.getPerspective().isFirstPerson()) return (float) Math.sin((client.player.age + delta) * 2.4) * .3F;
		if (held == null || client.player == null || !client.options.getPerspective().isFirstPerson()) return 0;
		return HeadRules.frogChewYaw(client.player.age + delta, Math.max(0, held.hurtTime - delta));
	}

	public static float hand(float delta) {
		var client = MinecraftClient.getInstance();
		LivingEntity held = held(client);
		if (held == null || client.player == null) return 0;
		return HeadRules.frogChew(client.player.age + delta, Math.max(0, held.hurtTime - delta), false);
	}

	private static ItemStack crumb(LivingEntity held) {
		SpawnEggItem egg = SpawnEggItem.forEntity(held.getType());
		return new ItemStack(egg != null ? egg : Items.SLIME_BALL);
	}
}
