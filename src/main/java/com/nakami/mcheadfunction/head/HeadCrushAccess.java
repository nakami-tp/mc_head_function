package com.nakami.mcheadfunction.head;

/** Short-lived server-authoritative squash, synchronized to every observing client. */
public interface HeadCrushAccess {
	int DURATION_TICKS = 200;
	int mhf$crushTicks();
	void mhf$crush(int ticks);
}
