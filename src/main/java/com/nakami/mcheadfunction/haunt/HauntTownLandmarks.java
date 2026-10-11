package com.nakami.mcheadfunction.haunt;

import java.util.List;

/** Shared town offsets: the graves the scenery builds and the extras climb out of. */
public final class HauntTownLandmarks {
	private HauntTownLandmarks() { }

	public record Spot(int x, int z) {}

	public static final List<Spot> GRAVES = List.of(
		new Spot(-46, -39), new Spot(-46, -34),
		new Spot(-38, -39), new Spot(-38, -34)
	);

	public static final List<Spot> RISES = List.of(
		new Spot(-46, -39), new Spot(-45, -39),
		new Spot(-46, -34), new Spot(-45, -34), new Spot(-46, -33),
		new Spot(-38, -39), new Spot(-37, -39),
		new Spot(-38, -34), new Spot(-37, -34), new Spot(-38, -33)
	);

	/** Just outside the north-yard gate, facing the village. */
	public static final List<Spot> GATE = List.of(
		new Spot(-44, -26), new Spot(-43, -26), new Spot(-42, -26),
		new Spot(-41, -26), new Spot(-40, -26)
	);
}
