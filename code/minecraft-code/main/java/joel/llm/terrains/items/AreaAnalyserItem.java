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
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;

import static joel.llm.terrains.items.ModItems.getUniqueFileName;

/**
 * An item that allows the player to analyse the blocks in a chunk and write the data to a CSV file.
 */
public class AreaAnalyserItem extends Item {
    HashSet<ChunkPos> scannedChunks = new HashSet<>();
    Path chunkletExampleFolder = Paths.get("../src/main/biome_examples/chunklets/raw_data");

    public AreaAnalyserItem(Settings settings) {
        super(settings);
    }

    /**
     * Called when the player right-clicks with the item in hand.
     * This method is responsible for analysing the chunk the player is in and writing the data to a CSV file.
     * Only y values between 62 and 255 (inclusive) are considered.
     *
     * @param world The world the player is in.
     * @param user The player using the item.
     * @param hand The hand the item is in.
     * @return The result of the action.
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient) return TypedActionResult.pass(user.getStackInHand(hand));

        Chunk currentChunk = world.getChunk(user.getBlockPos());

        // Check if the chunk has already been scanned (if not, add it to the set)
        if (scannedChunks.add(currentChunk.getPos())) {
            user.sendMessage(Text.of("New Chunk Analysed!"));
            user.sendMessage(Text.of("Chunk Start: " + currentChunk.getPos().getStartX() + ", " + currentChunk.getPos().getStartZ()));
            user.sendMessage(Text.of("Chunk End: " + currentChunk.getPos().getEndX() + ", " + currentChunk.getPos().getEndZ()));
            user.sendMessage(Text.of("Chunk Position: " + currentChunk.getPos()));

            Path newChunkletExample = getUniqueFileName(
                    chunkletExampleFolder,
                    world.getBiome(user.getBlockPos()).getIdAsString(),
                    ".csv",
                    true
            );

            ModItems.saveBlockDataCSV(
                    newChunkletExample,
                    new CoordinatePair(
                            new Coordinate(currentChunk.getPos().getStartX(), 62, currentChunk.getPos().getStartZ()),
                            new Coordinate(currentChunk.getPos().getEndX(), 255, currentChunk.getPos().getEndZ())
                    ),
                    world
            );
        } else {
            user.sendMessage(Text.of("Chunk Analysed Already!"));
        }

        return TypedActionResult.success(user.getStackInHand(hand));
    }

    /**
     * Called when the player hits a living entity with the item.
     * This method is responsible for clearing the scanned chunks set.
     *
     * @param stack The item stack used to hit the entity.
     * @param target The entity being hit.
     * @param attacker The entity hitting the target.
     * @return Whether the hit was successful.
     */
    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        scannedChunks.clear();
        attacker.sendMessage(Text.of("Cleared Chunk History!"));

        return true;
    }
}
