package com.nakami.mcheadfunction;

import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.wear.SkillHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

public class BlazeGameplayTest implements FabricGameTest {
	private ServerPlayerEntity player(TestContext c) {
		var p = c.createMockCreativeServerPlayerInWorld();
		var pos = c.getAbsolutePos(new BlockPos(2, 2, 2));
		p.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
		p.setNoGravity(true);
		p.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.BLAZE)));
		return p;
	}
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void sprayDealsDirectDamageAndCannotBurnThroughWall(TestContext c) {
		var p = player(c);
		var visible = c.spawnMob(EntityType.COW, 2, 2, 5);
		visible.setAiDisabled(true); visible.setNoGravity(true);
		var hidden = c.spawnMob(EntityType.COW, 2, 2, 7);
		hidden.setAiDisabled(true); hidden.setNoGravity(true);
		for (int x = 0; x < 5; x++) for (int y = 1; y < 6; y++) c.setBlockState(new BlockPos(x, y, 6), Blocks.STONE);
		SkillHandler.onSkill(p, true);
		c.waitAndRun(5, () -> {
			c.assertTrue(visible.getHealth() <= 8, "Spray must immediately deal at least 2 direct damage");
			c.assertFalse(hidden.isOnFire(), "Wall must block ignition as well as particles");
			c.assertTrue(hidden.getHealth() == 10, "Wall must block damage");
			SkillHandler.onSkill(p, false);
			c.complete();
		});
	}
}
