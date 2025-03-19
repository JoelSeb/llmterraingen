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

package joel.llm.terrains.utils;

import joel.llm.terrains.LLMTerrainGeneration;
import joel.llm.terrains.items.ModItems;
import joel.llm.terrains.misc.IScreenshotHandler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static net.minecraft.client.MinecraftClient.getInstance;
import static net.minecraft.client.util.ScreenshotRecorder.takeScreenshot;

/**
 * Utility class for client-side operations.
 */
public class ClientUtils implements IScreenshotHandler {
    Path regionExampleFolder = Paths.get("../src/main/biome_examples/regions");

    /**
     * Function that takes a screenshot of what the entity sees.
     *
     * @param world The world the player is in
     * @param player The player to take the screenshot
     */
    public void saveScreenshot(World world, PlayerEntity player) {
        if (!getInstance().isOnThread()) {
            throw new IllegalStateException("Must be called from the client thread!");
        }

        Path newRegionExampleScreenshot = ModItems.getUniqueFileName(
                Path.of(regionExampleFolder + "/screenshots"),
                world.getBiome(player.getBlockPos()).getIdAsString(),
                ".png",
                true
        );

        // Take a screenshot of the player's POV
        try (NativeImage nativeImage = takeScreenshot(getInstance().getFramebuffer())) {
            nativeImage.writeTo(newRegionExampleScreenshot);
            System.out.println("Screenshot saved to: " + newRegionExampleScreenshot);
        } catch (IOException e) {
            LLMTerrainGeneration.LOGGER.error("Failed to write screenshot to {}", newRegionExampleScreenshot, e);
        }
    }

    /**
     * Function that sends a packet to the server to take a screenshot.
     *
     * @param user The player to take the screenshot
     */
    public void notifyClientTakeScreenshot(PlayerEntity user) {
        if (user instanceof ServerPlayerEntity serverPlayer) {
            TakeScreenshotPayload payload = new TakeScreenshotPayload();
            ServerPlayNetworking.send(serverPlayer, payload);
        }
    }
}
