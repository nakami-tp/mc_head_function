package com.nakami.mcheadfunction.head;

/** Server-authoritative belly-full ticks, synchronized to every observing client. */
public interface PigStuffedAccess {
	int mhf$stuffedTicks();
	void mhf$setStuffedTicks(int ticks);
}
