package fr.steellgold.ccf.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import fr.steellgold.ccf.loot.ContainerKind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

final class ContainerFinder {
	private ContainerFinder() {
	}

	static List<BlockPos> findNear(ClientLevel level, BlockPos center, int radius, Set<ContainerKind> kinds) {
		List<BlockPos> found = new ArrayList<>();
		int centerChunkX = center.getX() >> 4;
		int centerChunkZ = center.getZ() >> 4;
		int chunkRadius = (radius >> 4) + 1;

		for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
			for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
				ChunkPos chunkPos = new ChunkPos(centerChunkX + dx, centerChunkZ + dz);

				if (!level.getChunkSource().hasChunk(chunkPos.x(), chunkPos.z())) {
					continue;
				}

				LevelChunk chunk = level.getChunk(chunkPos.x(), chunkPos.z());

				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					if (!(blockEntity instanceof Container)) {
						continue;
					}

					if (!ContainerKind.matchesAny(blockEntity, kinds)) {
						continue;
					}

					BlockPos pos = blockEntity.getBlockPos();

					if (withinRadius(pos, center, radius)) {
						found.add(pos.immutable());
					}
				}
			}
		}

		return found;
	}

	private static boolean withinRadius(BlockPos pos, BlockPos center, int radius) {
		int dx = pos.getX() - center.getX();
		int dz = pos.getZ() - center.getZ();
		return (long) dx * dx + (long) dz * dz <= (long) radius * radius;
	}
}