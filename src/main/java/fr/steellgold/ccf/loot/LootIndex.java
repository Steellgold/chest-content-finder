package fr.steellgold.ccf.loot;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonParser;
import fr.steellgold.ccf.Finder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LootIndex {
	private static final Logger LOGGER = LoggerFactory.getLogger(Finder.MOD_ID);

	private static final String LOOT_TABLE_DIRECTORY = "loot_table";
	private static final String LEGACY_LOOT_TABLE_DIRECTORY = "loot_tables";
	private static final String JSON_SUFFIX = ".json";

	private static Map<Identifier, Set<Identifier>> itemToStructures = Map.of();
	private static Map<Identifier, Set<String>> itemToLootTables = Map.of();
	private static boolean built;

	private LootIndex() {
	}

	public static void invalidate() {
		itemToStructures = Map.of();
		itemToLootTables = Map.of();
		built = false;
	}

	public static Set<Identifier> structuresContaining(Minecraft client, Identifier item) {
		build(client);
		return itemToStructures.getOrDefault(item, Set.of());
	}

	public static Set<Identifier> lootItems(Minecraft client) {
		build(client);
		return itemToStructures.keySet();
	}

	public static Set<ContainerKind> containerKinds(Minecraft client, Identifier item) {
		build(client);
		Set<String> tables = itemToLootTables.getOrDefault(item, Set.of());
		Set<ContainerKind> kinds = new HashSet<>();

		for (String path : tables) {
			ContainerKind kind = ContainerKind.fromLootTablePath(path);

			if (kind == null) {
				return Set.of();
			}

			kinds.add(kind);
		}

		return kinds;
	}

	public static boolean isAvailable(Minecraft client) {
		build(client);
		return !itemToStructures.isEmpty();
	}

	private static void build(Minecraft client) {
		if (built) {
			return;
		}

		built = true;

		MinecraftServer server = client.getSingleplayerServer();

		if (server == null) {
			return;
		}

		try {
			IndexData data = index(server.getResourceManager());
			itemToStructures = data.itemToStructures;
			itemToLootTables = data.itemToLootTables;
		} catch (RuntimeException e) {
			LOGGER.warn("Could not index loot tables, structure filtering is disabled", e);
			itemToStructures = Map.of();
			itemToLootTables = Map.of();
		}
	}

	private static IndexData index(ResourceManager resources) {
		Map<String, Resource> tables = collectLootTables(resources);
		Map<String, Set<Identifier>> directItems = new HashMap<>();
		Map<String, Set<String>> references = new HashMap<>();

		for (Map.Entry<String, Resource> entry : tables.entrySet()) {
			Set<Identifier> items = new HashSet<>();
			Set<String> referenced = new HashSet<>();

			try (BufferedReader reader = entry.getValue().openAsReader()) {
				LootTableParser.collect(JsonParser.parseReader(reader), items, referenced);
			} catch (IOException | RuntimeException e) {
				LOGGER.debug("Skipping unreadable loot table {}", entry.getKey(), e);
				continue;
			}

			directItems.put(entry.getKey(), items);
			references.put(entry.getKey(), referenced);
		}

		Map<Identifier, Set<Identifier>> structures = new HashMap<>();
		Map<Identifier, Set<String>> lootTables = new HashMap<>();

		for (String path : directItems.keySet()) {
			List<Identifier> linked = StructureCatalog.structuresFor(path);

			if (linked.isEmpty()) {
				continue;
			}

			for (Identifier item : LootTableParser.resolve(path, directItems, references, new HashSet<>(), 0)) {
				structures.computeIfAbsent(item, key -> new HashSet<>()).addAll(linked);
				lootTables.computeIfAbsent(item, key -> new HashSet<>()).add(path);
			}
		}

		LOGGER.info("Indexed {} structure loot tables covering {} distinct items", tables.size(), structures.size());
		return new IndexData(structures, lootTables);
	}

	private record IndexData(
			Map<Identifier, Set<Identifier>> itemToStructures,
			Map<Identifier, Set<String>> itemToLootTables
	) {
	}

	private static Map<String, Resource> collectLootTables(ResourceManager resources) {
		Map<String, Resource> tables = new HashMap<>();

		for (String directory : List.of(LOOT_TABLE_DIRECTORY, LEGACY_LOOT_TABLE_DIRECTORY)) {
			String prefix = directory + "/";

			resources.listResources(directory, id -> id.getPath().endsWith(JSON_SUFFIX)).forEach((id, resource) -> {
				String path = id.getPath();
				path = path.substring(prefix.length(), path.length() - JSON_SUFFIX.length());
				tables.putIfAbsent(path, resource);
			});

			if (!tables.isEmpty()) {
				break;
			}
		}

		return tables;
	}
}