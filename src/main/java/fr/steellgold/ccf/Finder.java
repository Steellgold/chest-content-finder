package fr.steellgold.ccf;

import fr.steellgold.ccf.command.FindCommand;
import fr.steellgold.ccf.listener.ClientListeners;
import net.fabricmc.api.ClientModInitializer;

public final class Finder implements ClientModInitializer {
	public static final String MOD_ID = "chestcontentfinder";

	@Override
	public void onInitializeClient() {
		FindCommand.register();
		ClientListeners.register();
	}
}