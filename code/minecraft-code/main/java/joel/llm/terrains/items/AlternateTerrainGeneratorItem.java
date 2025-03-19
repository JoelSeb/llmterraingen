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
import com.google.gson.JsonObject;
import joel.llm.terrains.LLMTerrainGeneration;
import joel.llm.terrains.misc.TerrainData;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The original method for generating terrains from run-length encoded JSON files.
 * As the high-level approach was preferred, this method was not used in the final implementation.
 */
public class AlternateTerrainGeneratorItem extends Item {
    public AlternateTerrainGeneratorItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient) return TypedActionResult.pass(user.getStackInHand(hand));

        // Get list of all JSON files in folder
        Path folder = Paths.get("../src/main/large_terrains/rle_data");
        List<Path> terrains = null;
        try {
            terrains = Files.walk(folder)
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.toString().contains("archive"))
                    .toList();
        } catch (IOException e) {
            LLMTerrainGeneration.LOGGER.error("Error reading JSON files");
        }

        // Generate terrain from each JSON file
        if (terrains == null) throw new AssertionError();

        Path firstLLM = terrains.stream().filter(path -> path.toString().contains("llm_1")).toList().getFirst();
        Path secondLLM = terrains.stream().filter(path -> path.toString().contains("llm_2")).toList().getFirst();
        Path firstRegular = terrains.stream().filter(path -> path.toString().contains("regular_1")).toList().getFirst();
        Path secondRegular = terrains.stream().filter(path -> path.toString().contains("regular_2")).toList().getFirst();

        // Initial position for first terrain
        BlockPos nextAvailablePosition = user.getBlockPos().offset(user.getHorizontalFacing(), 10);

        Path[] terrainsToGenerate = {firstLLM, secondLLM, firstRegular, secondRegular};
        Path terrain = terrainsToGenerate[user.getInventory().selectedSlot];
        user.sendMessage(Text.of("Source: " + terrain));

        Gson gson = new Gson();
        try {
            String jsonContent = new String(Files.readAllBytes(terrain));
            JsonObject json = gson.fromJson(jsonContent, JsonObject.class);
            TerrainData terrainData = parseTerrainData(json);

            TerrainGen terrainGen = new TerrainGen(terrainData.mappings);
            terrainGen.generateTerrain(world, nextAvailablePosition, terrainData);

            user.sendMessage(Text.of("Generated Terrain starting at: (" + nextAvailablePosition.getX() + ", " + nextAvailablePosition.getY() + ", " + nextAvailablePosition.getZ() + ")"));
        } catch (IOException e) {
            LLMTerrainGeneration.LOGGER.error("Error reading JSON file");
        }

        return TypedActionResult.success(user.getStackInHand(hand));
    }

    private TerrainData parseTerrainData(JsonObject json) {
        return new Gson().fromJson(json, TerrainData.class);
    }

    /**
     * Class for generating terrain from run-length encoded JSON files.
     */
    public static class TerrainGen {
        private final Map<String, Block> blockMapping;

        public TerrainGen(List<TerrainData.Mapping> mappings) {
            blockMapping = new HashMap<>();
            for (TerrainData.Mapping mapping : mappings) {
                blockMapping.put(mapping.symbol, Registries.BLOCK.get(Identifier.of(mapping.block)));
            }
        }

        /**
         * Loop through each layer of the terrain and generate the blocks.
         * 
         * @param world The world to generate the terrain in
         * @param startPos The starting position of the terrain
         * @param terrainData The terrain data to generate
         */
        public void generateTerrain(World world, BlockPos startPos, TerrainData terrainData) {
            for (TerrainData.GridLayer layer : terrainData.grids) {
                int currentLayer = layer.y - terrainData.start.y();
                for (int z = 0; z < layer.grid.size(); z++) { // Looped 16 times.
                    List<String> row = layer.grid.get(z);
                    for (int x = 0; x < row.size(); x++) {
                        parseAndPlaceBlocks(world, startPos.add(x, currentLayer, z), row.get(x));
                    }
                }
            }
        }

        /**
         * Parse the encoded string and place the blocks in the world.
         * 
         * @param world The world to place the blocks in
         * @param startPos The starting position of the blocks
         * @param encodedString The encoded string to parse
         */
        private void parseAndPlaceBlocks(World world, BlockPos startPos, String encodedString) {
            Matcher matcher = Pattern.compile("(\\d+)([A-Za-z!@#$%^&*()\\\\-_+=]+)").matcher(encodedString);
            int offsetX = 0;

            while (matcher.find()) {
                int count = Integer.parseInt(matcher.group(1));
                String symbol = matcher.group(2);
                Block block = blockMapping.get(symbol);

                if (block == null) {
                    LLMTerrainGeneration.LOGGER.warn("Unrecognized symbol in mapping: {}", symbol);
                    continue;
                }
//                if (block == Blocks.AIR) continue;

                for (int i = 0; i < count; i++) {
                    world.setBlockState(startPos.add(offsetX++, 0, 0), block.getDefaultState());
                }
            }
        }
    }
}
