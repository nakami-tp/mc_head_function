package com.nakami.mcheadfunction.head;

/** Synchronized frog-mouth link: the wearer stores what it holds, the held mob stores its holder (entity ids, -1 = none). */
public interface FrogMouthAccess {
	int mhf$mouthAmmo();
	void mhf$setMouthAmmo(int kind);
	int mhf$mouthHeld();
	void mhf$setMouthHeld(int id);
	int mhf$mouthHolder();
	void mhf$setMouthHolder(int id);
}
