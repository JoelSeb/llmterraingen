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

import joel.llm.terrains.LLMTerrainGeneration;
import joel.llm.terrains.misc.Coordinate;
import joel.llm.terrains.misc.CoordinatePair;
import joel.llm.terrains.misc.IScreenshotHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An item that allows the player to sample the structure of a specific biome.
 * The item is to be used within single-biome worlds to extract biome data.
 */
public class BiomeSamplerItem extends Item {
    Path regionExampleFolder = Paths.get("../src/main/biome_examples/regions");
    CoordinatePairContainer scannedRegions = new CoordinatePairContainer();

    public BiomeSamplerItem(Settings settings) {
        super(settings);
    }

    /**
     * Called when the player right-clicks with the item in hand.
     * This method is responsible for scanning a region of 256x256x194 blocks and storing the data.
     * It also places blocks in the world to represent the region scanned.
     *
     * @param world The world the item was used in
     * @param user  The player who used the item
     * @param hand  The hand used
     * @return The result of the action
     */

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient) return TypedActionResult.pass(user.getStackInHand(hand));

        BlockPos chunkStartBlockPos = world.getChunk(user.getBlockPos()).getPos().getStartPos();
        CoordinatePair regionCoordinates = getCoordinatePair(chunkStartBlockPos);

        if (scannedRegions.add(regionCoordinates)) {
            user.sendMessage(Text.of("New Region Analysed!"));

            Path newRegionExampleHeightmap = ModItems.getUniqueFileName(
                    Path.of(regionExampleFolder + "/heightmap_data"),
                    world.getBiome(user.getBlockPos()).getIdAsString(),
                    ".png",
                    true
            );

            ModItems.saveHeightmap(
                    newRegionExampleHeightmap,
                    regionCoordinates.startCoordinate().x(),
                    regionCoordinates.startCoordinate().z(),
                    regionCoordinates.endCoordinate().x(),
                    regionCoordinates.endCoordinate().z(),
                    world
            );

            // Schedule a deferred client task to take a screenshot.
            // Get the first player that is not a bot to take the screenshot
            PlayerEntity otherPlayer = world.getPlayers().stream()
                    .filter(player -> !player.getName().getString().contains("Bot"))
                    .findFirst().orElse(null);
            PlayerEntity humanPlayer = otherPlayer != null ? otherPlayer : user;

            humanPlayer.getServer().execute(() -> {
                humanPlayer.setNoGravity(true); // Prevent the player from falling

                // Get the player's current position
                Vec3d playerPos = humanPlayer.getPos();

                // Teleport player to the center of the region
                ModItems.teleportPlayer(
                        world, humanPlayer, false,
                        Optional.of((regionCoordinates.startCoordinate().x() + regionCoordinates.endCoordinate().x()) / 2.0),
                        Optional.of((regionCoordinates.startCoordinate().z() + regionCoordinates.endCoordinate().z()) / 2.0)
                );

                // Chain screenshot after ensuring teleportation
                // Teleport player back to original position
                // Allow the player to fall again
                humanPlayer.getServer().execute(() -> {
                    LLMTerrainGeneration.LOGGER.info("Dispatching screenshot on client thread...");
                    IScreenshotHandler handler = ModItems.getScreenshotHandler();
                    handler.notifyClientTakeScreenshot(humanPlayer);

                    // Teleport player back to original position
                    ModItems.teleportPlayer(world, humanPlayer, false, Optional.of(playerPos.getX()), Optional.of(playerPos.getZ()));

                    humanPlayer.setNoGravity(false); // Allow the player to fall again
                });
            });

            // Region is not within range of any other scanned regions, so scan
            CoordinatePair encasingCoordinates = new CoordinatePair(
                    new Coordinate(regionCoordinates.startCoordinate().x() - 1, 61, regionCoordinates.startCoordinate().z() - 1),
                    new Coordinate(regionCoordinates.endCoordinate().x() + 1, 126, regionCoordinates.endCoordinate().z() + 1)
            );

            // Get edges of the cuboid encasing
            List<CoordinatePair> edges = getEdges(encasingCoordinates);

            // Place blocks at the edges of the cuboid encasing
            for (CoordinatePair edge : edges) {
                BlockPos.stream(
                        edge.startCoordinate().x(), edge.startCoordinate().y(), edge.startCoordinate().z(),
                        edge.endCoordinate().x(), edge.endCoordinate().y(), edge.endCoordinate().z()
                ).forEach(blockPos ->
                        world.setBlockState(blockPos, Blocks.NETHER_BRICKS.getDefaultState(), 3)
                );
            }

            // Save the region data to a CSV and JSON file
            Path newRegionExampleCSV = ModItems.getUniqueFileName(
                    Path.of(regionExampleFolder + "/raw_data"),
                    world.getBiome(user.getBlockPos()).getIdAsString(),
                    ".csv",
                    true
            );
            Path newRegionExampleJSON = ModItems.getUniqueFileName(
                    Path.of(regionExampleFolder + "/rle_data"),
                    world.getBiome(user.getBlockPos()).getIdAsString(),
                    ".txt", // Saving as JSON as .txt so that the LLM can parse it
                    true
            );

            ModItems.saveBlockDataCSV(
                    newRegionExampleCSV,
                    regionCoordinates,
                    world
            );

            ModItems.saveBlockDataRLE(
                    newRegionExampleJSON,
                    regionCoordinates,
                    world
            );
        } else {
            user.sendMessage(Text.of("Region already scanned!"));
        }

        return TypedActionResult.pass(user.getStackInHand(hand));
    }

    /**
     * When the player hits a living entity with the item, clear the scanned regions set.
     *
     * @param stack    The item stack used to hit the entity
     * @param target   The entity being hit
     * @param attacker The entity hitting the target
     * @return Whether the hit was successful
     */
    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        scannedRegions.clear();
        attacker.sendMessage(Text.of("Cleared Region History!"));

        return true;
    }

    /**
     * Get the coordinates of the region to be scanned.
     *
     * @param chunkStartBlockPos The starting block position of the chunk the player is in
     * @return A CoordinatePair representing the region to be scanned
     */
    private static @NotNull CoordinatePair getCoordinatePair(BlockPos chunkStartBlockPos) {
        Coordinate chunkStartCoordinate = new Coordinate(
                chunkStartBlockPos.getX(), chunkStartBlockPos.getY(), chunkStartBlockPos.getZ()
        );

        // We want the region scanned to be 256 blocks wide and 256 blocks long (and 64 high).
        return new CoordinatePair(
                new Coordinate(chunkStartCoordinate.x() - 128, 62, chunkStartCoordinate.z() - 128),
                new Coordinate(chunkStartCoordinate.x() + 127, 125, chunkStartCoordinate.z() + 127)
        );
    }

    /**
     * Get the edges of the cuboid encasing the region to be scanned.
     *
     * @param encasingCoordinates The coordinates of the cuboid encasing the region
     * @return A list of CoordinatePairs representing the edges of the cuboid
     */
    @SuppressWarnings("DuplicatedCode")
    private List<CoordinatePair> getEdges(CoordinatePair encasingCoordinates) {
        List<CoordinatePair> edges = new ArrayList<>();
        Coordinate start = encasingCoordinates.startCoordinate();
        Coordinate end = encasingCoordinates.endCoordinate();

        // Add regular edges to define the cuboid frame
        edges.add(new CoordinatePair(start, new Coordinate(end.x(), start.y(), start.z()))); // x-axis line on bottom face
        edges.add(new CoordinatePair(start, new Coordinate(start.x(), end.y(), start.z()))); // y-axis line along one corner
        edges.add(new CoordinatePair(start, new Coordinate(start.x(), start.y(), end.z()))); // z-axis line on bottom face

        edges.add(new CoordinatePair(new Coordinate(end.x(), start.y(), start.z()), new Coordinate(end.x(), end.y(), start.z()))); // y-axis line along opposite x corner
        edges.add(new CoordinatePair(new Coordinate(end.x(), start.y(), start.z()), new Coordinate(end.x(), start.y(), end.z()))); // z-axis line on far x face

        edges.add(new CoordinatePair(new Coordinate(start.x(), end.y(), start.z()), new Coordinate(end.x(), end.y(), start.z()))); // x-axis line on top face
        edges.add(new CoordinatePair(new Coordinate(start.x(), end.y(), start.z()), new Coordinate(start.x(), end.y(), end.z()))); // z-axis line on top face

        edges.add(new CoordinatePair(new Coordinate(start.x(), start.y(), end.z()), new Coordinate(end.x(), start.y(), end.z()))); // x-axis line on far z face
        edges.add(new CoordinatePair(new Coordinate(start.x(), start.y(), end.z()), new Coordinate(start.x(), end.y(), end.z()))); // y-axis line along far z corner

        edges.add(new CoordinatePair(new Coordinate(end.x(), start.y(), end.z()), new Coordinate(end.x(), end.y(), end.z()))); // y-axis line along far x and z corner
        edges.add(new CoordinatePair(new Coordinate(start.x(), end.y(), end.z()), new Coordinate(end.x(), end.y(), end.z()))); // x-axis line on top far z face

        edges.add(new CoordinatePair(new Coordinate(end.x(), end.y(), start.z()), new Coordinate(end.x(), end.y(), end.z()))); // z-axis line on top far x face

        // Add lines on the top face for grid pattern
        int lineSpacing = 16; // Adjust spacing between each line
        for (int offset = 0; offset <= end.x() - start.x(); offset += lineSpacing) {
            Coordinate lineStart = new Coordinate(start.x() + offset, end.y(), start.z());
            Coordinate lineEnd = new Coordinate(start.x() + offset, end.y(), end.z());
            edges.add(new CoordinatePair(lineStart, lineEnd));
        }
        for (int offset = 0; offset <= end.z() - start.z(); offset += lineSpacing) {
            Coordinate lineStart = new Coordinate(start.x(), end.y(), start.z() + offset);
            Coordinate lineEnd = new Coordinate(end.x(), end.y(), start.z() + offset);
            edges.add(new CoordinatePair(lineStart, lineEnd));
        }

        return edges;
    }

    /**
     * A container for storing CoordinatePairs.
     * This container ensures that any CoordinatePair added to it does not overlap with any existing CoordinatePairs.
     */
    public static class CoordinatePairContainer {
        private final List<CoordinatePair> coordinatePairs;

        public CoordinatePairContainer() {
            this.coordinatePairs = new ArrayList<>();
        }

        /**
         * If the CoordinatePair isn't within range of any existing CoordinatePairs, add it to the container.
         *
         * @param coordinatePair The CoordinatePair to add
         * @return True if the CoordinatePair was added, False if it was not
         */
        public boolean add(CoordinatePair coordinatePair) {
            for (CoordinatePair existingPair : coordinatePairs) {
                if (existingPair.isWithinRange(coordinatePair)) {
                    return false;  // Coordinate is within range, so do not add
                }
            }

            return coordinatePairs.add(coordinatePair);  // Not within range, so add
        }

        public void clear() {
            coordinatePairs.clear();
        }
    }
}