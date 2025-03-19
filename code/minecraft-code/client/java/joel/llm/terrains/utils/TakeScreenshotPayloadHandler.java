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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Payload handler class for taking a screenshot.
 */
public class TakeScreenshotPayloadHandler implements ClientPlayNetworking.PlayPayloadHandler<TakeScreenshotPayload> {
    @Override
    public void receive(TakeScreenshotPayload takeScreenshotPayload, ClientPlayNetworking.Context context) {
        // Ensure the client processes this on its main thread
        context.client().execute(() -> {
            ClientPlayerEntity player = context.player();
            if (player != null) {

                LLMTerrainGeneration.LOGGER.info("Taking screenshot client-side...");

                // Save the screenshot with the player's POV (their frame buffer)
                ModItems.saveScreenshot(
                        player.getWorld(),
                        player
                );
            }
        });
    }
}
