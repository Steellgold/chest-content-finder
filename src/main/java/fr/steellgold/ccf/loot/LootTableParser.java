package fr.steellgold.ccf.loot;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

final class LootTableParser {
	private static final int MAX_REFERENCE_DEPTH = 8;

	private LootTableParser() {
	}

	static void collect(JsonElement element, Set<Identifier> items, Set<String> references) {
		if (element instanceof JsonArray array) {
			for (JsonElement child : array) {
				collect(child, items, references);
			}

			return;
		}

		if (!(element instanceof JsonObject object)) {
			return;
		}

		String type = asString(object.get("type"));

		if (type != null && type.endsWith("loot_table")) {
			String reference = asString(object.get("value"));

			if (reference != null) {
				references.add(stripNamespace(reference));
			}
		}

		String name = asString(object.get("name"));

		if (name != null) {
			Identifier id = tryParse(name);

			if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
				items.add(id);
			}
		}

		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			collect(entry.getValue(), items, references);
		}
	}

	static Set<Identifier> resolve(String path,
			Map<String, Set<Identifier>> directItems,
			Map<String, Set<String>> references,
			Set<String> visited,
			int depth) {
		if (depth > MAX_REFERENCE_DEPTH || !visited.add(path)) {
			return Set.of();
		}

		Set<Identifier> items = new HashSet<>(directItems.getOrDefault(path, Set.of()));

		for (String reference : references.getOrDefault(path, Set.of())) {
			items.addAll(resolve(reference, directItems, references, visited, depth + 1));
		}

		return items;
	}

	private static String asString(JsonElement element) {
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
				? element.getAsString()
				: null;
	}

	private static String stripNamespace(String reference) {
		int separator = reference.indexOf(':');
		return separator < 0 ? reference : reference.substring(separator + 1);
	}

	private static Identifier tryParse(String raw) {
		try {
			return Identifier.parse(raw);
		} catch (RuntimeException e) {
			return null;
		}
	}
}