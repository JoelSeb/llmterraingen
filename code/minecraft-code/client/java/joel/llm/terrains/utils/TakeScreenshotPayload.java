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

import joel.llm.terrains.LLMTerrainGenerationClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/**
 * Payload class for taking a screenshot.
 */
public record TakeScreenshotPayload() implements CustomPayload {

    // Create a static ID for payload
    public static final CustomPayload.Id<TakeScreenshotPayload> ID = new CustomPayload.Id<>(LLMTerrainGenerationClient.TAKE_SCREENSHOT_ID.id());

    // Define a codec for TakeScreenshotPayload
    public static final PacketCodec<PacketByteBuf, TakeScreenshotPayload> CODEC = new PacketCodec<PacketByteBuf, TakeScreenshotPayload>() {

        @Override
        public TakeScreenshotPayload decode(PacketByteBuf buf) {
            return new TakeScreenshotPayload();
        }

        @Override
        public void encode(PacketByteBuf buf, TakeScreenshotPayload payload) {}
    };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
