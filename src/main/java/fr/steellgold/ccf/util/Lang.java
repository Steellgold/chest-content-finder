package fr.steellgold.ccf.util;

import fr.steellgold.ccf.Finder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class Lang {
	private Lang() {
	}

	public static MutableComponent t(String key, Object... args) {
		return Component.translatable(Finder.MOD_ID + "." + key, args);
	}
}