package fr.steellgold.ccf.loot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

public final class StructureCatalog {
	private static final Map<String, List<String>> LOOT_TABLE_TO_STRUCTURES = new LinkedHashMap<>();

	private static final List<String> FALLBACK_STRUCTURES = List.of(
			"ancient_city", "bastion_remnant", "buried_treasure", "desert_pyramid", "end_city",
			"fortress", "igloo", "jungle_pyramid", "mansion", "mineshaft", "mineshaft_mesa",
			"monument", "nether_fossil", "ocean_ruin_cold", "ocean_ruin_warm", "pillager_outpost",
			"ruined_portal", "ruined_portal_desert", "ruined_portal_jungle", "ruined_portal_mountain",
			"ruined_portal_nether", "ruined_portal_ocean", "ruined_portal_swamp", "shipwreck",
			"shipwreck_beached", "stronghold", "swamp_hut", "trail_ruins", "trial_chambers",
			"village_desert", "village_plains", "village_savanna", "village_snowy", "village_taiga"
	);

	static {
		map("chests/ancient_city_ice_box", "ancient_city");
		map("chests/ancient_city", "ancient_city");
		map("chests/bastion_", "bastion_remnant");
		map("chests/stronghold_", "stronghold");
		map("chests/village/", "village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga");
		map("chests/pillager_outpost", "pillager_outpost");
		map("chests/abandoned_mineshaft", "mineshaft", "mineshaft_mesa");
		map("chests/woodland_mansion", "mansion");
		map("chests/underwater_ruin_", "ocean_ruin_cold", "ocean_ruin_warm");
		map("chests/shipwreck_", "shipwreck", "shipwreck_beached");
		map("chests/buried_treasure", "buried_treasure");
		map("chests/desert_pyramid", "desert_pyramid");
		map("chests/jungle_temple", "jungle_pyramid");
		map("chests/igloo_chest", "igloo");
		map("chests/nether_bridge", "fortress");
		map("chests/end_city_treasure", "end_city");
		map("chests/trial_chambers/", "trial_chambers");
		map("chests/ruined_portal", "ruined_portal", "ruined_portal_desert", "ruined_portal_jungle",
				"ruined_portal_mountain", "ruined_portal_nether", "ruined_portal_ocean", "ruined_portal_swamp");
		map("archaeology/trail_ruins_", "trail_ruins");
		map("archaeology/ocean_ruin_", "ocean_ruin_cold", "ocean_ruin_warm");
		map("archaeology/desert_pyramid", "desert_pyramid");
		map("archaeology/desert_well", "desert_well");
	}

	private StructureCatalog() {
	}

	private static void map(String lootTablePrefix, String... structures) {
		List<String> ids = new ArrayList<>(structures.length);

		for (String structure : structures) {
			ids.add(structure);
		}

		LOOT_TABLE_TO_STRUCTURES.put(lootTablePrefix, List.copyOf(ids));
	}

	public static List<Identifier> structuresFor(String lootTablePath) {
		for (Map.Entry<String, List<String>> entry : LOOT_TABLE_TO_STRUCTURES.entrySet()) {
			if (lootTablePath.startsWith(entry.getKey())) {
				List<Identifier> result = new ArrayList<>(entry.getValue().size());

				for (String structure : entry.getValue()) {
					result.add(Identifier.fromNamespaceAndPath("minecraft", structure));
				}

				return result;
			}
		}

		return List.of();
	}

	public static Set<Identifier> allStructures(Minecraft client) {
		Set<Identifier> fromRegistry = fromRegistry(client);

		if (!fromRegistry.isEmpty()) {
			return fromRegistry;
		}

		Set<Identifier> fallback = new LinkedHashSet<>();

		for (String structure : FALLBACK_STRUCTURES) {
			fallback.add(Identifier.fromNamespaceAndPath("minecraft", structure));
		}

		return fallback;
	}

	private static Set<Identifier> fromRegistry(Minecraft client) {
		MinecraftServer server = client.getSingleplayerServer();

		if (server != null) {
			Set<Identifier> ids = keys(server.registryAccess());

			if (!ids.isEmpty()) {
				return ids;
			}
		}

		ClientPacketListener connection = client.getConnection();

		if (connection != null) {
			return keys(connection.registryAccess());
		}

		return Set.of();
	}

	private static Set<Identifier> keys(RegistryAccess access) {
		return access.lookup(Registries.STRUCTURE)
				.map(registry -> new LinkedHashSet<>(registry.keySet()))
				.orElseGet(LinkedHashSet::new);
	}
}