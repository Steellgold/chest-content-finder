package fr.steellgold.ccf.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ClientChat {
	private ClientChat() {
	}

	public static void send(Minecraft client, Component message) {
		if (client.player != null) {
			client.player.sendSystemMessage(message);
		}
	}
}