package com.nakami.mcheadfunction;

import com.nakami.mcheadfunction.behead.BeheadingHandler;
import com.nakami.mcheadfunction.entity.ThrownHeadEntity;
import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.HeadShakeAccess;
import com.nakami.mcheadfunction.head.HeadlessAccess;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;

public final class BeheadingGameplayTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 30)
    public void knockedOffHeadStillCollidesWithWalls(TestContext context) {
        for (int x = 0; x <= 4; x++) for (int z = 0; z <= 5; z++) context.setBlockState(x, 1, z, Blocks.STONE);
        for (int y = 2; y <= 5; y++) context.setBlockState(2, y, 4, Blocks.STONE);
        var target = context.spawnMob(EntityType.COW, 2, 2, 3);
        target.setAiDisabled(true);
        var player = context.createMockCreativeServerPlayerInWorld();
        player.refreshPositionAndAngles(target.getX(), target.getY(), target.getZ() - 1.5, 0, 0);
        player.setHeadYaw(0);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.BEHEADING_GLOVE));
        player.attack(target); player.attack(target); player.attack(target);
        context.waitAndRun(15, () -> {
            var heads = context.getWorld().getEntitiesByClass(ThrownHeadEntity.class, target.getBoundingBox().expand(5), h -> true);
            context.assertTrue(heads.isEmpty(), "Head must stop on wall");
            var drops = context.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class, target.getBoundingBox().expand(5), e -> e.getStack().isOf(HeadItems.item(com.nakami.mcheadfunction.head.HeadType.COW)));
            context.assertTrue(drops.size() == 1 && drops.getFirst().getZ() < target.getZ() + 1, "Head must recycle in front of wall");
            context.assertTrue(target.getHealth() == target.getMaxHealth(), "Own head must not damage body");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 65)
    public void punchedHeadFliesFromVictimBeforeBecomingLoot(TestContext context) {
        var world = context.getWorld();
        for (int x = 0; x <= 5; x++) for (int z = 0; z <= 7; z++) context.setBlockState(x, 1, z, Blocks.STONE);
        var target = context.spawnMob(EntityType.COW, 2, 2, 3);
        target.setAiDisabled(true);
        var player = context.createMockCreativeServerPlayerInWorld();
        player.refreshPositionAndAngles(target.getX(), target.getY(), target.getZ() - 1.5, 0, 0);
        player.setHeadYaw(0);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.BEHEADING_GLOVE));
        player.attack(target); player.attack(target); player.attack(target);
        var heads = world.getEntitiesByClass(ThrownHeadEntity.class, target.getBoundingBox().expand(4), h -> true);
        context.assertTrue(heads.size() == 1, "One flying head expected");
        var head = heads.getFirst();
        var origin = target.getPos();
        context.assertTrue(Math.abs(head.getX()-origin.x) < 0.1 && Math.abs(head.getZ()-origin.z) < 0.1,
            "Head must launch from victim, not player's fist");
        context.waitAndRun(4, () -> {
            context.assertFalse(head.isRemoved(), "Head collided with its own body instead of flying");
            context.assertTrue(head.getZ() - origin.z > 0.8, "Head must visibly fly forward");
            context.assertTrue(head.getY() > origin.y + 0.7, "Head should still be airborne");
        });
        context.waitAndRun(40, () -> {
            context.assertTrue(head.isRemoved(), "Head must become loot after landing");
            var drops = world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, target.getBoundingBox().expand(6), e -> e.getStack().isOf(HeadItems.item(com.nakami.mcheadfunction.head.HeadType.COW)));
            context.assertTrue(drops.size() == 1, "Exactly one recovered head expected");
            var drop = drops.getFirst();
            context.assertTrue(drop.getZ() - origin.z >= 2 && drop.getZ() - origin.z <= 3, "Head must land 2–3 blocks forward: " + (drop.getZ() - origin.z));
            context.assertTrue(drop.getY() >= origin.y && drop.getY() <= origin.y + 0.1, "Recovered head must rest on floor");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void gloveBeheadsTallAndSmallMobsFromBodyHits(TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        player.getAbilities().creativeMode = false;
        for (var type : new EntityType<?>[] {EntityType.ENDERMAN, EntityType.PIG, EntityType.FROG,
                EntityType.BAT, EntityType.BEE, EntityType.IRON_GOLEM}) {
            var target = (net.minecraft.entity.mob.MobEntity) type.create(context.getWorld());
            var pos = context.getAbsolutePos(new net.minecraft.util.math.BlockPos(2, 2, 2));
            target.refreshPositionAndAngles(pos.getX(), pos.getY(), pos.getZ(), 0, 0);
            target.setAiDisabled(true);
            context.getWorld().spawnEntity(target);
            player.refreshPositionAndAngles(target.getX(), target.getY() - 1, target.getZ() - 1.5, 0, 0);
            player.setHeadYaw(0);
            var glove = new ItemStack(HeadItems.BEHEADING_GLOVE);
            player.setStackInHand(Hand.MAIN_HAND, glove);
            float health = target.getHealth();
            player.attack(target);
            context.assertTrue(((HeadShakeAccess) target).mhf$headShakeTicks() > 0, "First punch must shake " + type);
            player.attack(target);
            context.assertFalse(HeadlessAccess.isHeadless(target), "Two punches must not behead " + type);
            context.assertTrue(glove.getDamage() == 0, "Incomplete combo must not consume durability");
            player.attack(target);
            context.assertTrue(HeadlessAccess.isHeadless(target), "Third body punch must behead " + type);
            context.assertTrue(target.getHealth() == health, "Glove damaged " + type);
            context.assertTrue(glove.getDamage() == 1, "One successful combo costs one durability");
            context.assertTrue(((HeadShakeAccess) target).mhf$headShakeTicks() == 0, "Detached head must stop shaking");
            var heads = context.getWorld().getEntitiesByClass(ThrownHeadEntity.class, target.getBoundingBox().expand(8), h -> true);
            context.assertTrue(heads.size() == 1, "Exactly one head must fly off");
            player.attack(target);
            context.assertTrue(context.getWorld().getEntitiesByClass(ThrownHeadEntity.class, target.getBoundingBox().expand(8), h -> true).size() == 1, "Cannot harvest a second head");
            heads.forEach(ThrownHeadEntity::discard);
            target.discard();
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 90)
    public void expiredGloveComboStartsOverAndShakeStops(TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        var target = context.spawnMob(EntityType.PIG, 2, 2, 2);
        target.setAiDisabled(true); target.setNoGravity(true);
        player.refreshPositionAndAngles(target.getX(), target.getY(), target.getZ() - 1, 0, 0);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.BEHEADING_GLOVE));
        player.attack(target); player.attack(target);
        context.waitAndRun(12, () -> context.assertTrue(((HeadShakeAccess) target).mhf$headShakeTicks() == 0, "Shake must expire"));
        context.waitAndRun(42, () -> {
            player.refreshPositionAndAngles(target.getX(), target.getY(), target.getZ() - 1, 0, 0);
            player.attack(target);
            context.assertFalse(HeadlessAccess.isHeadless(target), "Expired hits must not finish combo");
            player.attack(target); player.attack(target);
            context.assertTrue(HeadlessAccess.isHeadless(target), "Fresh three-hit combo must succeed");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void gloveRespectsEligibilityRangeAndWalls(TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        var pig = context.spawnMob(EntityType.PIG, 2, 1, 3);
        pig.setAiDisabled(true);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.BEHEADING_GLOVE));
        player.refreshPositionAndAngles(pig.getX(), pig.getY(), pig.getZ() - 1, 0, 0);
        pig.setBaby(true);
        context.assertFalse(BeheadingHandler.tryShake(player, pig), "Babies must remain ineligible");
        pig.setBaby(false);
        player.refreshPositionAndAngles(pig.getX(), pig.getY(), pig.getZ() - 12, 0, 0);
        context.assertFalse(BeheadingHandler.tryShake(player, pig), "Cannot punch remotely");
        player.refreshPositionAndAngles(pig.getX(), pig.getY(), pig.getZ() - 2, 0, 0);
        for (int y = 1; y <= 3; y++) context.setBlockState(2, y, 2, Blocks.STONE);
        context.assertFalse(BeheadingHandler.tryShake(player, pig), "Cannot punch through a wall");
        for (int y = 1; y <= 3; y++) context.setBlockState(2, y, 2, Blocks.AIR);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
        context.assertFalse(BeheadingHandler.tryShake(player, pig), "Ordinary items must not act as gloves");
        var skeleton = context.spawnMob(EntityType.SKELETON, 2, 1, 3);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.BEHEADING_GLOVE));
        context.assertFalse(BeheadingHandler.tryShake(player, skeleton), "Unsupported mobs must remain ineligible");
        context.complete();
    }
}
