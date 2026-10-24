package com.nakami.mcheadfunction.head;

/** The accepted combo beat, synchronized so nearby players see the same finisher. */
public interface GlovePunchAccess {
	int mhf$glovePunchStage();
	void mhf$glovePunch(int stage);
}
