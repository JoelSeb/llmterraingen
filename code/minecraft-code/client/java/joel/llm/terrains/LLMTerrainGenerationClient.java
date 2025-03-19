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
import joel.llm.terrains.utils.TakeScreenshotPayload;
import joel.llm.terrains.utils.TakeScreenshotPayloadHandler;
import joel.llm.terrains.utils.ClientUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * The client-side entrypoint for the mod.
 * Responsible for setting up client-side logic - registering payload handlers and injecting client utilities.
 */
public class LLMTerrainGenerationClient implements ClientModInitializer {
	public static final CustomPayload.Id<TakeScreenshotPayload> TAKE_SCREENSHOT_ID = new CustomPayload.Id<>(Identifier.of(LLMTerrainGeneration.MOD_ID, "take_screenshot"));

	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		PayloadTypeRegistry.playS2C().register(TAKE_SCREENSHOT_ID, TakeScreenshotPayload.CODEC); // Register the custom payload

		registerClientHandlers(); // Register client-side handlers
		ModItems.setClientUtils(new ClientUtils()); // Inject the client-side screenshot handler

	}

	public static void registerClientHandlers() {
		// Receive 'TAKE_SCREENSHOT_ID' to handle screenshot action.
		ClientPlayNetworking.registerGlobalReceiver(
				new CustomPayload.Id<>(Identifier.of(LLMTerrainGeneration.MOD_ID, "take_screenshot")),
				new TakeScreenshotPayloadHandler()
		);
	}
}