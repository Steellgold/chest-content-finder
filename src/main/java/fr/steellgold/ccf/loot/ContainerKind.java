package fr.steellgold.ccf.loot;

import java.util.Locale;
import java.util.Set;

import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

public enum ContainerKind {
	CHEST,
	BARREL,
	SHULKER,
	HOPPER,
	DISPENSER,
	DROPPER;

	static ContainerKind fromLootTablePath(String path) {
		String p = path.toLowerCase(Locale.ROOT);

		if (p.startsWith("archaeology/") || p.startsWith("pots/") || p.contains("vault") || p.contains("reward")) {
			return null;
		}

		if (p.contains("barrel")) {
			return BARREL;
		}

		if (p.contains("dispenser")) {
			return DISPENSER;
		}

		if (p.contains("dropper")) {
			return DROPPER;
		}

		if (p.contains("hopper")) {
			return HOPPER;
		}

		if (p.contains("shulker")) {
			return SHULKER;
		}

		if (p.startsWith("chests/") || p.contains("chest")) {
			return CHEST;
		}

		return null;
	}

	public boolean matches(BlockEntity blockEntity) {
		return switch (this) {
			case CHEST -> blockEntity instanceof ChestBlockEntity;
			case BARREL -> blockEntity instanceof BarrelBlockEntity;
			case SHULKER -> blockEntity instanceof ShulkerBoxBlockEntity;
			case HOPPER -> blockEntity instanceof HopperBlockEntity;
			case DISPENSER -> blockEntity instanceof DispenserBlockEntity
					&& !(blockEntity instanceof DropperBlockEntity);
			case DROPPER -> blockEntity instanceof DropperBlockEntity;
		};
	}

	public static boolean matchesAny(BlockEntity blockEntity, Set<ContainerKind> kinds) {
		if (kinds == null || kinds.isEmpty()) {
			return true;
		}

		for (ContainerKind kind : kinds) {
			if (kind.matches(blockEntity)) {
				return true;
			}
		}

		return false;
	}
}