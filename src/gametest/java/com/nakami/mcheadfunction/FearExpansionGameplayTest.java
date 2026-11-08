package com.nakami.mcheadfunction;

import com.nakami.mcheadfunction.creeper.CreeperCooking;
import com.nakami.mcheadfunction.creeper.FearSystem;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.MooshroomEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class FearExpansionGameplayTest implements FabricGameTest {
    private ServerPlayerEntity player(TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        var pos = context.getAbsolutePos(new BlockPos(2, 4, 2));
        player.refreshPositionAndAngles(pos.getX(), pos.getY(), pos.getZ(), 180, -70);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.CREEPER_HEAD, 4));
        return player;
    }

    private <T extends MobEntity> T mob(TestContext context, EntityType<T> type) {
        var mob = type.create(context.getWorld());
        var pos = context.getAbsolutePos(new BlockPos(4, 4, 2));
        mob.refreshPositionAndAngles(pos.getX(), pos.getY(), pos.getZ(), 0, 0);
        mob.setAiDisabled(true);
        mob.setNoGravity(true);
        context.getWorld().spawnEntity(mob);
        return mob;
    }

    private long drops(TestContext context, Item item) {
        var center = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(3, 4, 2)));
        return context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(center, center).expand(12),
            entity -> entity.getStack().isOf(item)).stream().mapToLong(entity -> entity.getStack().getCount()).sum();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 130, batchId = "fear-expanded-animals")
    public void selectedAdultAnimalsDropTheirOwnProduceOnlyOnce(TestContext context) {
        var player = player(context);
        mob(context, EntityType.COW);
        mob(context, EntityType.MOOSHROOM);
        var brown = mob(context, EntityType.MOOSHROOM);
        brown.setVariant(MooshroomEntity.Type.BROWN);
        mob(context, EntityType.HORSE);
        mob(context, EntityType.DONKEY);
        mob(context, EntityType.MULE);
        mob(context, EntityType.LLAMA);
        mob(context, EntityType.TRADER_LLAMA);
        var pig = mob(context, EntityType.PIG);
        var rabbit = mob(context, EntityType.RABBIT);
        var cat = mob(context, EntityType.CAT);
        var wolf = mob(context, EntityType.WOLF);
        CreeperCooking.input(player, true);
        context.waitAndRun(65, () -> context.assertTrue(drops(context, Items.WHEAT) == 0,
            "Animals must not reward before level three"));
        context.waitAndRun(84, () -> {
            context.assertTrue(drops(context, Items.WHEAT) == 8, "Each cow and horse-family adult must drop one wheat");
            context.assertTrue(drops(context, Items.RED_MUSHROOM) == 1 && drops(context, Items.BROWN_MUSHROOM) == 1,
                "Mooshrooms must also drop their own mushroom color");
            context.assertTrue(drops(context, Items.CARROT) + drops(context, Items.POTATO) == 1, "Pig must drop one vegetable");
            context.assertTrue(drops(context, Items.RABBIT_HIDE) == 1, "Rabbit must drop one hide");
            context.assertTrue(drops(context, Items.STRING) == 1, "Cat must drop one string");
            context.assertTrue(drops(context, Items.BONE) == 1, "Wolf must drop one bone");
            context.assertTrue(pig.getHealth() == pig.getMaxHealth() && rabbit.getHealth() == rabbit.getMaxHealth()
                && cat.getHealth() == cat.getMaxHealth() && wolf.getHealth() == wolf.getMaxHealth(), "Rewards must not hurt animals");
        });
        context.waitAndRun(94, () -> {
            context.assertTrue(drops(context, Items.WHEAT) == 8 && drops(context, Items.BONE) == 1,
                "Remaining at level three must not duplicate produce");
            player.getInventory().selectedSlot = 1;
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 380, batchId = "fear-enderman-panic")
    public void scaredEndermanDropsItsActualBlockFleesAndCanDropAgainDuringRewardCooldown(TestContext context) {
        for (int x = -12; x <= 24; x++) for (int z = -12; z <= 24; z++) {
            context.setBlockState(new BlockPos(x, 3, z), Blocks.STONE);
            context.setBlockState(new BlockPos(x, 8, z), Blocks.STONE);
        }
        var player = player(context);
        player.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, Items.CARVED_PUMPKIN.getDefaultStack());
        var enderman = mob(context, EntityType.ENDERMAN);
        enderman.setCarriedBlock(Blocks.GRASS_BLOCK.getDefaultState());
        enderman.getRandom().setSeed(42);
        CreeperCooking.input(player, true);
        context.waitAndRun(65, () -> context.assertTrue(enderman.getCarriedBlock() != null,
            "Enderman must keep its block before level three"));
        context.waitAndRun(84, () -> {
            context.assertTrue(enderman.getCarriedBlock() == null && drops(context, Items.GRASS_BLOCK) == 1,
                "Level three must transfer the actual carried block into exactly one item");
            context.assertTrue(enderman.getTarget() == null && !enderman.isAngry(), "Panic must not turn into an attack");
            enderman.setAiDisabled(false);
            enderman.setNoGravity(false);
        });
        context.waitAndRun(96, () -> {
            context.assertTrue(drops(context, Items.GRASS_BLOCK) == 1 && drops(context, Items.ENDER_PEARL) == 0,
                "Panic must not duplicate blocks or generate pearls");
            player.getInventory().selectedSlot = 1;
        });
        context.waitAndRun(120, () -> context.assertTrue(enderman.getPos().subtract(player.getPos()).horizontalLength() > 2.5,
            "Enderman must actually run away during its panic, enderman=" + enderman.getPos() + " player=" + player.getPos()));
        context.waitAndRun(235, () -> {
            context.assertTrue(FearSystem.level(enderman) == 0, "Enderman fear must recover");
            var nbt = new NbtCompound();
            enderman.writeNbt(nbt);
            enderman.discard();
            var restored = EntityType.ENDERMAN.create(context.getWorld());
            restored.readNbt(nbt);
            var pos = context.getAbsolutePos(new BlockPos(4, 4, 2));
            restored.setPosition(Vec3d.of(pos));
            restored.setAiDisabled(true);
            restored.setCarriedBlock(Blocks.MOSS_BLOCK.getDefaultState());
            context.getWorld().spawnEntity(restored);
            player.getInventory().selectedSlot = 0;
            CreeperCooking.input(player, true);
            context.waitAndRun(84, () -> {
                context.assertTrue(restored.getCarriedBlock() == null && drops(context, Items.MOSS_BLOCK) == 1,
                    "Existing carried block must drop even within the persisted bonus reward cooldown");
                player.getInventory().selectedSlot = 1;
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120, batchId = "fear-baby-produce")
    public void selectedBabiesDoNotProduceFearRewards(TestContext context) {
        var player = player(context);
        for (var type : List.<EntityType<? extends PassiveEntity>>of(EntityType.CHICKEN, EntityType.SHEEP,
                EntityType.VILLAGER, EntityType.COW, EntityType.MOOSHROOM, EntityType.PIG, EntityType.HORSE,
                EntityType.DONKEY, EntityType.MULE, EntityType.LLAMA, EntityType.TRADER_LLAMA,
                EntityType.RABBIT, EntityType.CAT, EntityType.WOLF)) {
            mob(context, type).setBaby(true);
        }
        CreeperCooking.input(player, true);
        context.waitAndRun(84, () -> {
            var center = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(3, 4, 2)));
            context.assertTrue(context.getWorld().getEntitiesByClass(ItemEntity.class,
                new Box(center, center).expand(12), item -> true).isEmpty(), "Babies must not produce any fear reward");
            player.getInventory().selectedSlot = 1;
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 1400, batchId = "fear-produce-cooldown")
    public void produceCooldownSurvivesReloadAndExpiresAfterSixtySeconds(TestContext context) {
        var player = player(context);
        var cow = mob(context, EntityType.COW);
        mob(context, EntityType.CAT);
        mob(context, EntityType.WOLF);
        CreeperCooking.input(player, true);
        context.waitAndRun(84, () -> {
            context.assertTrue(drops(context, Items.WHEAT) == 1 && drops(context, Items.STRING) == 1 && drops(context, Items.BONE) == 1,
                "First fear episode must reward all three adults");
            var tag = new NbtCompound();
            cow.writeNbt(tag);
            cow.discard();
            var restored = EntityType.COW.create(context.getWorld());
            restored.readNbt(tag);
            context.getWorld().spawnEntity(restored);
            player.getInventory().selectedSlot = 1;
        });
        context.waitAndRun(235, () -> {
            player.getInventory().selectedSlot = 0;
            CreeperCooking.input(player, true);
        });
        context.waitAndRun(319, () -> {
            context.assertTrue(drops(context, Items.WHEAT) == 1 && drops(context, Items.STRING) == 1 && drops(context, Items.BONE) == 1,
                "A new episode during cooldown must not reward again, including after NBT reload");
            player.getInventory().selectedSlot = 1;
        });
        context.waitAndRun(1285, () -> {
            player.getInventory().selectedSlot = 0;
            CreeperCooking.input(player, true);
        });
        context.waitAndRun(1369, () -> {
            context.assertTrue(drops(context, Items.WHEAT) == 2 && drops(context, Items.STRING) == 2 && drops(context, Items.BONE) == 2,
                "A new episode after sixty seconds must reward again");
            player.getInventory().selectedSlot = 1;
            context.complete();
        });
    }
}
