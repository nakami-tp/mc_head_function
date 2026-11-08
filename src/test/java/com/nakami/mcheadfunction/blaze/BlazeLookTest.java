package com.nakami.mcheadfunction.blaze;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlazeLookTest {
	@Test
	void idleHeadStaysDark() {
		assertEquals(BlazeLook.Phase.IDLE, BlazeLook.phase(0, 0));
		assertEquals(0F, BlazeLook.glow(0, 0));
		assertEquals(0F, BlazeLook.ash(0, 0));
		assertEquals(1F, BlazeLook.pulse(0, 0, 12));
	}

	@Test
	void heatingRampsGlowUntilHighHeat() {
		assertEquals(BlazeLook.Phase.HEATING, BlazeLook.phase(20, 0));
		assertEquals(0.5F, BlazeLook.glow(20, 0));
		assertEquals(0F, BlazeLook.ash(20, 0));
		assertEquals(BlazeLook.Phase.HIGH, BlazeLook.phase(40, 0));
		assertEquals(1F, BlazeLook.glow(40, 0));
		assertEquals(BlazeLook.Phase.HIGH, BlazeLook.phase(79, 0));
	}

	@Test
	void overheatTurnsTheHeadAshenThenClears() {
		assertEquals(BlazeLook.Phase.COOLING, BlazeLook.phase(0, 40));
		assertEquals(0F, BlazeLook.glow(0, 40));
		assertEquals(1F, BlazeLook.ash(0, 40));
		assertEquals(1F, BlazeLook.ash(0, 80));
		assertEquals(BlazeLook.Phase.COOLING, BlazeLook.phase(0, 80));
		assertEquals(1F, BlazeLook.ash(0, 160));
		assertEquals(BlazeLook.Phase.COOLING, BlazeLook.phase(0, 160));
		assertEquals(0.5F, BlazeLook.ash(0, 20));
		assertEquals(BlazeLook.Phase.IDLE, BlazeLook.phase(0, 0));
	}

	@Test
	void highHeatPulsesAndOverheatDoesNot() {
		float peak = BlazeLook.pulse(40, 0, (float) (Math.PI / 0.55 / 2));
		float trough = BlazeLook.pulse(40, 0, 0);
		assertTrue(peak > 0.95F);
		assertTrue(trough < 0.65F);
		assertEquals(1F, BlazeLook.pulse(0, 40, 8));
	}
}
