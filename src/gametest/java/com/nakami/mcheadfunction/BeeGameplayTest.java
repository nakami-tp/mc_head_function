package com.nakami.mcheadfunction;

import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.wear.BeeCommand;
import com.nakami.mcheadfunction.wear.SkillHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

public class BeeGameplayTest implements FabricGameTest {
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void commandPersistsBeyondFifteenSecondsAndStillRecalls(TestContext context) {
		var player = context.createMockCreativeServerPlayerInWorld();
		var pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
		player.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 10);
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.BEE)));
		var target = context.spawnMob(EntityType.COW, 2, 2, 6);
		target.setAiDisabled(true);
		var bee = context.spawnMob(EntityType.BEE, 2, 3, 3);
		bee.addCommandTag("mhf_owner:" + player.getUuid());
		SkillHandler.onSkill(player, true);
		var state = PlayerHeadAccess.state(player);
		context.assertTrue(bee.getTarget() == target, "R must select the visible enemy");
		// Advance the real state/command tick path without waiting a minute of wall time.
		for (int tick = 0; tick < 1200; tick++) {
			state.tick(false);
			BeeCommand.tick(player, HeadType.BEE);
			context.assertTrue(target.getUuid().equals(state.beeTarget) && bee.getTarget() == target,
				"Command must remain active after tick " + (tick + 1));
		}
		SkillHandler.onSkill(player, false);
		player.setPitch(-90);
		SkillHandler.onSkill(player, true);
		context.assertTrue(state.beeTarget == null && bee.getTarget() == null, "R into empty sky must recall bees");
		BeeCommand.command(player, target);
		BeeCommand.tick(player, null);
		context.assertTrue(state.beeTarget == null && bee.getTarget() == null, "Removing the head must cancel command");
		BeeCommand.command(player, target);
		target.setPosition(player.getPos().add(49, 0, 0));
		BeeCommand.tick(player, HeadType.BEE);
		context.assertTrue(state.beeTarget == null && bee.getTarget() == null, "Leaving command range must cancel command");
		target.setPosition(player.getPos().add(0, 0, 4));
		BeeCommand.command(player, target);
		target.setHealth(0);
		BeeCommand.tick(player, HeadType.BEE);
		context.assertTrue(state.beeTarget == null && bee.getTarget() == null, "A dead target must cancel command");
		context.complete();
	}
}
