package fr.steellgold.ccf.listener;

import fr.steellgold.ccf.loot.LootIndex;
import fr.steellgold.ccf.search.SearchSession;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class ClientListeners {
	private ClientListeners() {
		//
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SearchSession::tick);

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> LootIndex.invalidate());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LootIndex.invalidate());
	}
}