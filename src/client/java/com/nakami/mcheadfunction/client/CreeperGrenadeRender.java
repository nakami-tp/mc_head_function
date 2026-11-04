package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.entity.ThrownHeadEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.SkullEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** A shared lit skull model for world and hand views; white frames use a plain flash texture. */
public final class CreeperGrenadeRender {
    private static final SkullEntityModel MODEL = new SkullEntityModel(SkullEntityModel.getSkullTexturedModelData().createModel());
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/creeper/creeper.png");
    private static final Identifier WHITE = com.nakami.mcheadfunction.McHeadFunction.id("textures/entity/fuse_white.png");
    private CreeperGrenadeRender() { }
    public static boolean flashing(int fuse) {
        int interval = fuse < 15 ? 3 : fuse < 30 ? 6 : 12;
        return fuse / interval % 2 == 0;
    }
    public static void head(ThrownHeadEntity grenade, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        matrices.push();
        matrices.translate(0, -0.25, 0);
        matrices.scale(1, -1, -1);
        MODEL.setHeadRotation(0, 0, 0);
        boolean flash = flashing(grenade.fuseTicks());
        MODEL.render(matrices, vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(flash ? WHITE : TEXTURE)),
            flash ? 0xF000F0 : light, OverlayTexture.DEFAULT_UV, -1);
        matrices.pop();
    }
    public static void firstPerson(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        if (client.player == null || !client.options.getPerspective().isFirstPerson()) return;
        var grenade = CreeperCookingControl.held(client.player);
        if (grenade == null) return;
        matrices.push();
        float side = client.player.getMainArm() == net.minecraft.util.Arm.RIGHT ? 1 : -1;
        matrices.translate(side * 0.38, -0.22, -0.85);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-side * 15));
        matrices.scale(0.65F, 0.65F, 0.65F);
        head(grenade, matrices, vertices, light);
        matrices.pop();
    }
}
