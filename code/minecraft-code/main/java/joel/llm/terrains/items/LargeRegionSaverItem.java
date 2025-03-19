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

import joel.llm.terrains.misc.Coordinate;
import joel.llm.terrains.misc.CoordinatePair;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * An item that allows the player to save the data of a large region (768x64x768 blocks).
 */
public class LargeRegionSaverItem extends Item {
    Path largeTerrainFolder = Paths.get("../src/main/large_terrains");
    CoordinatePairContainer scannedTerrains = new CoordinatePairContainer();

    public LargeRegionSaverItem(Settings settings) {
        super(settings);
    }

    /**
     * Called when the player right-clicks with the item in hand.
     * This method is responsible for scanning a region of 768x64x768 blocks and storing the data.
     * It also places blocks in the world to represent the region scanned.
     * It is used on the block with the lowest x, y and z values.
     *
     * @param context The context which stores:
     *  The world the item was used in
     *  The player who used the item
     * @return The result of the action
     */

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity user = context.getPlayer();
        if (world.isClient || user == null) return ActionResult.SUCCESS;

        BlockPos startBlockPos = context.getBlockPos();
        CoordinatePair regionCoordinates = getCoordinatePair(startBlockPos);

        if (scannedTerrains.add(regionCoordinates)) {
            user.sendMessage(Text.of("New Terrain Analysed!"));

            Path terrainHeightmap = ModItems.getUniqueFileName(
                    Path.of(largeTerrainFolder + "/heightmap_data"),
                    world.getBiome(user.getBlockPos()).getIdAsString(),
                    ".png",
                    true
            );

            Path terrainHeightmapArray = ModItems.getUniqueFileName(
                    Path.of(largeTerrainFolder + "/heightmap_data"),
                    world.getBiome(user.getBlockPos()).getIdAsString(),
                    ".txt",
                    true
            );

            ModItems.saveHeightmap(
                    terrainHeightmap,
                    regionCoordinates.startCoordinate().x(),
                    regionCoordinates.startCoordinate().z(),
                    regionCoordinates.endCoordinate().x(),
                    regionCoordinates.endCoordinate().z(),
                    world
            );

            ModItems.saveHeightmapAsArray(
                    terrainHeightmapArray,
                    regionCoordinates.startCoordinate().x(),
                    regionCoordinates.startCoordinate().z(),
                    regionCoordinates.endCoordinate().x(),
                    regionCoordinates.endCoordinate().z(),
                    world
            );

            // Region is not within range of any other scanned regions, so scan
            CoordinatePair encasingCoordinates = new CoordinatePair(
                    new Coordinate(regionCoordinates.startCoordinate().x() - 1, user.getBlockY() - 1, regionCoordinates.startCoordinate().z() - 1),
                    new Coordinate(regionCoordinates.endCoordinate().x() + 1, user.getBlockY() + 64, regionCoordinates.endCoordinate().z() + 1)
            );

            // Get edges of the cuboid encasing
            List<CoordinatePair> edges = getEdges(encasingCoordinates);

            // Place blocks at the edges of the cuboid encasing
            for (CoordinatePair edge : edges) {
                BlockPos.stream(
                        edge.startCoordinate().x(), edge.startCoordinate().y(), edge.startCoordinate().z(),
                        edge.endCoordinate().x(), edge.endCoordinate().y(), edge.endCoordinate().z()
                ).forEach(blockPos ->
                    world.setBlockState(blockPos, Blocks.AIR.getDefaultState(), 3)
                );
            }

            // Save the region data to a CSV and JSON file
            Path newRegionExampleCSV = ModItems.getUniqueFileName(
                    Path.of(largeTerrainFolder + "/raw_data"),
                    world.getBiome(user.getBlockPos()).getIdAsString(),
                    ".csv",
                    true
            );
            Path newRegionExampleJSON = ModItems.getUniqueFileName(
                    Path.of(largeTerrainFolder + "/rle_data"),
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
        }
        else {
            user.sendMessage(Text.of("Region already scanned!"));
        }

        return ActionResult.SUCCESS;
    }

    /**
     * When the player hits a living entity with the item, clear the scanned regions set.
     *
     * @param stack The item stack used to hit the entity
     * @param target The entity being hit
     * @param attacker The entity hitting the target
     * @return Whether the hit was successful
     */
    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        scannedTerrains.clear();
        attacker.sendMessage(Text.of("Cleared Region History!"));

        return true;
    }

    /**
     * Get the coordinates of the region to be scanned.
     *
     * @param startBlockPos The starting block position of the chunk the player is in
     * @return A CoordinatePair representing the region to be scanned
     */
    public static @NotNull CoordinatePair getCoordinatePair(BlockPos startBlockPos) {
        Coordinate startCoordinate = new Coordinate(
                startBlockPos.getX(), startBlockPos.getY(), startBlockPos.getZ()
        );

        // We want the region scanned to be 768 blocks wide and 768 blocks long.
        return new CoordinatePair(
                new Coordinate(startCoordinate.x(), startBlockPos.getY(), startCoordinate.z()),
                new Coordinate(startCoordinate.x() + 767, startBlockPos.getY() + 63, startCoordinate.z() + 767)
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