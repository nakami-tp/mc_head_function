package com.nakami.mcheadfunction.rule;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Three glove punches or empty-hand shakes on the same target inside a time window starting with the first hit.
 */
public final class BeheadingTracker {
	private final Map<Key, Window> windows = new HashMap<>();

	/** Returns the accepted hit number (1–3), resetting after completion or timeout. */
	public int registerHit(UUID playerId, UUID targetId, long nowMs) {
		Key key = new Key(playerId, targetId);
		Window window = windows.get(key);
		if (window == null || nowMs - window.firstHitMs > HeadRules.SHAKE_WINDOW_MS) {
			windows.put(key, new Window(nowMs, 1));
			return 1;
		}
		window.hits++;
		if (window.hits >= HeadRules.SHAKE_HITS) {
			windows.remove(key);
			return window.hits;
		}
		return window.hits;
	}

	public void clear(UUID playerId, UUID targetId) {
		windows.remove(new Key(playerId, targetId));
	}

	private record Key(UUID playerId, UUID targetId) {
	}

	private static final class Window {
		final long firstHitMs;
		int hits;

		Window(long firstHitMs, int hits) {
			this.firstHitMs = firstHitMs;
			this.hits = hits;
		}
	}
}
