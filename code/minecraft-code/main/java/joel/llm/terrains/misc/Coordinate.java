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

package joel.llm.terrains.misc;

import net.minecraft.util.math.BlockPos;

/**
 * Represents a coordinate in 3D space.
 */
public record Coordinate(int x, int y, int z) {
    @Override
    public String toString() {
        return String.format("(%d, %d, %d)", x, y, z);
    }

    public String toXZ() {
        return String.format("(%d, %d)", x, z);
    }

    public BlockPos toBlockPos() {
        return new BlockPos(x, y, z);
    }

    public static Coordinate coordsToCentre(Coordinate point, int tileLength) {
        int x = (int) Math.floor((double) point.x() / tileLength) * tileLength + tileLength / 2;
        int z = (int) Math.floor((double) point.z() / tileLength) * tileLength + tileLength / 2;
        return new Coordinate(x, point.y(), z);
    }

    public static CoordinatePair centreToPair(Coordinate centre, int tileLength) {
        return new CoordinatePair(
                new Coordinate(centre.x() - tileLength / 2, centre.y(), centre.z() - tileLength / 2),
                new Coordinate(centre.x() + tileLength / 2 - 1, centre.y(), centre.z() + tileLength / 2 - 1)
        );
    }

    public static Coordinate fromFileName(String fileName) {
        // Ignore any brackets, trim whitespace, and remove ".resolved" if present
        fileName = fileName.replace("(", "").replace(")", "").trim();
        if (fileName.endsWith(".resolved")) {
            fileName = fileName.substring(0, fileName.length() - 9);
        }
        String[] coordinates = fileName.split(",");

        try {
            return new Coordinate(
                    Integer.parseInt(coordinates[0].trim()),
                    coordinates.length == 3 ? Integer.parseInt(coordinates[1].trim()) : 0,
                    coordinates.length == 3 ? Integer.parseInt(coordinates[2].trim()) : Integer.parseInt(coordinates[1].trim())
            );
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid coordinate format: " + fileName, e);
        }
    }
}