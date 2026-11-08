package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.entity.ThrownHeadEntity;
import com.nakami.mcheadfunction.rule.HeadRules;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumers;
import net.minecraft.client.render.entity.model.SkullEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** A shared lit skull model for world and hand views; white frames use a plain flash texture. */
public final class CreeperGrenadeRender {
    private static final SkullEntityModel MODEL = new SkullEntityModel(SkullEntityModel.getSkullTexturedModelData().createModel());
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/creeper/creeper.png");
    private static final Identifier WHITE = com.nakami.mcheadfunction.McHeadFunction.id("textures/entity/fuse_white.png");
    private static final VertexConsumer HIDDEN_VERTICES = VertexConsumers.union(new VertexConsumer[0]);
    private static final VertexConsumerProvider HIDDEN_ARM = layer -> HIDDEN_VERTICES;
    private static final Map<ThrownHeadEntity, Integer> PRIMING_START_AGES = new WeakHashMap<>();
    private CreeperGrenadeRender() { }

    @FunctionalInterface
    public interface ArmRenderer {
        void render(MatrixStack matrices, VertexConsumerProvider vertices, int light, float equip, float swing, net.minecraft.util.Arm arm);
    }

    public static boolean flashing(int fuse) {
        int interval = fuse < 15 ? 3 : fuse < 30 ? 6 : 12;
        return fuse / interval % 2 == 0;
    }

    public static boolean flashing(ThrownHeadEntity grenade) {
        return HeadRules.creeperFuseLit(grenade.fuseTicks(), grenade.isCooking()) && flashing(grenade.fuseTicks());
    }

    public static float primingProgress(ThrownHeadEntity grenade, float tickDelta) {
        if (!grenade.isCooking() || HeadRules.creeperFuseLit(grenade.fuseTicks(), true)) return 1;
        // Network fuse updates arrive between client ticks. Subtracting tickDelta
        // from each packet can rewind the wrist; use one local clock per head.
        int start = PRIMING_START_AGES.computeIfAbsent(grenade,
            head -> head.age - (HeadRules.CREEPER_FUSE_TICKS - head.fuseTicks()));
        return HeadRules.creeperPrimeProgress(HeadRules.CREEPER_FUSE_TICKS - (grenade.age + tickDelta - start), true);
    }

    /** Two wrist shakes with a smooth start and finish, before the fuse lights. */
    public static float ignitionShake(ThrownHeadEntity grenade, float tickDelta) {
        float progress = primingProgress(grenade, tickDelta);
        return progress < 1 ? (float) (Math.sin(progress * Math.PI * 4) * Math.sin(progress * Math.PI)) : 0;
    }

    public static void head(ThrownHeadEntity grenade, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        head(grenade, matrices, vertices, light, 0);
    }

    public static void head(ThrownHeadEntity grenade, MatrixStack matrices, VertexConsumerProvider vertices, int light, float tickDelta) {
        matrices.push();
        matrices.translate(0, -0.25, 0);
        matrices.scale(1, -1, -1);
        MODEL.setHeadRotation(0, 0, 0);
        boolean flash = flashing(grenade);
        MODEL.render(matrices, vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(flash ? WHITE : TEXTURE)),
            flash ? 0xF000F0 : light, OverlayTexture.DEFAULT_UV, -1);
        matrices.pop();
    }

    public static void firstPerson(AbstractClientPlayerEntity player, float tickDelta, MatrixStack matrices,
        VertexConsumerProvider vertices, int light, ArmRenderer arm) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || !client.options.getPerspective().isFirstPerson()) return;
        var grenade = CreeperCookingControl.held(player);
        if (grenade == null) return;
        // Keep vanilla attack swing/equip at rest: they sweep across the HUD.
        float shake = ignitionShake(grenade, tickDelta);
        float side = player.getMainArm() == net.minecraft.util.Arm.RIGHT ? 1 : -1;
        matrices.push();
        matrices.translate(side * 0.6F, -0.65F, -1.1F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 9 * shake));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(4 * shake));
        matrices.translate(-side * 0.6F - side * 0.025F * shake, 0.65F, 1.1F);
        // Vanilla leaves the arm's model-space transform on this stack. Attach at
        // the end of its twelve-pixel forearm, rather than guessing a HUD position.
        // Invisible players still need the palm transform, with their skin hidden.
        arm.render(matrices, player.isInvisible() ? HIDDEN_ARM : vertices, light, 0, 0, player.getMainArm());
        matrices.translate(-side * 0.375F, 0.75F, -0.1F);
        // Undo only the fixed vanilla arm orientation; the wrist shakes remain
        // inherited by both the hand and its held skull.
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 135));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-200));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-side * 120));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-side * 45));
        matrices.translate(-side * 0.025F, 0.13F, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-side * 15));
        matrices.scale(0.65F, 0.65F, 0.65F);
        head(grenade, matrices, vertices, light, tickDelta);
        matrices.pop();
    }
}
