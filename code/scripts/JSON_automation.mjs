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

import express from "express";
import fs from "fs";
import path from "path";
import chokidar from "chokidar";
import stringify from "json-stringify-pretty-compact";
import { fileURLToPath } from "url";
import { spawn } from "child_process";

// Define __dirname for ES module compatibility
const __dirname = path.dirname(fileURLToPath(import.meta.url));

const app = express();
const foldersToWatch = [
  path.join(
    __dirname,
    "../mc-project/src/main/biome_examples/regions/processed_data"
  ),
  path.join(
    __dirname,
    "../mc-project/src/main/biome_examples/regions/rle_data"
  ),
  path.join(
    __dirname,
    "../mc-project/src/main/biome_examples/regions/raw_data"
  ),
  path.join(
    __dirname,
    "../mc-project/src/main/large_terrains/rle_data"
  ),
  path.join(
    __dirname,
    "../mc-project/src/main/large_terrains/raw_data"
  ),
];
const port = 7778;
const delay = 5000;

// Endpoint to confirm the server is running
app.get("/", (req, res) => {
  res.send(
    "File Watcher Server is running and watching for new JSON files in multiple folders..."
  );
});

// Start the Express server
app.listen(port, () => {
  console.log(`Server is running at http://localhost:${port}`);
  console.log(`Watching folders: ${foldersToWatch.join(", ")}`);
});

// Function to process JSON files (reads, formats, and saves)
function processJsonFile(filePath) {
  fs.readFile(filePath, "utf8", (err, data) => {
    if (err) {
      console.error(`Error reading file ${filePath}:`, err);
      return;
    }
    try {
      const jsonContent = JSON.parse(data);
      const formattedJson = stringify(jsonContent);

      // Save the formatted JSON back to a new file with "simplified_" prefix
      const outputFilePath = path.join(
        path.dirname(filePath),
        `simplified_${path.basename(filePath)}`
      );
      fs.writeFile(outputFilePath, formattedJson, (err) => {
        if (err) {
          console.error(`Error writing formatted file ${outputFilePath}:`, err);
        } else {
          console.log(`Formatted JSON saved to ${outputFilePath}`);
        }
      });
    } catch (parseErr) {
      console.error(`Error parsing JSON from ${filePath}:`, parseErr);
    }
  });
}

// Set up watchers for each folder in foldersToWatch
foldersToWatch.forEach((folder) => {
  chokidar
    .watch(folder, {
      ignored: /(^|[\/\\])simplified_.*\.txt$/, // Ignore files starting with "simplified_" in JSON format (saved as .txt)
      persistent: true,
    })
    .on("add", (filePath) => {
      if (path.extname(filePath) === ".txt" || path.extname(filePath) === ".json") {
        console.log(`New JSON file detected: ${filePath}`);
        setTimeout(() => {
          processJsonFile(filePath); // Process the file when added
        }, delay);
      }
      else if (path.extname(filePath) === ".csv") { // raw_data folder
        console.log(`New CSV file detected: ${filePath}`)
        setTimeout(() => {
          // Run the Python script
          const pythonProcess = spawn("python", [path.join(__dirname, "block_data_compression.py")]);

          pythonProcess.stdout.on("data", (data) => {
            console.log(`stdout: ${data}`);
          });
        }, delay);
      }
    })
    .on("error", (error) =>
      console.error(`Watcher error in folder ${folder}: ${error}`)
    );
});
