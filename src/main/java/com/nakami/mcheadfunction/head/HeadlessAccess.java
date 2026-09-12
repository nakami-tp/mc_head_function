package com.nakami.mcheadfunction.head;

import net.minecraft.entity.LivingEntity;

public interface HeadlessAccess {
	boolean mhf$isHeadless();

	void mhf$setHeadless(boolean headless);

	static boolean isHeadless(LivingEntity entity) {
		return entity instanceof HeadlessAccess access && access.mhf$isHeadless();
	}

	static void setHeadless(LivingEntity entity, boolean headless) {
		if (entity instanceof HeadlessAccess access) {
			access.mhf$setHeadless(headless);
		}
	}
}
