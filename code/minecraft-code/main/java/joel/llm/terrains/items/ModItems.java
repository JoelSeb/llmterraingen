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

package joel.llm.terrains.items;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import joel.llm.terrains.LLMTerrainGeneration;
import joel.llm.terrains.misc.Coordinate;
import joel.llm.terrains.misc.CoordinatePair;
import joel.llm.terrains.misc.IScreenshotHandler;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ModItems {
    private static IScreenshotHandler screenshotHandler;
    public static final Item AREA_ANALYSER = register(
            new AreaAnalyserItem(new Item.Settings()),
            "area_analyser"
    );
    public static final Item BIOME_SAMPLER = register(
            new BiomeSamplerItem(new Item.Settings()),
            "biome_sampler"
    );
    public static final Item LARGE_REGION_SAVER = register(
            new LargeRegionSaverItem(new Item.Settings()),
            "large_region_saver"
    );
    public static final Item TERRAIN_GENERATOR = register(
            new TerrainGeneratorItem(new Item.Settings()),
            "terrain_generator"
    );
    public static final Item ALTERNATE_TERRAIN_GENERATOR = register(
            new AlternateTerrainGeneratorItem(new Item.Settings()),
            "alternate_terrain_generator"
    );
    public static final Item DEBUG_WAND = register(
            new DebugWandItem(new Item.Settings()),
            "debug_wand"
    );
    public static final Item SCREENSHOT_CAMERA = register(
            new ScreenshotCameraItem(new Item.Settings()),
            "screenshot_camera"
    );

	public static Item register(Item item, String id) {
        // Creates an identifier for the item.
        Identifier itemID = Identifier.of(LLMTerrainGeneration.MOD_ID, id);

        // Registers and returns the registered item.
        return Registry.register(Registries.ITEM, itemID, item);
    }

    public static void initialize() {
        // Get the event for modifying entries in the tools group.
        // And register an event handler that adds the Area Analyser, Biome Sampler and Debug Wand to the tools group.
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(itemGroup -> {
            itemGroup.add(ModItems.AREA_ANALYSER);
            itemGroup.add(ModItems.BIOME_SAMPLER);
            itemGroup.add(ModItems.LARGE_REGION_SAVER);
            itemGroup.add(ModItems.TERRAIN_GENERATOR);
            itemGroup.add(ModItems.ALTERNATE_TERRAIN_GENERATOR);
            itemGroup.add(ModItems.DEBUG_WAND);
            itemGroup.add(ModItems.SCREENSHOT_CAMERA);
        });
    }

    // The method that implements the IScreenshotHandler interface with a class on the client-side.
    public static void setClientUtils(IScreenshotHandler handler) {
        screenshotHandler = handler;
        LLMTerrainGeneration.LOGGER.info("ClientUtils injected successfully, screenshotHandler: {}", screenshotHandler);
    }

    public static BlockPos getRandomTeleportLocation(PlayerEntity user) {
        return new BlockPos(
                user.getRandom().nextBetween(-10000, 10000),
                127,
                user.getRandom().nextBetween(-10000, 10000)
        );
    }

    /**
     * If random is true, the player will be teleported to a random location.
     * Else, the optional x and z coordinates will be used.
     * 
     * Loads the chunk at the target location before teleporting the player.
     * 
     * @param world The world the player is in
     * @param user The player to teleport
     * @param random Whether to teleport to a random location
     * @param x The optional x coordinate
     * @param z The optional z coordinate
     */
    public static void teleportPlayer(World world, PlayerEntity user, boolean random, Optional<Double> x, Optional<Double> z) {
        LLMTerrainGeneration.LOGGER.info("Random Teleportation used by: {}, isClient: {}", user.getName().getString(), world.isClient);

        if (world.isClient) return;

        ServerCommandSource commandSource = user.getCommandSource();

        // Calculate the target position
        double targetX, targetY, targetZ;

        BlockPos targetPos = random ? getRandomTeleportLocation(user) : null;

        targetX = random ?
                targetPos.getX() :
                x.orElseGet(user::getX);
        targetY = 290;
        targetZ = random ?
                targetPos.getZ() :
                z.orElseGet(user::getZ);

        world.getChunkManager().getChunk((int) targetX >> 4, (int) targetZ >> 4, ChunkStatus.FULL, true);

        // Send /tp command to teleport the player
        commandSource.getServer().getCommandManager().executeWithPrefix(
                commandSource,
                String.format("/tp %s %.2f %.2f %.2f 180 90", user.getName().getString(), targetX, targetY, targetZ)
        );

        user.sendMessage(Text.of("Teleported " + user.getName().getString() + " Successfully"));
    }

    /**
     * Helper method to save block data to a CSV file
     *
     * @param path The path to save the file to
     * @param regionCoordinates The pair of coordinates encapsulating the region
     * @param world The world to get the block data from
     */
    public static void saveBlockDataCSV(Path path, CoordinatePair regionCoordinates, World world) {
        Coordinate startCoordinate = regionCoordinates.startCoordinate();
        Coordinate endCoordinate = regionCoordinates.endCoordinate();

        // Create a list to hold the rows of data (Y, Z, X, BlockState)
        List<String> lines = new ArrayList<>();
        BlockPos.stream(
                startCoordinate.x(), startCoordinate.y(), startCoordinate.z(),
                endCoordinate.x(), endCoordinate.y(), endCoordinate.z()
        ).forEach(blockPos ->
            lines.add(blockPos.getY() + "," + blockPos.getZ() + "," + blockPos.getX() + "," + world.getBlockState(blockPos))
        );

        // Write the data to the file
        try {
            Files.write(path, lines);
        } catch (IOException e) {
            LLMTerrainGeneration.LOGGER.error("Failed to write to {}, current directory is {}", path, System.getProperty("user.dir"));
            LLMTerrainGeneration.LOGGER.error(e.getMessage());
        }
    }

    public static void saveBlockDataRLE(Path path, CoordinatePair regionCoordinates, World world) {
        Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        Coordinate startCoordinate = regionCoordinates.startCoordinate();
        Coordinate endCoordinate = regionCoordinates.endCoordinate();

        // Define the structure with start first
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Integer> start = new LinkedHashMap<>();  // Use LinkedHashMap for start to control order
        start.put("x", startCoordinate.x());
        start.put("y", startCoordinate.y());
        start.put("z", startCoordinate.z());
        data.put("start", start);  // start will maintain the "x", "y", "z" order

        // Mappings for unique symbols to block names
        List<Map<String, String>> mappings = new ArrayList<>();
        Map<String, String> symbolMapping = new HashMap<>();
        Set<String> usedSymbols = new HashSet<>();

        // Generate grids for each Y layer with run-length encoding
        List<Map<String, Object>> grids = new ArrayList<>();

        for (int y = startCoordinate.y(); y <= endCoordinate.y(); y++) {
            List<List<String>> grid = new ArrayList<>();

            for (int z = startCoordinate.z(); z <= endCoordinate.z(); z++) {
                StringBuilder encodedRow = new StringBuilder();

                int runLength = 1;  // Track run length of the current symbol
                String lastSymbol = null;

                for (int x = startCoordinate.x(); x <= endCoordinate.x(); x++) {
                    BlockState blockState = world.getBlockState(new BlockPos(x, y, z));
                    String blockName = blockState.getBlock().toString();

                    // Remove the "Block{}" wrapper by substring
                    if (blockName.startsWith("Block{") && blockName.endsWith("}"))
                        blockName = blockName.substring(6, blockName.length() - 1); // Extract "minecraft:block_name"

                    String symbol = symbolMapping.computeIfAbsent(blockName, name -> {
                        String newSymbol = generateUniqueSymbol(name, usedSymbols);
                        usedSymbols.add(newSymbol);
                        mappings.add(Map.of("symbol", newSymbol, "block", name));
                        return newSymbol;
                    });

                    // Run-length encode: check if the current symbol matches the previous
                    if (symbol.equals(lastSymbol)) {
                        runLength++;
                    } else {
                        if (lastSymbol != null) encodedRow.append(runLength).append(lastSymbol);
                        runLength = 1;
                        lastSymbol = symbol;
                    }
                }

                // Append the final run of the row
                if (lastSymbol != null) encodedRow.append(runLength).append(lastSymbol);

                grid.add(List.of(encodedRow.toString()));  // Add encoded row to grid
            }
            grids.add(Map.of("y", y, "grid", grid));  // Add the grid to grids list for this y level
        }

        // Add mappings and grids to the data
        data.put("mappings", mappings);  // Mappings added after start
        data.put("grids", grids);

        // Write the JSON data to the file
        try {
            Files.writeString(path, GSON.toJson(data));
        } catch (IOException e) {
            LLMTerrainGeneration.LOGGER.error("JSON Mode: Failed to write to {}, current directory is {}", path, System.getProperty("user.dir"));
            LLMTerrainGeneration.LOGGER.error(e.getMessage());
        }
    }

    /**
     * Helper method to save a heightmap of the world to a PNG file
     *
     * @param path The path to save the file to
     * @param startX The starting X coordinate
     * @param startZ The starting Z coordinate
     * @param endX The ending X coordinate
     * @param endZ The ending Z coordinate
     * @param world The world to get the heightmap data from
     */
    public static void saveHeightmap(Path path, int startX, int startZ, int endX, int endZ, World world) {
        int width = endX - startX + 1;
        int height = endZ - startZ + 1;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        // Determine the max and min height for normalization
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;

        // First pass: calculate min and max heights
        for (int chunkX = startX >> 4; chunkX <= endX >> 4; chunkX++) {
            for (int chunkZ = startZ >> 4; chunkZ <= endZ >> 4; chunkZ++) {
                // Force chunk loading and ensure generation
                world.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);

                // Process all blocks within the current chunk
                for (int x = Math.max(startX, chunkX << 4); x <= Math.min(endX, (chunkX << 4) + 15); x++) {
                    for (int z = Math.max(startZ, chunkZ << 4); z <= Math.min(endZ, (chunkZ << 4) + 15); z++) {
                        int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
                        minHeight = Math.min(minHeight, y);
                        maxHeight = Math.max(maxHeight, y);
                    }
                }
            }
        }

        // Second pass: fill the heightmap image
        for (int chunkX = startX >> 4; chunkX <= endX >> 4; chunkX++) {
            for (int chunkZ = startZ >> 4; chunkZ <= endZ >> 4; chunkZ++) {
                // Force chunk loading and ensure generation
                world.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);

                // Process all blocks within the current chunk
                for (int x = Math.max(startX, chunkX << 4); x <= Math.min(endX, (chunkX << 4) + 15); x++) {
                    for (int z = Math.max(startZ, chunkZ << 4); z <= Math.min(endZ, (chunkZ << 4) + 15); z++) {
                        int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);

                        // Normalize the height value to grayscale (0-255)
                        int normalizedHeight = (int) ((y - minHeight) / (double) (maxHeight - minHeight) * 255);
                        int grayValue = (normalizedHeight << 16) | (normalizedHeight << 8) | normalizedHeight;

                        // Set the pixel value in the image
                        image.setRGB(x - startX, z - startZ, grayValue);
                    }
                }
            }
        }

        // Write the image to file
        try {
            ImageIO.write(image, "png", path.toFile());
            System.out.println("Heightmap saved to: " + path);
        } catch (IOException e) {
            LLMTerrainGeneration.LOGGER.error("Failed to write heightmap to {}", path);
        }
    }

    public static void saveHeightmapAsArray(Path path, int startX, int startZ, int endX, int endZ, World world) {
        // Create a list to hold the rows of data (Y, Z, X, BlockState)
        List<String> lines = new ArrayList<>();

        int minHeight = Integer.MAX_VALUE;

        // Find the minimum height in the region
        for (int chunkX = startX >> 4; chunkX <= endX >> 4; chunkX++) {
            for (int chunkZ = startZ >> 4; chunkZ <= endZ >> 4; chunkZ++) {
                // Force chunk loading and ensure generation
                world.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);

                // Process all blocks within the current chunk
                for (int x = Math.max(startX, chunkX << 4); x <= Math.min(endX, (chunkX << 4) + 15); x++) {
                    for (int z = Math.max(startZ, chunkZ << 4); z <= Math.min(endZ, (chunkZ << 4) + 15); z++) {
                        int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
                        minHeight = Math.min(minHeight, y);
                    }
                }
            }
        }

        // Loop over the Z-axis first to ensure row-major order (top-to-bottom)
        for (int z = startZ; z <= endZ; z++) {
            StringBuilder line = new StringBuilder();

            for (int x = startX; x <= endX; x++) {
                // Load and generate the chunk if necessary
                world.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);

                // Get the height at the current x, z position
                int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);

                // Append the height value to the current line
                // Subtracting the minimum height so just displacements are saved
                line.append(y - minHeight).append(",");
            }

            // Add the completed row to the list
            lines.add(line.toString());
        }

        // Write the data to the file
        try {
            Files.write(path, lines);
        } catch (IOException e) {
            LLMTerrainGeneration.LOGGER.error("Failed to write heightmap to {}", path);
        }
    }

    /**
     * Helper method to save a screenshot of the player's POV
     *
     * @param world The world the player is in
     * @param player The player to take the screenshot
     */
    public static void saveScreenshot(World world, PlayerEntity player) {
        if (!world.isClient) return;

        IScreenshotHandler handler = ModItems.getScreenshotHandler();
        try {
            handler.saveScreenshot(world, player);
        } catch (Exception e) {
            e.printStackTrace();
            LLMTerrainGeneration.LOGGER.error("Failed to write screenshot");
        }
    }

    /**
     * Generates a unique symbol for a block name, attempting to use its initial letters
     * until an unused symbol is found.
     *
     * @param blockName The name of the block
     *                  (e.g. "minecraft:stone")
     * @param usedSymbols A set of symbols that have already been used
     *                    (e.g. ["S", "ST", "G"])
     */
    private static String generateUniqueSymbol(String blockName, Set<String> usedSymbols) {
        // Extract the block name after "minecraft:"
        String baseName = blockName.contains(":") ? blockName.split(":")[1] : blockName;

        // Generate unique symbols by increasing length
        for (int length = 1; length <= baseName.length(); length++) {
            String symbol = baseName.substring(0, length).toUpperCase();
            if (!usedSymbols.contains(symbol)) {
                return symbol;
            }
        }

        // Fallback to adding special characters if all combinations of initials are used
        Character[] specialChars = {'!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '-', '_', '=', '+'};
        for (Character specialChar : specialChars) {
            String symbol = baseName.substring(0, 1).toUpperCase() + specialChar;
            if (!usedSymbols.contains(symbol)) {
                return symbol;
            }
        }

        return "X";  // Fallback to "X" if all combinations are used
    }

    /**
     * Helper method to generate a unique file based on existing file names
     *
     * @param directory The directory to save the file in
     * @param baseName The base name of the file
     * @return A unique file path
     */
    public static Path getUniqueFileName(Path directory, String baseName, String extension, boolean shorten) {
        int counter = 1;
        Path filePath;

        // Remove "minecraft:" from start of baseName
        if (shorten) baseName = baseName.substring(10);

        // Loop until a unique file name is found
        do {
            filePath = directory.resolve(baseName + "_" + counter + extension);
            counter++;
        } while (Files.exists(filePath));

        return filePath;
    }

    public static IScreenshotHandler getScreenshotHandler() {
        return screenshotHandler;
    }
}
