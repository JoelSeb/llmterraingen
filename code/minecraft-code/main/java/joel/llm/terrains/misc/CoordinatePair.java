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

/**
 * Represents a pair of coordinates in 3D space.
 */
public record CoordinatePair(Coordinate startCoordinate, Coordinate endCoordinate) {
    /**
     * Checks if any of the four corners of the region encapsulated by the CoordinatePair is within the range of another CoordinatePair.
     * (Four corners are checked because the region is a cuboid - y region is fixed).
     * <p>
     * 1. Top-Left Corner
     * 2. Top-Right Corner
     * 3. Bottom-Left Corner
     * 4. Bottom-Right Corner
     *
     * @param coordinatePair : The CoordinatePair to check against.
     * @return : True if any of the four corners of the region encapsulated by the CoordinatePair is within the range of another CoordinatePair.
     */
    public boolean isWithinRange(CoordinatePair coordinatePair) {
        int startX = coordinatePair.startCoordinate.x();
        int startZ = coordinatePair.startCoordinate.z();
        int endX = coordinatePair.endCoordinate.x();
        int endZ = coordinatePair.endCoordinate.z();

        // Check if the ranges overlap on both x and z axes
        boolean xOverlap = (this.startCoordinate.x() <= endX && this.endCoordinate.x() >= startX);
        boolean zOverlap = (this.startCoordinate.z() <= endZ && this.endCoordinate.z() >= startZ);

        return xOverlap && zOverlap;
    }
    
    public static CoordinatePair fromFileName(String fileName) {
        String[] coordinates = fileName.split("_");
        Coordinate startCoordinate = Coordinate.fromFileName(coordinates[0]);
        Coordinate endCoordinate = Coordinate.fromFileName(coordinates[1]);
        return new CoordinatePair(startCoordinate, endCoordinate);
    }
}
