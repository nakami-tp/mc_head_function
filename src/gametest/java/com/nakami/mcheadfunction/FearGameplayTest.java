package com.nakami.mcheadfunction;

import com.nakami.mcheadfunction.creeper.CreeperCooking;
import com.nakami.mcheadfunction.creeper.FearAccess;
import com.nakami.mcheadfunction.creeper.FearSystem;
import com.nakami.mcheadfunction.entity.ThrownHeadEntity;
import com.nakami.mcheadfunction.throwing.ThrowHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class FearGameplayTest implements FabricGameTest {
    private ServerPlayerEntity player(TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        var p = context.getAbsolutePos(new BlockPos(2, 4, 2));
        player.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), 180, -40);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.CREEPER_HEAD, 4));
        return player;
    }
    private <T extends MobEntity> T mob(TestContext context, EntityType<T> type) {
        T mob = type.create(context.getWorld());
        var p = context.getAbsolutePos(new BlockPos(4, 4, 2));
        mob.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), 0, 0);
        mob.setAiDisabled(true);
        mob.setNoGravity(true);
        context.getWorld().spawnEntity(mob);
        return mob;
    }
    private long drops(TestContext context, Item item) {
        var center = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(3, 4, 2)));
        return context.getWorld().getEntitiesByClass(ItemEntity.class, new net.minecraft.util.math.Box(center, center).expand(12),
            e -> e.getStack().isOf(item)).stream().mapToLong(e -> e.getStack().getCount()).sum();
    }
    private void secondCook(TestContext context, ServerPlayerEntity player) {
        context.waitAndRun(50, () -> {
            CreeperCooking.input(player, false);
            CreeperCooking.input(player, true);
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 90, batchId = "fear-fuse")
    public void cookedFuseContinuesAfterReleaseAndRecoversExactlyOneHead(TestContext context) {
        var player = player(context);
        player.getAbilities().creativeMode = false;
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.CREEPER_HEAD));
        CreeperCooking.input(player, true);
        var grenade = CreeperCooking.held(player);
        context.assertTrue(grenade != null && player.getMainHandStack().isEmpty(), "Ignition must reserve exactly one head");
        CreeperCooking.input(player, true);
        context.assertTrue(CreeperCooking.held(player) == grenade, "Repeated press must not duplicate grenade");
        context.waitAndRun(40, () -> {
            context.assertTrue(grenade.fuseTicks() >= 18 && grenade.fuseTicks() <= 21, "Held fuse must count down");
            CreeperCooking.input(player, false);
            context.assertFalse(grenade.isCooking(), "Release must throw reserved grenade even with empty hand");
            context.assertTrue(grenade.fuseTicks() < 25, "Release must not restart fuse");
            player.setPosition(player.getPos().add(0, 20, 0));
        });
        context.waitAndRun(55, () -> context.assertFalse(grenade.isRemoved(), "Fuse must remain alive before deadline"));
        context.waitAndRun(64, () -> {
            context.assertTrue(grenade.isRemoved(), "Grenade must explode at original deadline");
            context.assertTrue(drops(context, Items.CREEPER_HEAD) == 1, "Exactly one reusable head must remain");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 35, batchId = "fear-switch")
    public void switchingSlotReleasesLitHeadWithoutDefusingAndNbtKeepsFuse(TestContext context) {
        var player = player(context);
        CreeperCooking.input(player, true);
        var grenade = CreeperCooking.held(player);
        context.waitAndRun(10, () -> player.getInventory().selectedSlot = 1);
        context.waitAndRun(15, () -> {
            context.assertFalse(grenade.isCooking(), "Slot switch must release lit head");
            var tag = new NbtCompound();
            grenade.writeNbt(tag);
            var restored = new ThrownHeadEntity(com.nakami.mcheadfunction.entity.ModEntities.THROWN_HEAD, context.getWorld());
            restored.readNbt(tag);
            context.assertTrue(restored.fuseTicks() == grenade.fuseTicks() && !restored.isCooking(), "Reload must preserve remaining fuse without stale reservation");
            grenade.discard();
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 230, batchId = "fear-rewards")
    public void fearProgressesDropsOnceAndRecoversWithoutDamageOrDisarmingSkeleton(TestContext context) {
        var player = player(context);
        var chicken = mob(context, EntityType.CHICKEN);
        var sheep = mob(context, EntityType.SHEEP);
        var villager = mob(context, EntityType.VILLAGER);
        var skeleton = mob(context, EntityType.SKELETON);
        skeleton.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, Items.BOW.getDefaultStack());
        skeleton.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, Items.CARVED_PUMPKIN.getDefaultStack());
        float health = chicken.getHealth();
        double normalSpeed = sheep.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        CreeperCooking.input(player, true);
        secondCook(context, player);
        context.waitAndRun(10, () -> context.assertTrue(FearSystem.level(chicken) == 1, "Initial stimulus must produce level one"));
        context.waitAndRun(38, () -> {
            context.assertTrue(FearSystem.level(chicken) == 2, "Sustained stimulus must produce level two");
            context.assertTrue(sheep.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) < normalSpeed * 0.6, "Level two must slow movement");
        });
        context.waitAndRun(84, () -> {
            context.assertTrue(FearSystem.level(chicken) == 3, "Fear must accumulate across cooking cycles");
            context.assertTrue(drops(context, Items.EGG) == 1, "Chicken must drop one egg");
            context.assertTrue(drops(context, Items.EMERALD) == 1, "Villager must drop one emerald");
            context.assertTrue(drops(context, Items.WHITE_WOOL) >= 1 && sheep.isSheared(), "Sheep must drop wool and become sheared");
            context.assertTrue(skeleton.getMainHandStack().isOf(Items.BOW), "Skeleton must retain bow");
            context.assertTrue(chicken.getHealth() == health, "Fear must not inflict legacy fright damage");
            var tag = new NbtCompound();
            villager.writeNbt(tag);
            var copy = EntityType.VILLAGER.create(context.getWorld());
            copy.readNbt(tag);
            context.assertTrue(((FearAccess) copy).mhf$fear().rewarded, "Reward latch must survive reload");
        });
        context.waitAndRun(96, () -> {
            context.assertTrue(drops(context, Items.EGG) == 1 && drops(context, Items.EMERALD) == 1, "Level three must not repeat drops");
            CreeperCooking.input(player, false);
            player.setPosition(player.getPos().add(0, 30, 0));
        });
        context.waitAndRun(215, () -> {
            context.assertTrue(FearSystem.level(chicken) == 0, "Fear must decay fully after stimulus ends");
            context.assertTrue(Math.abs(sheep.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) - normalSpeed) < 0.0001, "Recovery must remove only fear speed modifier");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 125, batchId = "fear-flee")
    public void frightenedCreeperActuallyRunsAwayThenReturnsToNormal(TestContext context) {
        for (int x = -12; x <= 20; x++) for (int z = -12; z <= 20; z++) context.setBlockState(new BlockPos(x, 2, z), Blocks.STONE);
        var player = player(context);
        var creeper = mob(context, EntityType.CREEPER);
        creeper.setAiDisabled(false);
        creeper.setNoGravity(false);
        double start = creeper.getPos().subtract(player.getPos()).horizontalLength();
        creeper.getRandom().setSeed(42);
        CreeperCooking.input(player, true);
        context.waitAndRun(24, () -> {
            context.assertTrue(creeper.getPos().subtract(player.getPos()).horizontalLength() > start + 0.5, "Fear must increase horizontal distance from player");
            context.assertTrue(creeper.getTarget() == null && !creeper.isIgnited(), "Early fear must suppress attacks and ignition");
            CreeperCooking.input(player, false);
            player.setPosition(player.getPos().add(0, 25, 0));
        });
        context.waitAndRun(110, () -> {
            context.assertTrue(FearSystem.level(creeper) == 0, "Flee state must clear");
            creeper.discard();
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 155, batchId = "fear-retaliation")
    public void thirdLevelCreeperPausesThenIgnitesAndCannotBeScaredOutOfIt(TestContext context) {
        var player = player(context);
        var creeper = mob(context, EntityType.CREEPER);
        CreeperCooking.input(player, true);
        secondCook(context, player);
        context.waitAndRun(80, () -> {
            context.assertTrue(FearSystem.level(creeper) == 3 && FearSystem.retaliating(creeper), "Level three must lock retaliation");
            context.assertFalse(creeper.isIgnited(), "Retaliation must include a visible reaction pause");
        });
        context.waitAndRun(96, () -> {
            context.assertTrue(creeper.isIgnited(), "Creeper must ignite after reaction window");
            context.assertFalse(FearSystem.pacified(creeper), "Retaliating creeper must be allowed to hurt player");
            CreeperCooking.input(player, false);
            CreeperCooking.input(player, true);
        });
        context.waitAndRun(104, () -> context.assertTrue(creeper.isIgnited(), "New cooking must not cancel retaliation"));
        context.waitAndRun(130, () -> {
            context.assertTrue(creeper.isRemoved(), "Ignited creeper must actually self-destruct");
            var pending = CreeperCooking.held(player);
            if (pending != null) pending.discard();
            context.complete();
        });
    }
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 225, batchId = "fear-survival-damage")
    public void retaliationExplosionDamageIsNotSuppressedForSurvivalPlayer(TestContext context) {
        var world = context.getWorld();
        var data = net.minecraft.server.network.ConnectedClientData.createDefault(
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "fear-survival"), false);
        var player = new ServerPlayerEntity(world.getServer(), world, data.gameProfile(), data.syncedOptions());
        var connection = new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
        player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        context.waitAndRun(65, () -> {
            var pos = context.getAbsolutePos(new BlockPos(2, 5, 2));
            player.teleport(world, pos.getX(), pos.getY(), pos.getZ(), 180, -85);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.CREEPER_HEAD, 3));
            var creeper = mob(context, EntityType.CREEPER);
            creeper.setPosition(player.getPos().add(1, 0, 0));
            CreeperCooking.input(player, true);
            context.waitAndRun(50, () -> { CreeperCooking.input(player, false); CreeperCooking.input(player, true); });
            context.waitAndRun(85, () -> {
                context.assertTrue(FearSystem.level(creeper) == 3, "Actual cooked input must reach retaliation");
                context.assertTrue(player.getHealth() == 20, "Ordinary fear stages must not hurt player");
                boolean damaged = player.damage(world.getDamageSources().explosion(creeper, creeper), 5);
                context.assertTrue(damaged && player.getHealth() < 20, "Retaliation explosion damage must bypass fear pacification");
                CreeperCooking.input(player, false);
            });
            context.waitAndRun(135, () -> {
                context.assertTrue(creeper.isRemoved(), "Retaliation must explode");
                context.complete();
            });
        });
    }

}
