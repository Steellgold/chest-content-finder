package fr.steellgold.ccf.search;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

final class LocalServer {
	// same radius /locate uses
	private static final int LOCATE_RADIUS = 100;

	private LocalServer() {
	}

	static boolean available(Minecraft client) {
		return client.getSingleplayerServer() != null;
	}

	static void run(Minecraft client, Consumer<ServerPlayer> action) {
		MinecraftServer server = client.getSingleplayerServer();

		if (server == null || client.player == null) {
			return;
		}

		UUID id = client.player.getUUID();
		server.execute(() -> {
			ServerPlayer player = server.getPlayerList().getPlayer(id);

			if (player != null) {
				action.accept(player);
			}
		});
	}

	static BlockPos locate(ServerPlayer player, Identifier structureId) {
		ServerLevel level = player.level();
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Holder.Reference<Structure> holder = registry.get(structureId).orElse(null);

		if (holder == null) {
			return null;
		}

		Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
				.findNearestMapStructure(level, HolderSet.direct(holder), player.blockPosition(), LOCATE_RADIUS, false);
		return found == null ? null : found.getFirst();
	}

	static void teleport(ServerPlayer player, double x, double y, double z) {
		player.teleportTo(player.level(), x, y, z, Set.of(), player.getYRot(), player.getXRot(), true);
	}

	static void teleportLookingAt(ServerPlayer player, double x, double y, double z, BlockPos target) {
		teleport(player, x, y, z);
		player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(target));
	}

	static void setGameMode(ServerPlayer player, GameType mode) {
		player.setGameMode(mode);
	}
}