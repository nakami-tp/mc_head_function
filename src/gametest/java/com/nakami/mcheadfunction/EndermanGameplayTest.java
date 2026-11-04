package com.nakami.mcheadfunction;

import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.head.PlayerHeadAccess;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

public class EndermanGameplayTest implements FabricGameTest {
    private ServerPlayerEntity wearer(TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        // Expire vanilla join invulnerability before exercising the damage entry point.
        for (int i = 0; i < 80; i++) player.tick();
        player.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.ENDERMAN)));
        var center = context.getAbsolutePos(new BlockPos(10, 2, 10));
        player.refreshPositionAndAngles(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 0, 0);
        // Every point on the eight-block ring is inside solid terrain, regardless of RNG.
        for (int x = 1; x <= 19; x++) for (int z = 1; z <= 19; z++) {
            for (int y = 1; y <= 4; y++) {
                context.setBlockState(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
        context.setBlockState(new BlockPos(10, 2, 10), Blocks.AIR);
        context.setBlockState(new BlockPos(10, 3, 10), Blocks.AIR);
        return player;
    }

    @GameTest(templateName = "mc_head_function:enderman_arena")
    public void dodgeCancelsHitAndCanLandInsideWall(TestContext context) {
        var player = wearer(context);
        var origin = player.getPos();
        float health = player.getHealth();
        player.damage(player.getDamageSources().generic(), 4);
        context.assertTrue(player.getHealth() == health, "Dodge must cancel the triggering hit");
        context.assertTrue(Math.abs(player.getPos().distanceTo(origin) - 8) < 0.001, "Dodge must move eight blocks");
        context.assertTrue(player.getY() == origin.y, "Dodge must preserve height");
        context.assertFalse(context.getWorld().isSpaceEmpty(player), "Random landing must allow a solid wall");
        var state = PlayerHeadAccess.state(player);
        context.assertTrue(state.endermanDodgeCooldown == 40, "Normal cooldown must be two seconds");
        var landing = player.getPos();
        player.damage(player.getDamageSources().generic(), 4);
        context.assertTrue(player.getHealth() < health && player.getPos().equals(landing), "Cooldown must allow damage and prevent another dodge");
        context.complete();
    }

    @GameTest(templateName = "mc_head_function:enderman_arena")
    public void normalDodgeReadyExactlyAfterFortyTicks(TestContext context) {
        verifyCooldown(context, false, 40);
    }

    @GameTest(templateName = "mc_head_function:enderman_arena")
    public void masteredDodgeReadyExactlyAfterTwentyTicks(TestContext context) {
        verifyCooldown(context, true, 20);
    }

    private void verifyCooldown(TestContext context, boolean mastered, int ticks) {
        var player = wearer(context);
        var state = PlayerHeadAccess.state(player);
        if (mastered) state.mastery.put(HeadType.ENDERMAN, 1000);
        player.damage(player.getDamageSources().generic(), 1);
        context.assertTrue(state.endermanDodgeCooldown == ticks, "Wrong initial dodge cooldown");
        var landing = player.getPos();
        for (int i = 0; i < ticks - 1; i++) state.tick(false);
        player.damage(player.getDamageSources().generic(), 1);
        context.assertTrue(player.getPos().equals(landing) && state.endermanDodgeCooldown == 1, "Dodge must stay unavailable until final tick");
        state.tick(false);
        player.timeUntilRegen = 0;
        float health = player.getHealth();
        player.damage(player.getDamageSources().generic(), 1);
        context.assertTrue(player.getHealth() == health && !player.getPos().equals(landing), "Dodge must be available at exact cooldown boundary");
        context.assertTrue(state.endermanDodgeCooldown == ticks, "Successful dodge must restart cooldown");
        context.complete();
    }

    @GameTest(templateName = "mc_head_function:enderman_arena")
    public void suffocationNeverDodgesEvenWhenReady(TestContext context) {
        var player = wearer(context);
        var origin = player.getPos();
        float health = player.getHealth();
        player.damage(player.getDamageSources().inWall(), 1);
        context.assertTrue(player.getHealth() < health, "Suffocation must still hurt");
        context.assertTrue(player.getPos().equals(origin), "Suffocation must not teleport the player out");
        context.assertTrue(PlayerHeadAccess.state(player).endermanDodgeCooldown == 0, "Suffocation must not consume cooldown");
        context.complete();
    }
}
