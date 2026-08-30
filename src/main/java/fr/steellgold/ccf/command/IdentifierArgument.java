package fr.steellgold.ccf.command;

import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class IdentifierArgument implements ArgumentType<Identifier> {
	private static final DynamicCommandExceptionType INVALID_ID =
			new DynamicCommandExceptionType(raw -> Component.literal("Invalid identifier: " + raw));

	private IdentifierArgument() {
	}

	public static IdentifierArgument identifier() {
		return new IdentifierArgument();
	}

	public static Identifier get(CommandContext<?> context, String name) {
		return context.getArgument(name, Identifier.class);
	}

	@Override
	public Identifier parse(StringReader reader) throws CommandSyntaxException {
		int start = reader.getCursor();

		while (reader.canRead() && isIdentifierChar(reader.peek())) {
			reader.skip();
		}

		String raw = reader.getString().substring(start, reader.getCursor());

		if (raw.isEmpty()) {
			reader.setCursor(start);
			throw INVALID_ID.createWithContext(reader, raw);
		}

		try {
			return Identifier.parse(raw);
		} catch (RuntimeException e) {
			reader.setCursor(start);
			throw INVALID_ID.createWithContext(reader, raw);
		}
	}

	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
		return Suggestions.empty();
	}

	private static boolean isIdentifierChar(char c) {
		return (c >= 'a' && c <= 'z')
				|| (c >= 'A' && c <= 'Z')
				|| (c >= '0' && c <= '9')
				|| c == '_' || c == '-' || c == '.' || c == '/' || c == ':';
	}
}