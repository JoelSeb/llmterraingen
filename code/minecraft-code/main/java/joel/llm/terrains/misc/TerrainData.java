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

import java.util.List;

public class TerrainData {
    public Coordinate start;
    public List<Mapping> mappings;
    public List<GridLayer> grids;

    public static class Mapping {
        public String symbol;
        public String block;
    }

    public static class GridLayer {
        public int y;
        public List<List<String>> grid;
    }
}
