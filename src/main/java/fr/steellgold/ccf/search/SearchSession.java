package fr.steellgold.ccf.search;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import fr.steellgold.ccf.loot.LootIndex;
import fr.steellgold.ccf.util.ClientChat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class SearchSession {
	private static final int SEARCH_RADIUS = 100;
	private static final int CHUNK_LOAD_TICKS = 60;
	private static final int APPROACH_TICKS = 10;
	private static final int CONTAINER_OPEN_TICKS = 20;
	private static final int RELOCATE_MIN_DISTANCE = 2000;
	private static final int RELOCATE_RANGE = 4000;

	private static SearchPhase phase = SearchPhase.IDLE;

	private static Identifier wantedItem;
	private static List<Identifier> structurePool;
	private static int structureIndex;
	private static int locateGeneration;

	private static BlockPos structurePos;
	private static final Deque<BlockPos> pendingContainers = new ArrayDeque<>();
	private static BlockPos currentContainer;

	private static int waitTicks;
	private static int containersChecked;
	private static int structuresVisited;

	private static boolean paused;
	private static GameType previousGameMode = GameType.SURVIVAL;

	private SearchSession() {
	}

	public static boolean isRunning() {
		return phase != SearchPhase.IDLE;
	}

	public static boolean isPaused() {
		return paused;
	}

	public static void start(Minecraft client, Identifier item, List<Identifier> structures) {
		LocalPlayer player = client.player;

		if (player == null || structures.isEmpty() || !LocalServer.available(client)) {
			return;
		}

		wantedItem = item;
		structurePool = List.copyOf(structures);
		structureIndex = 0;
		structuresVisited = 0;
		containersChecked = 0;
		pendingContainers.clear();
		currentContainer = null;
		structurePos = null;
		locateGeneration++;

		paused = false;
		SneakHandler.reset();

		previousGameMode = client.gameMode != null ? client.gameMode.getPlayerMode() : GameType.SURVIVAL;
		LocalServer.run(client, serverPlayer -> LocalServer.setGameMode(serverPlayer, GameType.SPECTATOR));

		ClientChat.send(client, Component.literal("Searching for " + item + " in " + structures.size()
				+ (structures.size() == 1 ? " structure." : " structures.")).withStyle(ChatFormatting.GREEN));
		ClientChat.send(client, Component.literal("Sneak to pause, sneak twice to stop.").withStyle(ChatFormatting.GRAY));

		phase = SearchPhase.LOCATE;
	}

	public static void pause(Minecraft client) {
		if (!isRunning()) {
			ClientChat.send(client, Component.literal("No search is running.").withStyle(ChatFormatting.GRAY));
			return;
		}

		if (paused) {
			ClientChat.send(client, Component.literal("The search is already paused.").withStyle(ChatFormatting.GRAY));
			return;
		}

		paused = true;
		ClientChat.send(client, Component.literal("Search paused. Sneak to resume, sneak twice to stop.")
				.withStyle(ChatFormatting.YELLOW));
	}

	public static void resume(Minecraft client) {
		if (!isRunning()) {
			ClientChat.send(client, Component.literal("No search is running.").withStyle(ChatFormatting.GRAY));
			return;
		}

		if (!paused) {
			ClientChat.send(client, Component.literal("The search is not paused.").withStyle(ChatFormatting.GRAY));
			return;
		}

		paused = false;
		ClientChat.send(client, Component.literal("Search resumed.").withStyle(ChatFormatting.GREEN));
	}

	public static void stop(Minecraft client) {
		if (!isRunning()) {
			ClientChat.send(client, Component.literal("No search is running.").withStyle(ChatFormatting.GRAY));
			return;
		}

		restoreGameMode(client);
		reset();
		ClientChat.send(client, Component.literal("Search stopped.").withStyle(ChatFormatting.YELLOW));
	}

	public static void tick(Minecraft client) {
		if (phase == SearchPhase.IDLE) {
			return;
		}

		LocalPlayer player = client.player;
		ClientLevel level = client.level;

		if (player == null || level == null || !LocalServer.available(client)) {
			reset();
			return;
		}

		if (handleSneak(client, player)) {
			return;
		}

		if (paused) {
			return;
		}

		switch (phase) {
			case LOCATE -> locate(client);
			case AWAIT_LOCATE -> { }
			case TELEPORT_TO_STRUCTURE -> teleportToStructure(client);
			case AWAIT_CHUNKS -> { if (--waitTicks <= 0) phase = SearchPhase.SCAN; }
			case SCAN -> scan(client, level);
			case APPROACH_CONTAINER -> approachContainer(client);
			case OPEN_CONTAINER -> openContainer(client, player);
			case READ_CONTAINER -> readContainer(client, player);
			case RELOCATE -> relocate(client, player);
			default -> { }
		}
	}

	private static void locate(Minecraft client) {
		Identifier structure = structurePool.get(structureIndex % structurePool.size());
		structureIndex++;
		structuresVisited++;

		int token = ++locateGeneration;
		phase = SearchPhase.AWAIT_LOCATE;

		LocalServer.run(client, serverPlayer -> {
			BlockPos found = LocalServer.locate(serverPlayer, structure);
			client.execute(() -> onLocated(token, found));
		});
	}

	private static void onLocated(int token, BlockPos found) {
		if (phase != SearchPhase.AWAIT_LOCATE || token != locateGeneration) {
			return;
		}

		if (found == null) {
			phase = SearchPhase.LOCATE;
			return;
		}

		structurePos = found;
		containersChecked = 0;
		phase = SearchPhase.TELEPORT_TO_STRUCTURE;
	}

	private static void teleportToStructure(Minecraft client) {
		LocalServer.run(client, serverPlayer -> {
			LocalServer.setGameMode(serverPlayer, GameType.SPECTATOR);
			LocalServer.teleport(serverPlayer, structurePos.getX() + 0.5, structurePos.getY(), structurePos.getZ() + 0.5);
		});
		waitTicks = CHUNK_LOAD_TICKS;
		phase = SearchPhase.AWAIT_CHUNKS;
	}

	private static void scan(Minecraft client, ClientLevel level) {
		pendingContainers.clear();
		pendingContainers.addAll(ContainerFinder.findNear(
				level, structurePos, SEARCH_RADIUS, LootIndex.containerKinds(client, wantedItem)));

		if (pendingContainers.isEmpty()) {
			phase = SearchPhase.RELOCATE;
			return;
		}

		ClientChat.send(client, Component.literal("Found " + pendingContainers.size() + " container(s) near "
				+ structurePos.toShortString() + ", opening them.").withStyle(ChatFormatting.AQUA));
		phase = SearchPhase.APPROACH_CONTAINER;
	}

	private static void approachContainer(Minecraft client) {
		currentContainer = pendingContainers.poll();

		if (currentContainer == null) {
			LocalServer.run(client, serverPlayer -> LocalServer.setGameMode(serverPlayer, GameType.SPECTATOR));
			phase = SearchPhase.RELOCATE;
			return;
		}

		BlockPos target = currentContainer;
		LocalServer.run(client, serverPlayer -> {
			LocalServer.teleportLookingAt(serverPlayer,
					target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5, target);
			LocalServer.setGameMode(serverPlayer, GameType.CREATIVE);
		});

		waitTicks = APPROACH_TICKS;
		phase = SearchPhase.OPEN_CONTAINER;
	}

	private static void openContainer(Minecraft client, LocalPlayer player) {
		if (--waitTicks > 0) {
			return;
		}

		if (client.gameMode == null) {
			phase = SearchPhase.APPROACH_CONTAINER;
			return;
		}

		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(currentContainer), Direction.UP, currentContainer, false);
		client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);

		waitTicks = CONTAINER_OPEN_TICKS;
		phase = SearchPhase.READ_CONTAINER;
	}

	private static void readContainer(Minecraft client, LocalPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;

		if (menu != null && menu != player.inventoryMenu) {
			boolean found = contains(player, menu, wantedItem);
			closeContainer(client, player);
			containersChecked++;

			if (found) {
				announceFound(client);
			} else {
				phase = SearchPhase.APPROACH_CONTAINER;
			}

			return;
		}

		if (--waitTicks <= 0) {
			phase = SearchPhase.APPROACH_CONTAINER;
		}
	}

	private static boolean contains(LocalPlayer player, AbstractContainerMenu menu, Identifier item) {
		for (Slot slot : menu.slots) {
			if (slot.container == player.getInventory()) {
				continue;
			}

			ItemStack stack = slot.getItem();

			if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item)) {
				return true;
			}
		}

		return false;
	}

	private static void closeContainer(Minecraft client, LocalPlayer player) {
		client.setScreenAndShow(null);

		if (player.containerMenu != player.inventoryMenu) {
			player.closeContainer();
		}
	}

	private static void announceFound(Minecraft client) {
		restoreGameMode(client);

		ClientChat.send(client, Component.literal("Found " + wantedItem + " at " + currentContainer.toShortString()
				+ " after checking " + containersChecked + " container(s) across " + structuresVisited + " structure(s).")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

		reset();
	}

	private static void relocate(Minecraft client, LocalPlayer player) {
		ClientChat.send(client, Component.literal("Nothing here (" + containersChecked
				+ " container(s) checked), moving on.").withStyle(ChatFormatting.GRAY));

		BlockPos origin = structurePos != null ? structurePos : player.blockPosition();
		BlockPos target = origin.offset(randomOffset(player), 0, randomOffset(player));

		LocalServer.run(client, serverPlayer -> {
			LocalServer.setGameMode(serverPlayer, GameType.SPECTATOR);
			LocalServer.teleport(serverPlayer, target.getX(), 200, target.getZ());
		});
		phase = SearchPhase.LOCATE;
	}

	private static int randomOffset(LocalPlayer player) {
		int distance = RELOCATE_MIN_DISTANCE + player.getRandom().nextInt(RELOCATE_RANGE);
		return player.getRandom().nextBoolean() ? distance : -distance;
	}

	private static boolean handleSneak(Minecraft client, LocalPlayer player) {
		SneakHandler.Action action = SneakHandler.tick(player);

		if (action == SneakHandler.Action.STOP) {
			stop(client);
			return true;
		}

		if (action == SneakHandler.Action.TOGGLE_PAUSE) {
			if (paused) {
				resume(client);
			} else {
				pause(client);
			}
		}

		return false;
	}

	private static void restoreGameMode(Minecraft client) {
		LocalServer.run(client, serverPlayer -> LocalServer.setGameMode(serverPlayer, previousGameMode));
	}

	private static void reset() {
		locateGeneration++;
		phase = SearchPhase.IDLE;
		paused = false;
		pendingContainers.clear();
		currentContainer = null;
		structurePos = null;
	}
}