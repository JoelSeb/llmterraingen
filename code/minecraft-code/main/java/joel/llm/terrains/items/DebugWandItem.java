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
import joel.llm.terrains.misc.CoordinatePair;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static joel.llm.terrains.items.LargeRegionSaverItem.getCoordinatePair;

/**
 * A debug item that allows the player to manipulate the world.
 * Used to test experimental functionality.
 * Was used for teleporting when obtaining 300 screenshots.
 */
public class DebugWandItem extends Item {
    public DebugWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient) return TypedActionResult.pass(user.getStackInHand(hand));

        // Save heightmap data if the player has a nether star in their inventory
        if (user.getInventory().contains(Items.NETHER_STAR.getDefaultStack())) {
            user.sendMessage(Text.of("Using Heightmap mode!"));
            heightmapMode(world, user);
            user.sendMessage(Text.of("Saved heightmap data!"));
            return TypedActionResult.success(user.getStackInHand(hand));
        }

        // Get the height of the player's current position if they have a diamond in their inventory
        if (user.getInventory().contains(Items.DIAMOND.getDefaultStack())) {
            user.sendMessage(Text.of("Height is: " + world.getTopY(Heightmap.Type.WORLD_SURFACE, (int) user.getX(), (int) user.getZ())));
            return TypedActionResult.success(user.getStackInHand(hand));
        }

        // Save water data if the player has an emerald in their inventory
        if (user.getInventory().contains(Items.EMERALD.getDefaultStack())) {
            user.sendMessage(Text.of("Getting water data!"));
            saveWaterData(world, user);
            user.sendMessage(Text.of("Saved water data!"));
            return TypedActionResult.success(user.getStackInHand(hand));
        }

        // Spawns a random animal or villager depending on the player's selected slot
        ServerCommandSource source = user.getServer().getCommandSource();
        if (user.getInventory().selectedSlot != 0) {
            int floorY = world.getTopY(Heightmap.Type.WORLD_SURFACE, (int) user.getX(), (int) user.getZ());
            boolean isVillager = false;
            String villagerProfession = "";
            String mob = "minecraft:" + switch (user.getInventory().selectedSlot) {
                case 1 -> "sheep";
                case 2 -> "chicken";
                case 3 -> "pig";
                case 4 -> {
                    isVillager = true;
                    villagerProfession = switch (user.getRandom().nextBetween(1, 5)) {
                        case 1 -> "butcher";
                        case 2 -> "leatherworker";
                        case 3 -> "mason";
                        case 4 -> "shepherd";
                        default -> "farmer";
                    };
                    yield "villager";
                }
                default -> "cow";
            };

            source.getServer().getCommandManager().executeWithPrefix(source,
                    "/summon " + mob + " " + user.getX() + " " + floorY + " " + user.getZ() +
                            (isVillager ? " {VillagerData:{type: plains, profession: " + villagerProfession + ", level: " + user.getRandom().nextBetween(1, 5) + "}}" : "")
            );
            return TypedActionResult.success(user.getStackInHand(hand));
        }

        // Clear weather and set time to day

        source.getServer().getCommandManager().executeWithPrefix(source, "/weather clear");
        source.getServer().getCommandManager().executeWithPrefix(source, "/time set day");

        ModItems.teleportPlayer(world, user, true, Optional.empty(), Optional.empty());

        return TypedActionResult.success(user.getStackInHand(hand));
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        attacker.setNoGravity(!attacker.hasNoGravity()); // Allow the entity to fall again
        return true;
    }

    public static void heightmapMode(World world, PlayerEntity user) {
        BlockPos startBlockPos = user.getBlockPos();
        CoordinatePair regionCoordinates = getCoordinatePair(startBlockPos);

        Path largeTerrainFolder = Paths.get("../src/main/large_terrains");

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
    }

    /**
     * Gets the top block at each (X, Z) coordinate pair and checks if it is water.
     * If so, it appends a 1 to a file, else it appends a 0.
     * 
     * @param world The world the player is in
     * @param user The player to save the water data
     */
    private void saveWaterData(World world, PlayerEntity user) {
        BlockPos startBlockPos = user.getBlockPos();
        CoordinatePair regionCoordinates = getCoordinatePair(startBlockPos);

        Path largeTerrainFolder = Paths.get("../src/main/large_terrains");

        Path watermap = ModItems.getUniqueFileName(
                Path.of(largeTerrainFolder + "/watermap_data"),
                world.getBiome(user.getBlockPos()).getIdAsString(),
                ".txt",
                true
        );

        // Create a list to hold the rows of data (Y, Z, X, BlockState)
        List<String> lines = new ArrayList<>();

        int startX = regionCoordinates.startCoordinate().x();
        int startZ = regionCoordinates.startCoordinate().z();
        int endX = regionCoordinates.endCoordinate().x();
        int endZ = regionCoordinates.endCoordinate().z();

        // Loop over the Z-axis first to ensure row-major order (top-to-bottom)
        for (int z = startZ; z <= endZ; z++) {
            StringBuilder line = new StringBuilder();

            for (int x = startX; x <= endX; x++) {
                // Load and generate the chunk if necessary
                world.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);

                // Get the highest block at the current x, z position
                int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
                BlockState block = world.getBlockState(new BlockPos(x, y - 1, z));

                // If water block, append 1 to the line, else append 0
                line.append(block.isOf(Blocks.WATER) ? "1" : "0").append(",");
            }

            // Add the completed row to the list
            lines.add(line.toString());
        }

        // Write the data to the file
        try {
            Files.write(watermap, lines);
        } catch (IOException e) {
            LLMTerrainGeneration.LOGGER.error("Failed to write heightmap to {}", watermap);
        }
    }
}
