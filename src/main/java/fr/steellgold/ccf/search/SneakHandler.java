package fr.steellgold.ccf.search;

import net.minecraft.client.player.LocalPlayer;

final class SneakHandler {
	enum Action {
		NONE,
		TOGGLE_PAUSE,
		STOP
	}

	private static final int DOUBLE_SNEAK_TICKS = 10;

	private static boolean armed;
	private static boolean held;
	private static int count;
	private static int window;

	private SneakHandler() {
	}

	static void reset() {
		armed = false;
		held = false;
		count = 0;
		window = 0;
	}

	static Action tick(LocalPlayer player) {
		boolean sneaking = player.isShiftKeyDown();

		if (!sneaking) {
			armed = true;
		}

		if (armed && sneaking && !held) {
			count++;
			window = DOUBLE_SNEAK_TICKS;
		}

		held = sneaking;

		if (window > 0 && --window == 0) {
			boolean stop = count >= 2;
			count = 0;
			return stop ? Action.STOP : Action.TOGGLE_PAUSE;
		}

		return Action.NONE;
	}
}