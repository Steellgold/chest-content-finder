package fr.steellgold.ccf.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.steellgold.ccf.loot.LootIndex;
import fr.steellgold.ccf.loot.StructureCatalog;
import fr.steellgold.ccf.search.SearchSession;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;

public final class FindCommand {
	private static final String ITEM_ARGUMENT = "item";
	private static final String STRUCTURE_ARGUMENT = "structure";

	private FindCommand() {
		//
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
			register(dispatcher, "find");
			register(dispatcher, "finder");
		});
	}

	private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, String name) {
		dispatcher.register(node(name));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> node(String name) {
		return ClientCommands.literal(name)
				.requires(FindCommand::isAllowed)
				.then(ClientCommands.literal("stop")
						.executes(context -> {
							SearchSession.stop(Minecraft.getInstance());
							return 1;
						}))
				.then(ClientCommands.literal("pause")
						.executes(context -> {
							SearchSession.pause(Minecraft.getInstance());
							return 1;
						}))
				.then(ClientCommands.literal("resume")
						.executes(context -> {
							SearchSession.resume(Minecraft.getInstance());
							return 1;
						}))
				.then(ClientCommands.argument(ITEM_ARGUMENT, IdentifierArgument.identifier())
						.suggests(itemSuggestions())
						.executes(context -> startEverywhere(context))
						.then(ClientCommands.argument(STRUCTURE_ARGUMENT, IdentifierArgument.identifier())
								.suggests(structureSuggestions())
								.executes(context -> startInStructure(context))));
	}

	private static int startEverywhere(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();

		if (!confirmAttended(source)) {
			return 0;
		}

		Minecraft client = Minecraft.getInstance();

		if (!requireHostWorld(source, client)) {
			return 0;
		}

		Identifier item = IdentifierArgument.get(context, ITEM_ARGUMENT);

		if (!isKnownItem(source, item)) {
			return 0;
		}

		List<Identifier> structures = candidateStructures(client, item);

		if (structures.isEmpty()) {
			source.sendError(Component.literal("No structure is known to contain " + item + "."));
			return 0;
		}

		SearchSession.start(client, item, structures);
		return 1;
	}

	private static int startInStructure(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();

		if (!confirmAttended(source)) {
			return 0;
		}

		Minecraft client = Minecraft.getInstance();

		if (!requireHostWorld(source, client)) {
			return 0;
		}

		Identifier item = IdentifierArgument.get(context, ITEM_ARGUMENT);
		Identifier structure = IdentifierArgument.get(context, STRUCTURE_ARGUMENT);

		if (!isKnownItem(source, item)) {
			return 0;
		}

		if (!StructureCatalog.allStructures(client).contains(structure)) {
			source.sendError(Component.literal("Unknown structure: " + structure));
			return 0;
		}

		if (LootIndex.isAvailable(client) && !LootIndex.structuresContaining(client, item).contains(structure)) {
			source.sendFeedback(Component.literal("Warning: no loot table links " + item + " to " + structure + ".")
					.withStyle(ChatFormatting.YELLOW));
		}

		SearchSession.start(client, item, List.of(structure));
		return 1;
	}

	private static List<Identifier> candidateStructures(Minecraft client, Identifier item) {
		Set<Identifier> matching = LootIndex.structuresContaining(client, item);
		return new ArrayList<>(matching.isEmpty() ? StructureCatalog.allStructures(client) : matching);
	}

	private static SuggestionProvider<FabricClientCommandSource> itemSuggestions() {
		return (context, builder) -> {
			if (builder.getRemaining().isEmpty()) {
				return builder.buildFuture();
			}

			Minecraft client = Minecraft.getInstance();
			Set<Identifier> items = LootIndex.lootItems(client);

			if (items.isEmpty()) {
				items = BuiltInRegistries.ITEM.keySet();
			}

			return SharedSuggestionProvider.suggestResource(items, builder);
		};
	}

	private static SuggestionProvider<FabricClientCommandSource> structureSuggestions() {
		return (context, builder) -> {
			Minecraft client = Minecraft.getInstance();
			Set<Identifier> matching = Set.of();

			try {
				matching = LootIndex.structuresContaining(client, IdentifierArgument.get(context, ITEM_ARGUMENT));
			} catch (IllegalArgumentException e) {
				// item not parsed yet
			}

			return SharedSuggestionProvider.suggestResource(
					matching.isEmpty() ? StructureCatalog.allStructures(client) : matching, builder);
		};
	}

	private static boolean isKnownItem(FabricClientCommandSource source, Identifier item) {
		if (!BuiltInRegistries.ITEM.containsKey(item)) {
			source.sendError(Component.literal("Unknown item: " + item));
			return false;
		}

		Minecraft client = Minecraft.getInstance();

		if (LootIndex.isAvailable(client) && !LootIndex.lootItems(client).contains(item)) {
			source.sendError(Component.literal("No structure loot table drops " + item + "."));
			return false;
		}

		return true;
	}

	private static boolean requireHostWorld(FabricClientCommandSource source, Minecraft client) {
		if (client.getSingleplayerServer() != null) {
			return true;
		}

		source.sendError(Component.literal("This only works in singleplayer, or when you host the LAN world."));
		return false;
	}

	// don't let servers fire this from a clickable chat component (Fabric API 0.152+)
	private static boolean confirmAttended(FabricClientCommandSource source) {
		if (isAttended(source)) {
			return true;
		}

		source.sendError(Component.literal("This command has to be typed manually."));
		return false;
	}

	private static boolean isAttended(FabricClientCommandSource source) {
		try {
			return (boolean) FabricClientCommandSource.class.getMethod("attended").invoke(source);
		} catch (NoSuchMethodException e) {
			return true;
		} catch (ReflectiveOperationException e) {
			return true;
		}
	}

	private static boolean isAllowed(FabricClientCommandSource source) {
		LocalPlayer player = source.getPlayer();
		return player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}
}