package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.McHeadFunction;
import com.nakami.mcheadfunction.head.GlovePunchAccess;
import com.nakami.mcheadfunction.head.HeadItems;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

/** A padded fist around the actual skin arm, shared by first and third person. */
public final class GloveRenderer {
	private static final Identifier TEXTURE = McHeadFunction.id("textures/entity/beheading_glove.png");
	private static final ModelPart RIGHT = model(Arm.RIGHT);
	private static final ModelPart LEFT = model(Arm.LEFT);

	private GloveRenderer() { }

	private static ModelPart model(Arm arm) {
		float center = arm == Arm.RIGHT ? -1 : 1;
		float thumb = arm == Arm.RIGHT ? center + 1.8F : center - 3.4F;
		ModelData data = new ModelData();
		data.getRoot().addChild("glove", ModelPartBuilder.create()
			.uv(0, 0).cuboid(center - 2.7F, 6.9F, -2.7F, 5.4F, 4.7F, 5.4F)
			.uv(0, 0).cuboid(center - 2.2F, 11.5F, -2.2F, 4.4F, 0.8F, 4.4F)
			.uv(0, 0).cuboid(thumb, 7.1F, -1.7F, 1.6F, 3.4F, 3.5F)
			.uv(0, 24).cuboid(center - 2.35F, 4.8F, -2.35F, 4.7F, 2.3F, 4.7F)
			.uv(0, 48).cuboid(center - 0.8F, 5.3F, -2.55F, 1.6F, 1.2F, 0.3F), ModelTransform.NONE);
		return TexturedModelData.of(data, 64, 64).createModel();
	}

	public static boolean equipped(PlayerEntity player, Arm arm) {
		return (arm == player.getMainArm() ? player.getMainHandStack() : player.getOffHandStack()).isOf(HeadItems.BEHEADING_GLOVE);
	}

	/** Matrices are already in the player's arm-local coordinates. */
	public static void render(Arm arm, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
		(arm == Arm.RIGHT ? RIGHT : LEFT).render(matrices, vertices.getBuffer(RenderLayer.getEntityCutout(TEXTURE)), light, OverlayTexture.DEFAULT_UV);
	}

	/** A short wind-up, explosive straight jab, contact hold, then recovery. */
	public static float extension(float swing) {
		if (swing <= 0 || swing >= 1) return 0;
		if (swing < 0.10F) return 0;
		if (swing < 0.24F) return smooth((swing - 0.10F) / 0.14F);
		if (swing < 0.40F) return 1;
		return 1 - smooth((swing - 0.40F) / 0.60F);
	}

	public static float windup(float swing) {
		if (swing <= 0 || swing >= 0.24F) return 0;
		return swing < 0.10F ? smooth(swing / 0.10F) : 1 - smooth((swing - 0.10F) / 0.14F);
	}

	private static float smooth(float value) {
		return value * value * (3 - 2 * value);
	}

	public static float strength(PlayerEntity player) {
		return ((GlovePunchAccess) player).mhf$glovePunchStage() == 3 ? 1.25F : 1;
	}

	public static void pose(PlayerEntity player, BipedEntityModel<?> model, float delta) {
		for (Arm arm : Arm.values()) {
			if (!equipped(player, arm)) continue;
			Hand hand = arm == player.getMainArm() ? Hand.MAIN_HAND : Hand.OFF_HAND;
			float swing = player.preferredHand == hand ? player.getHandSwingProgress(delta) : 0;
			float strike = extension(swing) * strength(player);
			float windup = windup(swing);
			ModelPart part = arm == Arm.RIGHT ? model.rightArm : model.leftArm;
			part.pitch = -0.65F - 0.95F * strike + 0.18F * windup;
			part.yaw = (arm == Arm.RIGHT ? -1 : 1) * (0.10F - 0.08F * strike);
			part.roll = 0;
			if (model instanceof PlayerEntityModel<?> skin) {
				(arm == Arm.RIGHT ? skin.rightSleeve : skin.leftSleeve).copyTransform(part);
			}
		}
	}
}
