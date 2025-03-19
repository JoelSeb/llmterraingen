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
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.noise.OctavePerlinNoiseSampler;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * The main item used for testing dynamic terrain generation.
 * Generates terrain based on commands in a .txt file (created by an LLM).
 */
public class TerrainGeneratorItem extends Item {
    public TerrainGeneratorItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.llm-terrain-generation.terrain_generator.tooltip").formatted(Formatting.GOLD));
    }

    /**
     * Generates an environment based on the "/generate ..." commands in a .txt file.
     *
     * @param world the world the item was used in
     * @param user the player who used the item
     * @param hand the hand used
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient) return TypedActionResult.pass(user.getStackInHand(hand));

        // Get list of all TXT files in folder
        Path folder = Paths.get("../src/main/generated_terrains/");
        List<Path> terrains = null;
        try {
            terrains = Files.walk(folder)
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.toString().contains("archive") && !path.toString().contains("iterations"))
                    .toList();
        } catch (IOException e) {
            LLMTerrainGeneration.LOGGER.error("Error reading TXT files");
        }

        if (terrains == null) throw new AssertionError();

        List<Path> firstLLMTerrains = terrains.stream().filter(path -> path.toString().contains("1_")).toList();
        List<Path> secondLLMTerrains = terrains.stream().filter(path -> path.toString().contains("2_")).toList();
        List<Path>[] allTerrains = new List[]{firstLLMTerrains, secondLLMTerrains};

//        Path terrain = terrains.getLast(); // Get the most recently added terrain
        for (Path terrain : allTerrains[user.getInventory().selectedSlot]) {
            LLMTerrainGeneration.LOGGER.info("Reading terrain from: {}", terrain);

            // Read the file and find each of the /generate commands
            try {
                List<String> lines = Files.readAllLines(terrain);
                for (String line : lines)
                    if (line.trim().startsWith("/generate"))
                        if (!(new GenerationCommand(line, (ServerWorld) world).execute()))
                            LLMTerrainGeneration.LOGGER.error("Error generating feature");
            } catch (IOException e) {
                LLMTerrainGeneration.LOGGER.error("Error reading TXT file");
            }
        }

        LLMTerrainGeneration.LOGGER.info("Filenames: {}", terrains);

        return TypedActionResult.pass(user.getStackInHand(hand));
    }

    /**
     * Command Behavioural Design Pattern for generating terrain based on commands.
     * The modular design allows for easy addition of new terrain generation commands as well as easy modification of existing ones.
     */
    public static class GenerationCommand {
        private final String toGenerate;
        private final String[] parameters;
        private final ServerCommandSource source;

        public GenerationCommand(String command, ServerWorld world) {
            String[] parts = command.split(" ");
            toGenerate = parts[1];
            parameters = new String[parts.length - 2];
            System.arraycopy(parts, 2, parameters, 0, parts.length - 2);
            source = world.getServer().getCommandSource();
        }

        public boolean execute() {
            return switch (toGenerate) {
                case "village" -> generateVillage();
                case "grass" -> generateGrass();
                case "forest" -> generateForest();
                case "flowers" -> generateFlowers();
                case "hill" -> generateHill();
                case "lake" -> generateLake();
                case "river" -> generateRiver();
                default -> false;
            };
        }

        private boolean generateFlowers() {
            int x = Integer.parseInt(parameters[0]), z = Integer.parseInt(parameters[1]);
            int radius = Integer.parseInt(parameters[2]);
            int area = radius * radius; // Approximate area of a circle, around 3 grass blocks are placed per command
            int toPlace = (int) switch (parameters[3]) {
                case "low" -> area * 0.01;
                case "high" -> area * 0.05;
                default -> area * 0.03;
            };
            long seed = parameters[4].isEmpty() ? Long.parseLong(parameters[4]) : 0;
            Random random = Random.create(seed);

            for (int i = 0; i < toPlace; i++) {
                int newX = x + random.nextBetween(-radius, radius);
                int newZ = z + random.nextBetween(-radius, radius);
                int y = source.getWorld().getTopY(Heightmap.Type.WORLD_SURFACE, newX, newZ);
                source.getServer().getCommandManager().executeWithPrefix(source, "/place feature minecraft:forest_flowers " + newX + " " + y + " " + newZ);
            }
            return true;
        }

        private boolean generateForest() {
            int x = Integer.parseInt(parameters[0]), z = Integer.parseInt(parameters[1]);
            int radius = Integer.parseInt(parameters[2]);
            int area = radius * radius; // Approximate area of a circle, 1 tree is placed per command
            int toPlace = (int) switch (parameters[3]) {
                case "low" -> area * 0.02;
                case "high" -> area * 0.1;
                default -> area * 0.05;
            };
            long seed = parameters[5].isEmpty() ? Long.parseLong(parameters[5]) : 0;
            Random random = Random.create(seed);
            boolean mixed = parameters[4].equals("any");
            String treeType = switch (parameters[4]) {
                case "oak" -> "minecraft:oak";
                case "birch" -> "minecraft:birch";
                default -> "minecraft:trees_plains";
            };

            for (int i = 0; i < toPlace; i++) {
                int newX = x + random.nextBetween(-radius, radius);
                int newZ = z + random.nextBetween(-radius, radius);
                int y = source.getWorld().getTopY(Heightmap.Type.WORLD_SURFACE, newX, newZ);

                // If the top block is a leaf, we want to look for another place to put the tree
                if (source.getWorld().getBlockState(new BlockPos(newX, y - 1, newZ)) != Blocks.GRASS_BLOCK.getDefaultState()) {
                    i--;
                    continue;
                }

                source.getServer().getCommandManager().executeWithPrefix(source, "/place feature " + (mixed ? random.nextBoolean() ? "minecraft:oak" : "minecraft:birch" : treeType) + " " + newX + " " + y + " " + newZ);
            }

            return true;
        }

        private boolean generateGrass() {
            int x = Integer.parseInt(parameters[0]), z = Integer.parseInt(parameters[1]);
            int radius = Integer.parseInt(parameters[2]);
            int area = radius * radius; // Approximate area of a circle, around 3 grass blocks are placed per command
            int toPlace = (int) switch (parameters[3]) {
                case "low" -> area * 0.1;
                case "high" -> area * 0.5;
                default -> area * 0.3;
            };

            for (int i = 0; i < toPlace; i++) {
                int newX = x + source.getWorld().getRandom().nextBetween(-radius, radius);
                int newZ = z + source.getWorld().getRandom().nextBetween(-radius, radius);
                int y = source.getWorld().getTopY(Heightmap.Type.WORLD_SURFACE, newX, newZ);
                source.getServer().getCommandManager().executeWithPrefix(source, "/place feature minecraft:patch_grass " + newX + " " + y + " " + newZ);
            }
            return true;
        }

        /**
         * Uses Perlin noise to generate a hill between the specified coordinates.
         * The hill is generated using a sigmoid function to create a smooth hill.
         * The hill is then populated with grass blocks and has a random chance of placing coal or iron ore.
         * To produce the hill-shape (no side cut off), any cut-off regions are continued (more generation).
         *
         * @return true if the hill was generated successfully, false otherwise
         */
        private boolean generateHill() {
            int bottomLeftX = Integer.parseInt(parameters[0]), bottomLeftZ = Integer.parseInt(parameters[1]);
            int topRightX = Integer.parseInt(parameters[2]), topRightZ = Integer.parseInt(parameters[3]);
            int maxAmplitude = Integer.parseInt(parameters[4]);
            long seed = parameters[5].isEmpty() ? Long.parseLong(parameters[5]) : 0;
            Random random = Random.create(seed);
            int bottomY = source.getWorld().getTopY(Heightmap.Type.WORLD_SURFACE, bottomLeftX, bottomLeftZ);

            double squashFactor = 0.1;
            placeTerrain(bottomLeftX, bottomLeftZ, topRightX, topRightZ, bottomY, bottomY + maxAmplitude, squashFactor, random);

            // Replace the top two, three or four blocks with grass and have a random chance of placing ore (coal or iron)
            for (int xCoord = bottomLeftX; xCoord <= topRightX; xCoord++) {
                for (int zCoord = bottomLeftZ; zCoord <= topRightZ; zCoord++) {
                    int y = source.getWorld().getTopY(Heightmap.Type.WORLD_SURFACE, xCoord, zCoord) - 1;
                    int randomResult = random.nextBetween(0, 100);

                    // Places 2-4 grass blocks (with an equal chance of each)
                    source.getWorld().setBlockState(new BlockPos(xCoord, y--, zCoord), Blocks.GRASS_BLOCK.getDefaultState(), 3);
                    source.getWorld().setBlockState(new BlockPos(xCoord, y--, zCoord), Blocks.GRASS_BLOCK.getDefaultState(), 3);
                    if (randomResult > 33) source.getWorld().setBlockState(new BlockPos(xCoord, y--, zCoord), Blocks.GRASS_BLOCK.getDefaultState(), 3);
                    if (randomResult > 66) source.getWorld().setBlockState(new BlockPos(xCoord, y--, zCoord), Blocks.GRASS_BLOCK.getDefaultState(), 3);

                    // 8% chance of placing coal ore and 2% chance of placing iron ore
                    for (int yCoord = y; yCoord > bottomY; yCoord--) {
                        if (random.nextBetween(0, 100) < 8) source.getWorld().setBlockState(new BlockPos(xCoord, yCoord, zCoord), Blocks.COAL_ORE.getDefaultState(), 3);
                        if (random.nextBetween(0, 100) < 2) source.getWorld().setBlockState(new BlockPos(xCoord, yCoord, zCoord), Blocks.IRON_ORE.getDefaultState(), 3);
                    }
                }
            }

            return true;
        }

        /**
         * Uses Perlin noise to generate a terrain between the specified coordinates.
         * The terrain is generated using a sigmoid function to create a smooth transition between blocks.
         * The terrain is then populated with stone blocks.
         * 
         * @param bottomLeftX The x-coordinate of the bottom-left corner
         * @param bottomLeftZ The z-coordinate of the bottom-left corner
         * @param topRightX The x-coordinate of the top-right corner
         * @param topRightZ The z-coordinate of the top-right corner
         * @param bottomY The y-coordinate of the bottom of the terrain
         * @param topY The y-coordinate of the top of the terrain
         * @param squashFactor The factor to squash the sigmoid function by
         * @param random The random number generator to use
         */
        private void placeTerrain(int bottomLeftX, int bottomLeftZ, int topRightX, int topRightZ, int bottomY, int topY, double squashFactor, Random random) {
            OctavePerlinNoiseSampler sampler = OctavePerlinNoiseSampler.create(random, 0, 3, 0.1, 0.05, 0.01);

            BlockPos.stream(bottomLeftX, bottomY, bottomLeftZ, topRightX, topY, topRightZ)
                    .forEach(pos -> {
                        double noise = sampler.sample((double) pos.getX() / 50, 0, (double) pos.getZ() / 50) ;
                        double val = 2 * sigmoid((pos.getY() - bottomY) * squashFactor) - 1;

                        source.getWorld().setBlockState(pos, noise > val ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState(), 3);
                    });
        }

        private double sigmoid(double x) {
            return 1 / (1 + Math.exp(-x));
        }

        // Generates a lake which is either a square shape or a circle shape
        private boolean generateLake() {
            int x = Integer.parseInt(parameters[0]), z = Integer.parseInt(parameters[1]);
            int lowestY = source.getWorld().getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
            boolean isCircle = parameters[2].equals("circle");
            double lengthOrRadius = Double.parseDouble(parameters[3]);
            if (isCircle && lengthOrRadius % 1 == 0) lengthOrRadius += 0.5; // Circles are nicer with N.5 radii
            double width = isCircle ? lengthOrRadius : Integer.parseInt(parameters[4]);

            // Makes a circle lake centered at (x, y, z) with radius lengthOrRadius
            if (isCircle) {
                int top = (int) Math.ceil(z - lengthOrRadius), bottom = (int) Math.floor(z + lengthOrRadius);
                int left = (int) Math.ceil(x - lengthOrRadius), right = (int) Math.floor(x + lengthOrRadius);

                for (int i = top; i <= bottom; i++) {
                    for (int j = left; j <= right; j++) {
                        lowestY = Math.min(lowestY, source.getWorld().getTopY(Heightmap.Type.WORLD_SURFACE, i, j) - 1);
                    }
                }

                for (int i = top; i <= bottom; i++) {
                    for (int j = left; j <= right; j++) {
                        if (Math.pow(j - x, 2) + Math.pow(i - z, 2) <= Math.pow(lengthOrRadius, 2)) {
                            source.getWorld().setBlockState(new BlockPos(j, lowestY, i), Blocks.WATER.getDefaultState(), 3);
                        }
                    }
                }

                return true;
            }

            // Makes a square lake centered at (x, y, z) with side lengths lengthOrRadius and width
            // Loop through all points and find the lowest y
            for (int i = ((int)(x - width / 2)); i <= ((int)(x + width / 2)); i++) {
                for (int j = ((int)(z - lengthOrRadius / 2)); j <= ((int)(z + lengthOrRadius / 2)); j++) {
                    lowestY = Math.min(lowestY, source.getWorld().getTopY(Heightmap.Type.WORLD_SURFACE, i, j) - 1);
                }
            }

            source.getServer().getCommandManager().executeWithPrefix(source, "/fill " + ((int)(x - width / 2)) + " " + lowestY + " " + ((int)(z - lengthOrRadius / 2)) + " " + ((int)(x + width / 2)) + " " + lowestY + " " + ((int)(z + lengthOrRadius / 2)) + " minecraft:water");
            return true;
        }

        // Generates a river with random chances to swap between horizontal and vertical (X and Z) directions
        private boolean generateRiver() {
            ServerWorld world = source.getWorld();
            long seed = parameters[5].isEmpty() ? Long.parseLong(parameters[5]) : 0;
            Random random = Random.create(seed);

            int startX = Integer.parseInt(parameters[0]), startZ = Integer.parseInt(parameters[1]);
            int endX = Integer.parseInt(parameters[2]), endZ = Integer.parseInt(parameters[3]);
            int width = Integer.parseInt(parameters[4]);
            width = width % 2 == 0 ? width + 1 : width; // Ensure width is odd
            boolean vertical = random.nextBoolean();

            int currentX = startX, currentZ = startZ;
            int lowestY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, currentX, currentZ) - 1;

            List<Pair<Integer, Integer>> positions = new ArrayList<>();

            while (currentX != endX || currentZ != endZ) {
                for (int i = 0; i <= width / 2; i++) {
                    if (vertical) {
                        lowestY = Math.min(lowestY, world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, currentX + i, currentZ));
                        lowestY = Math.min(lowestY, world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, currentX - i, currentZ));
                        positions.add(new Pair<>(currentX + i, currentZ));
                        positions.add(new Pair<>(currentX - i, currentZ));
                    } else {
                        lowestY = Math.min(lowestY, world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, currentX, currentZ + i));
                        lowestY = Math.min(lowestY, world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, currentX, currentZ - i));
                        positions.add(new Pair<>(currentX, currentZ + i));
                        positions.add(new Pair<>(currentX, currentZ - i));
                    }
                }

                // 40% chance to change direction (if not at the end of a direction)
                if (random.nextBetween(0, 100) < 40 || currentX == endX || currentZ == endZ) {
                    if (currentX == endX) {
                        vertical = true;
                    } else if (currentZ == endZ) {
                        vertical = false;
                    } else {
                        vertical = random.nextBoolean();
                    }
                }

                // Move in the current direction
                if (vertical) {
                    currentZ += endZ > startZ ? 1 : -1;
                } else {
                    currentX += endX > startX ? 1 : -1;
                }
            }

            for (Pair<Integer, Integer> position : positions)
                world.setBlockState(new BlockPos(position.getLeft(), lowestY - 1, position.getRight()), Blocks.WATER.getDefaultState(), 3);

            return true;
        }

        private boolean generateVillage() {
            int y = source.getWorld().getTopY(Heightmap.Type.WORLD_SURFACE, Integer.parseInt(parameters[0]), Integer.parseInt(parameters[1]));
            source.getServer().getCommandManager().executeWithPrefix(source, "/place structure minecraft:village_plains " + parameters[0] + " " + y + " " + parameters[1]);
            return true;
        }
    }
}
