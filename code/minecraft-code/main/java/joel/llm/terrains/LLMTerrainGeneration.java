// Copyright (C) 2025 Joel Sebastian, github.com/JoelSeb
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at:
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package joel.llm.terrains;

import joel.llm.terrains.items.ModItems;
import joel.llm.terrains.items.TerrainGeneratorItem;
import joel.llm.terrains.misc.Coordinate;
import joel.llm.terrains.misc.CoordinatePair;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static joel.llm.terrains.misc.Coordinate.centreToPair;
import static joel.llm.terrains.misc.Coordinate.coordsToCentre;

/**
 * Main class for the LLM Terrain Generation mod.
 * Contains the main logic for the mod's functionality.
 */
public class LLMTerrainGeneration implements ModInitializer {
    public static final String MOD_ID = "llm-terrain-generation";

    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static int currentTerrainOrderIndex, currentTaskOrderIndex, currentTerrainIndex = 0, currentTaskIndex = 0;
    public static Map<Integer, String[]> terrainOrders = generateTerrainOrders();
    public static Map<Integer, Integer[]> taskOrders = generateTaskOrders();
    public static Map<Integer, Integer[]> coordinateMap = generateCoordinates();
    public static String[] terrainsShown = new String[9];
    public static Integer[] taskResults = new Integer[9];
    public static Integer[] highestYs = new Integer[9];

    // Store Previously Generated Terrain Tiles.
    public static final Identifier PREVIOUSLY_GENERATED_TILES = Identifier.of(MOD_ID, "previously_generated_tiles");
    public Set<Coordinate> previouslyGeneratedTiles = new HashSet<>();

    // Store Previously Explored Tiles.
    public static final Identifier PREVIOUSLY_EXPLORED_TILES = Identifier.of(MOD_ID, "previously_explored_tiles");
    public Set<Coordinate> previouslyExploredTiles = new HashSet<>();

    // Store player position from last tick.
    public static final Identifier LAST_PLAYER_POSITION = Identifier.of(MOD_ID, "last_player_position");
    private Coordinate lastPlayerPosition = null;

    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        LOGGER.info("Main File Started!");
        ModItems.initialize();

        // Add default tile to the previously explored tiles set.
        previouslyExploredTiles.add(new Coordinate(128, 0, 128)); // Centre

        // Add default tiles to the previously generated tiles set.
        previouslyGeneratedTiles.add(new Coordinate(128, 0, 128)); // Centre
        previouslyGeneratedTiles.add(new Coordinate(128, 0, 384)); // North
        previouslyGeneratedTiles.add(new Coordinate(128, 0, -128)); // South
        previouslyGeneratedTiles.add(new Coordinate(384, 0, 128)); // East
        previouslyGeneratedTiles.add(new Coordinate(-128, 0, 128)); // West
        previouslyGeneratedTiles.add(new Coordinate(384, 0, 384)); // North-East
        previouslyGeneratedTiles.add(new Coordinate(-128, 0, 384)); // North-West
        previouslyGeneratedTiles.add(new Coordinate(384, 0, -128)); // South-East
        previouslyGeneratedTiles.add(new Coordinate(-128, 0, -128)); // South-West

        // Register tick event that checks if not previously explored tile.
        ServerTickEvents.START_WORLD_TICK.register(world -> {
            // Get the player's position.
            PlayerEntity player = world.getRandomAlivePlayer();
            if (player == null) {
                return;
            }

            BlockPos playerPos = player.getBlockPos();
            Coordinate playerCoords = new Coordinate(playerPos.getX(), 0, playerPos.getZ());
            Coordinate tileCentre = coordsToCentre(playerCoords, 256);

            // Check if the player is in a previously explored tile.
            if (!previouslyExploredTiles.contains(tileCentre) && lastPlayerPosition != null) {
                // Add the player's current tile to the set of previously explored tiles.
                previouslyExploredTiles.add(tileCentre);

                // Check what side of the tile the player crossed into:
                ArrayList<Coordinate> centresToCheck = getCentresToCheck(tileCentre);

                // Check if any of the centres are in the previously generated tiles set:
                for (Coordinate centre : centresToCheck) {
                    if (!previouslyGeneratedTiles.contains(centre)) {
                        // Generate the tile.
                        LOGGER.info("Generating tile at: {}", centre.toXZ());

                        CoordinatePair tileBounds = centreToPair(centre, 256);
                        Path newTileRequest = Path.of("../src/main/tile_requests")
                            .resolve(tileBounds.startCoordinate().toXZ() + "_" + tileBounds.endCoordinate().toXZ()
                        );

                        try {
                            Files.createFile(newTileRequest);
                        } catch (IOException e) {
                            LOGGER.info("Tile already exists: {}", newTileRequest);
                        }
                    }
                }
            }
            // Update the last player position.
            lastPlayerPosition = playerCoords;
        });

        // Register tick event that checks if requested tile has been generated.
        ServerTickEvents.START_WORLD_TICK.register(world -> {
            // Early return if no resolved tile requests in the folder.
            Path tileRequestsFolder = Path.of("../src/main/tile_requests");

            // Get all the files in the tile requests folder.
            try {
                Files.list(tileRequestsFolder).forEach(tileRequest -> {
                    if (tileRequest.toString().contains("requested") || tileRequest.toString().contains("used")) return;

                    Coordinate tileRequestCentre = coordsToCentre(CoordinatePair.fromFileName(tileRequest.getFileName().toString()).startCoordinate(), 256);
                    if (tileRequest.toString().endsWith("resolved") && !previouslyGeneratedTiles.contains(tileRequestCentre)) {
                        // Add the tile to the set of previously generated tiles.
                        previouslyGeneratedTiles.add(tileRequestCentre);

                        // Fill a 256x256 area with grass blocks based on the coordinates in the file name.
                        CoordinatePair tileBounds = CoordinatePair.fromFileName(tileRequest.getFileName().toString());
                        Coordinate start = tileBounds.startCoordinate();
                        Coordinate end = tileBounds.endCoordinate();
                        world.getServer().getCommandManager().executeWithPrefix(
                                world.getServer().getCommandSource(),
                            "/fill " + start.x() + " 105 " + start.z() + " " + end.x() + " 105 " + end.z() + " minecraft:grass_block"
                        );

                        // Read the file and find each of the /generate commands
                        try {
                            List<String> lines = Files.readAllLines(tileRequest);
                            int count = 0;
                            int loadingIncrement = 100 / lines.size();

                            for (String line : lines) {
                                if (line.trim().startsWith("/generate"))
                                    if (!(new TerrainGeneratorItem.GenerationCommand(line, world).execute())) // Invokes the command
                                        LLMTerrainGeneration.LOGGER.error("Error generating feature");
                                PlayerEntity closestPlayer = world.getClosestPlayer(0, 105 ,0, 1000000, false);
                                if (closestPlayer != null) closestPlayer.sendMessage(Text.of("Loading: " + count + "%"));
                                else LLMTerrainGeneration.LOGGER.info("Loading: {}%", count);
                                count += loadingIncrement;
                            }
                        } catch (IOException e) {
                            LLMTerrainGeneration.LOGGER.error("Error reading TXT file");
                        }
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            };

            // Move all resolved tile requests to the resolved folder.
            try {
                Files.list(tileRequestsFolder).forEach(tileRequest -> {
                    if (tileRequest.endsWith("resolved")) {
                        Path resolvedFolder = Path.of("../src/main/tile_requests/used");
                        try {
                            Files.move(tileRequest, resolvedFolder.resolve(tileRequest.getFileName()));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Register the custom commands for the mod
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // Command to wipe the previously generated tiles set.
            dispatcher.register(CommandManager.literal("wipeGeneratedTiles")
                .executes(context -> {
                previouslyGeneratedTiles.clear();
                // Add default tiles to the previously generated tiles set.
                previouslyGeneratedTiles.add(new Coordinate(128, 0, 128)); // Centre
                previouslyGeneratedTiles.add(new Coordinate(128, 0, 384)); // North
                previouslyGeneratedTiles.add(new Coordinate(128, 0, -128)); // South
                previouslyGeneratedTiles.add(new Coordinate(384, 0, 128)); // East
                previouslyGeneratedTiles.add(new Coordinate(-128, 0, 128)); // West
                previouslyGeneratedTiles.add(new Coordinate(384, 0, 384)); // North-East
                previouslyGeneratedTiles.add(new Coordinate(-128, 0, 384)); // North-West
                previouslyGeneratedTiles.add(new Coordinate(384, 0, -128)); // South-East
                previouslyGeneratedTiles.add(new Coordinate(-128, 0, -128)); // South-West
                return 1;
                })
            );

            // Command to wipe the previously explored tiles set.
            dispatcher.register(CommandManager.literal("wipeExploredTiles")
                .executes(context -> {
                previouslyExploredTiles.clear();
                // Add default tile to the previously explored tiles set.
                previouslyExploredTiles.add(new Coordinate(128, 0, 128)); // Centre
                return 1;
                })
            );

            // Command to list the previously generated tiles.
            dispatcher.register(CommandManager.literal("listGeneratedTiles")
                .executes(context -> {
                for (Coordinate tile : previouslyGeneratedTiles) {
                    context.getSource().sendFeedback(() -> Text.literal(tile.toString()).formatted(Formatting.GREEN), false);
                }
                return 1;
                })
            );

            // Command to list the previously explored tiles.
            dispatcher.register(CommandManager.literal("listExploredTiles")
                .executes(context -> {
                for (Coordinate tile : previouslyExploredTiles) {
                    context.getSource().sendFeedback(() -> Text.literal(tile.toString()).formatted(Formatting.GREEN), false);
                }
                return 1;
                })
            );

            // Command to randomize the order of terrains and tasks
            dispatcher.register(CommandManager.literal("randomiseOrders")
                .executes(context -> {
                Random random = context.getSource().getWorld().getRandom();
                // Randomize the order of the terrains and tasks
                currentTaskOrderIndex = random.nextInt(6); // 0-5
                currentTerrainOrderIndex = random.nextInt(24); // 0-23

                // Reset the current terrain and task indices
                currentTerrainIndex = 0;
                currentTaskIndex = 0;

                // Clear the terrains shown and task results
                terrainsShown = new String[9];
                taskResults = new Integer[9];

                context.getSource().sendFeedback(() -> Text.literal("Randomised orders!").formatted(Formatting.GREEN), false);
                return 1;
                })
            );

            // Command to get the current orders of tasks and terrains
            dispatcher.register(CommandManager.literal("getOrders")
                .executes(context -> {
                context.getSource().sendFeedback(() -> Text.literal("currentTaskOrderIndex: " + currentTaskOrderIndex).formatted(Formatting.GREEN), false);
                context.getSource().sendFeedback(() -> Text.literal("currentTerrainOrderIndex: " + currentTerrainOrderIndex).formatted(Formatting.GREEN), false);
                context.getSource().sendFeedback(() -> Text.literal("Indices: " + currentTerrainIndex + " " + currentTaskIndex).formatted(Formatting.GREEN), false);
                return 1;
                })
            );

            // Command to get the coordinates for the current task and terrain
            dispatcher.register(CommandManager.literal("getCoordinates")
                .executes(context -> {
                context.getSource().sendFeedback(() -> Text.literal(getCoordinates()).formatted(Formatting.BLUE), false);
                return 1;
                })
            );

            // Command to teleport players to the coordinates for the current task and terrain
            dispatcher.register(CommandManager.literal("teleportToCoordinates")
                .then(CommandManager.argument("players", EntityArgumentType.players())
                .executes(context -> {
                    final PlayerEntity player = EntityArgumentType.getPlayers(context, "players").stream().findFirst().orElse(null);
                    if (player == null) {
                    context.getSource().sendError(Text.of("No player found!"));
                    return 0;
                    }

                    // Provide player with correct inventory:
                    // Clear current inventory + set gamemode to survival
                    // Give appropriate book for the task
                    // If Iron Mining task, give diamond shovel, pickaxe and blast furnace
                    // If Flag task, give diamond axe and shears
                    // If Trading task, give 5 emeralds
                    context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource(), "/clear " + player.getName().getString());
                    context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource(), "/gamemode survival " + player.getName().getString());

                    switch (taskOrders.get(currentTaskOrderIndex)[currentTaskIndex]) {
                    case 1 -> {
                        context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource(), "/give " + player.getName().getString() + " written_book[writable_book_content={pages:['Mine as much iron and coal as you can, use the blast furnace provided to smelt the iron and produce iron ingots.\n\nHINT: Try mining inside of hills!']}, custom_name=\"Right-Click!\"]");
                        context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource(), "/give " + player.getName().getString() + " minecraft:diamond_shovel");
                        context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource(), "/give " + player.getName().getString() + " minecraft:diamond_pickaxe");
                        context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource(), "/give " + player.getName().getString() + " minecraft:blast_furnace");
                    }
                    case 2 -> {
                        context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource(), "/give " + player.getName().getString() + " written_book[writable_book_content={pages:['Place a flag as high as you can!\n\nHINT: Flags can be made from fences and wool! Craft fences using wood, and obtain wool by shearing sheep!']}, custom_name=\"Right-Click!\"]");
                        context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource(), "/give " + player.getName().getString() + " minecraft:diamond_axe");
                        context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource(), "/give " + player.getName().getString() + " minecraft:shears");
                    }
                    case 3 -> {
                        context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource(), "/give " + player.getName().getString() + " written_book[writable_book_content={pages:['Trade with villagers to get as many emeralds as you can!']}, custom_name=\"Right-Click!\"]");
                        context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource(), "/give " + player.getName().getString() + " minecraft:emerald 5");
                    }
                    }

                    context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource(), "/tp " + player.getName().getString() + " " + getCoordinates());
                    context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource(), "/spawnpoint " + player.getName().getString() + " " + getCoordinates());

                    highestYs[3 * currentTaskIndex + currentTerrainIndex] = getHighestY(context.getSource().getWorld(), player.getBlockPos().getX(), player.getBlockPos().getZ());

                    return 1;
                })
                )
            );

            // Command to increment the indices for the current task and terrain
            dispatcher.register(CommandManager.literal("incrementIndices")
                .executes(context -> {
                // Save the current terrain and task result
                terrainsShown[3 * currentTaskIndex + currentTerrainIndex] = terrainOrders.get(currentTerrainOrderIndex)[currentTerrainIndex];
                taskResults[3 * currentTaskIndex + currentTerrainIndex] = getTaskResult(context.getSource().getWorld().getRandomAlivePlayer());

                if (++currentTerrainIndex == 3) { // Randomize the terrain order and get the next one
                    currentTerrainIndex = 0;
                    currentTerrainOrderIndex = context.getSource().getWorld().getRandom().nextInt(24);

                    if (++currentTaskIndex == 3) {
                    currentTaskIndex = 0;

                    LLMTerrainGeneration.LOGGER.info("Task Order: {}", Arrays.toString(taskOrders.get(currentTaskOrderIndex)));
                    LLMTerrainGeneration.LOGGER.info("Terrain Order: {}", Arrays.toString(terrainsShown));
                    LLMTerrainGeneration.LOGGER.info("Task Results: {}", Arrays.toString(taskResults));

                    // Save experiment data to a file
                    Path experimentData = ModItems.getUniqueFileName(
                        Path.of("../src/main/experiment_data"),
                        "participant",
                        ".txt",
                        false
                    );

                    // Save the task order and terrain order (as Strings)
                    try {
                        LLMTerrainGeneration.LOGGER.info("Writing to file: {}", experimentData);
                        Files.writeString(experimentData, "Task Order: " + Arrays.toString(taskOrders.get(currentTaskOrderIndex)) + "\n", StandardOpenOption.APPEND);
                        Files.writeString(experimentData, "Terrain Order: " + Arrays.toString(terrainsShown) + "\n", StandardOpenOption.APPEND);
                        Files.writeString(experimentData, "Task Results: " + Arrays.toString(taskResults) + "\n\n", StandardOpenOption.APPEND);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    terrainsShown = new String[9];
                    taskResults = new Integer[9];
                    }
                }

                return 1;
                })
            );

            // Command to return the player to the hub
            dispatcher.register(CommandManager.literal("returnToHub")
                .executes(context -> {
                // Clear current inventory + set gamemode to adventure
                context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource(), "/clear @p");
                context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource(), "/gamemode adventure @p");

                // Teleport player to hub and set spawnpoint
                context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource(), "/tp @p 1530 121 1530");
                context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource(), "/spawnpoint @p 1530 121 1530");

                return 1;
                })
            );
        });
    }

    /**
     * Gets the result of a task for a player.
     * The nature of the task changes how the result is calculated.
     * 
     * @param player The player to get the task result for
     * @return The result of the task
     */
    private Integer getTaskResult(PlayerEntity player) {
        return switch (taskOrders.get(currentTaskOrderIndex)[currentTaskIndex]) {
            // Iron Mining: How many iron ingots in inventory?
            case 1 -> player.getInventory().count(Items.IRON_INGOT);

            // Flag: Player's Y position - Highest block in region
            case 2 -> player.getBlockPos().getY() - highestYs[4 * currentTaskIndex + currentTerrainIndex];

            // Trading: How many emeralds in inventory - starting number (5)
            case 3 -> player.getInventory().count(Items.EMERALD) - 5;

            default -> throw new IllegalStateException("Unexpected value: " + taskOrders.get(currentTaskOrderIndex)[currentTaskIndex]);
        };
    }

    // A map of all possible terrain orders.
    public static Map<Integer, String[]> generateTerrainOrders() {
        Map<Integer, String[]> terrainOrders = new HashMap<>();
        terrainOrders.put(0, new String[]{"L1", "L2", "R1"});
        terrainOrders.put(1, new String[]{"L1", "L2", "R2"});
        terrainOrders.put(2, new String[]{"L1", "R1", "R2"});
        terrainOrders.put(3, new String[]{"L1", "R1", "L2"});
        terrainOrders.put(4, new String[]{"L1", "R2", "L2"});
        terrainOrders.put(5, new String[]{"L1", "R2", "R1"});

        terrainOrders.put(6, new String[]{"L2", "L1", "R1"});
        terrainOrders.put(7, new String[]{"L2", "L1", "R2"});
        terrainOrders.put(8, new String[]{"L2", "R1", "R2"});
        terrainOrders.put(9, new String[]{"L2", "R1", "L1"});
        terrainOrders.put(10, new String[]{"L2", "R2", "L1"});
        terrainOrders.put(11, new String[]{"L2", "R2", "R1"});

        terrainOrders.put(12, new String[]{"R1", "L1", "L2"});
        terrainOrders.put(13, new String[]{"R1", "L1", "R2"});
        terrainOrders.put(14, new String[]{"R1", "L2", "R2"});
        terrainOrders.put(15, new String[]{"R1", "L2", "L1"});
        terrainOrders.put(16, new String[]{"R1", "R2", "L1"});
        terrainOrders.put(17, new String[]{"R1", "R2", "L2"});

        terrainOrders.put(18, new String[]{"R2", "L1", "L2"});
        terrainOrders.put(19, new String[]{"R2", "L1", "R1"});
        terrainOrders.put(20, new String[]{"R2", "L2", "R1"});
        terrainOrders.put(21, new String[]{"R2", "L2", "L1"});
        terrainOrders.put(22, new String[]{"R2", "R1", "L1"});
        terrainOrders.put(23, new String[]{"R2", "R1", "L2"});

        return terrainOrders;
    }

    // A map of all possible task orders.
    public static Map<Integer, Integer[]> generateTaskOrders() {
        Map<Integer, Integer[]> taskOrders = new HashMap<>();
        taskOrders.put(0, new Integer[]{1, 2, 3});
        taskOrders.put(1, new Integer[]{1, 3, 2});
        taskOrders.put(2, new Integer[]{2, 1, 3});
        taskOrders.put(3, new Integer[]{2, 3, 1});
        taskOrders.put(4, new Integer[]{3, 1, 2});
        taskOrders.put(5, new Integer[]{3, 2, 1});

        return taskOrders;
    }

    // A map of all possible coordinates for each terrain and task combination.
    public static Map<Integer, Integer[]> generateCoordinates() {
        Map<Integer, Integer[]> coordinateMap = new HashMap<>();
        coordinateMap.put(0, new Integer[]{3075, 114, 6016}); // L1
        coordinateMap.put(1, new Integer[]{5685, 105, 2652}); // L2
        coordinateMap.put(2, new Integer[]{80, 112, -300}); // R1
        coordinateMap.put(3, new Integer[]{-6270, 110, 6047}); // R2

        coordinateMap.put(4, new Integer[]{3210, 106, 6198}); // L1
        coordinateMap.put(5, new Integer[]{6134, 105, 3138}); // L2
        coordinateMap.put(6, new Integer[]{-180, 116, -270}); // R1
        coordinateMap.put(7, new Integer[]{-6116, 118, 5921}); // R2

        coordinateMap.put(8, new Integer[]{2753, 106, 6258}); // L1
        coordinateMap.put(9, new Integer[]{5760, 105, 3000}); // L2
        coordinateMap.put(10, new Integer[]{-235, 115, 198}); // R1
        coordinateMap.put(11, new Integer[]{-6350, 106, 6176}); // R2

        return coordinateMap;
    }

    public static String getCoordinates() {
        int taskIndex = taskOrders.get(currentTaskOrderIndex)[currentTaskIndex] - 1;
        int terrainIndex = terrainToInt(terrainOrders.get(currentTerrainOrderIndex)[currentTerrainIndex]);
        Integer[] coordinates = coordinateMap.get(4 * taskIndex + terrainIndex);

        return coordinates[0] + " " + coordinates[1] + " " + coordinates[2];
    }

    /**
     * Finds the highest Y-coordinate of a block within a 150-block radius from the given position.
     *
     * @param world The world where the search will take place.
     * @param centerX The X-coordinate of the center position.
     * @param centerZ The Z-coordinate of the center position.
     * @return The highest Y-coordinate of a non-air block in a 150-block radius.
     */
    public static int getHighestY(ServerWorld world, int centerX, int centerZ) {
        int highestY = world.getBottomY(); // Start with the lowest Y in the world
        int radius = 150;

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                // Get the highest Y for this column
                int currentHighestY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
                while (currentHighestY >= world.getBottomY()) {
                    BlockPos pos = new BlockPos(x, currentHighestY, z);
                    BlockState blockState = world.getBlockState(pos);

                    if (!blockState.isAir()) {
                        highestY = Math.max(highestY, currentHighestY);
                        break; // No need to keep checking downwards
                    }

                    currentHighestY--;
                }
            }
        }

        return highestY;
    }

    // L1 = 0, L2 = 1, R1 = 2, R2 = 3
    public static int terrainToInt(String terrain) {
        return switch (terrain) {
            case "L2" -> 1;
            case "R1" -> 2;
            case "R2" -> 3;
            default -> 0;
        };
    }

    private ArrayList<Coordinate> getCentresToCheck(Coordinate tileCentre) {
        ArrayList<Coordinate> centresToCheck = new ArrayList<>();
        // From the left.
        if (lastPlayerPosition.x() <= tileCentre.x() - 128) {
            centresToCheck.add(new Coordinate(tileCentre.x() + 256, 0, tileCentre.z()));
            centresToCheck.add(new Coordinate(tileCentre.x() + 256, 0, tileCentre.z() + 256));
            centresToCheck.add(new Coordinate(tileCentre.x() + 256, 0, tileCentre.z() - 256));
        }
        // From the right.
        if (lastPlayerPosition.x() >= tileCentre.x() + 128) {
            centresToCheck.add(new Coordinate(tileCentre.x() - 256, 0, tileCentre.z()));
            centresToCheck.add(new Coordinate(tileCentre.x() - 256, 0, tileCentre.z() + 256));
            centresToCheck.add(new Coordinate(tileCentre.x() - 256, 0, tileCentre.z() - 256));
        }
        // From the bottom.
        if (lastPlayerPosition.z() <= tileCentre.z() - 128) {
            centresToCheck.add(new Coordinate(tileCentre.x(), 0, tileCentre.z() + 256));
            centresToCheck.add(new Coordinate(tileCentre.x() + 256, 0, tileCentre.z() + 256));
            centresToCheck.add(new Coordinate(tileCentre.x() - 256, 0, tileCentre.z() + 256));
        }
        // From the top.
        if (lastPlayerPosition.z() >= tileCentre.z() + 128) {
            centresToCheck.add(new Coordinate(tileCentre.x(), 0, tileCentre.z() - 256));
            centresToCheck.add(new Coordinate(tileCentre.x() + 256, 0, tileCentre.z() - 256));
            centresToCheck.add(new Coordinate(tileCentre.x() - 256, 0, tileCentre.z() - 256));
        }
        return centresToCheck;
    }
}