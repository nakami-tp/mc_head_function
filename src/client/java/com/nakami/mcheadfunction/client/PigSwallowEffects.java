package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.head.PigStuffedAccess;
import com.nakami.mcheadfunction.rule.HeadRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/** First-person gulp and local crumbs. Belly fat lives on the living-entity renderer. */
public final class PigSwallowEffects {
	private PigSwallowEffects() {
	}

	public static int stuffed(PlayerEntity player) {
		return ((PigStuffedAccess) player).mhf$stuffedTicks();
	}

	public static void tick(MinecraftClient client) {
		if (client.player == null || client.world == null) return;
		int ticks = stuffed(client.player);
		float swallow = HeadRules.pigSwallowProgress(ticks, 0);
		if (swallow < 1 && client.world.random.nextFloat() < 0.55F) {
			Vec3d look = client.player.getRotationVec(1);
			Vec3d mouth = client.player.getEyePos().add(look.multiply(0.28)).add(0, -0.08, 0);
			client.world.addParticle(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(HeadItems.item(HeadType.PIG))),
				mouth.x, mouth.y, mouth.z,
				(client.world.random.nextFloat() - 0.5) * 0.08,
				0.04,
				(client.world.random.nextFloat() - 0.5) * 0.08);
		}
		float fat = HeadRules.pigFatness(ticks, 0);
		if (fat > 0.75F && client.player.age % 7 == 0) {
			Vec3d belly = client.player.getPos().add(0, 0.85, 0).add(client.player.getRotationVec(1).multiply(0.22));
			client.world.addParticle(ParticleTypes.CLOUD, belly.x, belly.y, belly.z, 0, 0.01, 0);
		}
	}

	public static void renderFirstPerson(MatrixStack matrices, VertexConsumerProvider vertices, PlayerEntity player, int light, float tickDelta) {
		float progress = HeadRules.pigSwallowProgress(stuffed(player), tickDelta);
		if (progress >= 1) return;
		var client = MinecraftClient.getInstance();
		if (!client.options.getPerspective().isFirstPerson()) return;
		matrices.push();
		matrices.translate(0.42F - progress * 0.62F, -0.22F + progress * 0.48F, -0.62F + progress * 0.22F);
		float scale = 0.9F * (1 - progress * 0.92F);
		matrices.scale(scale, scale, scale);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(18 + progress * 110));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-18 - progress * 48));
		client.getItemRenderer().renderItem(new ItemStack(HeadItems.item(HeadType.PIG)),
			ModelTransformationMode.FIRST_PERSON_RIGHT_HAND, light, OverlayTexture.DEFAULT_UV,
			matrices, vertices, player.getWorld(), player.getId());
		matrices.pop();
	}

	public static void renderIncoming(MatrixStack matrices, VertexConsumerProvider vertices, PlayerEntity player, int light, float tickDelta) {
		float progress = HeadRules.pigSwallowProgress(stuffed(player), tickDelta);
		if (progress >= 1) return;
		matrices.push();
		matrices.translate(0.28 * (1 - progress), 1.42 - progress * 0.08, 0.32 * (1 - progress));
		float scale = 0.58F * (1 - progress * 0.88F);
		matrices.scale(scale, scale, scale);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(progress * 90));
		MinecraftClient.getInstance().getItemRenderer().renderItem(new ItemStack(HeadItems.item(HeadType.PIG)),
			ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV,
			matrices, vertices, player.getWorld(), player.getId());
		matrices.pop();
	}
}
