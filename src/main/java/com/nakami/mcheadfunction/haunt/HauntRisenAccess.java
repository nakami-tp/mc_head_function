package com.nakami.mcheadfunction.haunt;

import net.minecraft.entity.LivingEntity;

public interface HauntRisenAccess {
	boolean mhf$hauntDancing();
	void mhf$setHauntDancing(boolean dancing);
	int mhf$hauntRiseTicks();
	void mhf$setHauntRiseTicks(int ticks);

	static boolean dancing(LivingEntity entity) {
		return entity instanceof HauntRisenAccess access && access.mhf$hauntDancing() && access.mhf$hauntRiseTicks() <= 0;
	}
}
