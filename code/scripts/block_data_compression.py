# Copyright (C) 2025 Joel Sebastian, github.com/JoelSeb
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at:
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import os
import pandas as pd
import json
import re
from tqdm import tqdm  # Import tqdm
from collections import Counter

# Define the raw and processed data directories
region_raw_data_dir = os.path.join(os.path.realpath(os.getcwd()), '../mc-project/src/main/large_terrains/raw_data')
region_processed_data_dir = os.path.join(os.path.realpath(os.getcwd()), '../mc-project/src/main/large_terrains/processed_data')
os.listdir(region_raw_data_dir)

# Function to process each line manually
def process_line(line):
    # Split the line into the first four fields (x, y, z, and type)
    parts = line.split(",", 3)
    
    if len(parts) == 4:
        # Handle 'type' field: strip any extra attributes inside square brackets
        parts[3] = re.match(r"Block{[^}]+}", parts[3]).group(0) if "Block{" in parts[3] else parts[3]
        return parts
    else:
        # Handle malformed lines, log them and return None
        print(f"Skipping malformed line: {line}")
        return None  # Return None if the line doesn't have exactly 4 fields

def parse_data(df):
    # Calculate start and end coordinates
    start = {
        'x': int(df['x'].min()),
        'y': int(df['y'].min()),
        'z': int(df['z'].min())
    }
    end = {
        'x': int(df['x'].max()),
        'y': int(df['y'].max()),
        'z': int(df['z'].max())
    }
    
    layer_counts = {}  # Dictionary to store counts for each y-level
    for y, group in df.groupby('y'):
        type_counts = Counter(group['type']) #Use a counter for easier counting
        layer_counts[int(y)] = type_counts
    return start, end, layer_counts

# Ensure the processed data directory exists
os.makedirs(region_processed_data_dir, exist_ok=True)

# Iterate over each file in the raw data directory
for filename in os.listdir(region_raw_data_dir):
    # Create a JSON file (saved as .txt for LLM) in the processed data directory
    json_filename = os.path.splitext(filename)[0] + '.txt'
    json_path = os.path.join(region_processed_data_dir, json_filename)
    
    if json_filename in os.listdir(region_processed_data_dir):
        print(f"Skipping {filename} as {json_filename} already exists")
        continue

    file_path = os.path.join(region_raw_data_dir, filename)
    print(f"Processing {filename}...")

    # Load the data in chunks (e.g., 10000 lines at a time) to avoid memory overload
    chunk_size = 10000
    all_data = {'layers': {}}  # Initialize the main data structure

    try:
        with open(file_path, 'r') as f:
            total_lines = sum(1 for _ in f)
        total_chunks = (total_lines + chunk_size - 1) // chunk_size  # Calculate total chunks
    except FileNotFoundError:
        print(f"File not found: {file_path}")
        continue
    except Exception as e:
        print(f"Error getting file size: {e}")
        continue

    # Improved reading with regex separator
    chunks = pd.read_csv(
        file_path,
        header=None,
        names=['y', 'z', 'x', 'type'],
        dtype={'y': 'int32', 'z': 'int32', 'x': 'int32', 'type': 'string'}, # Important: 'string' dtype
        chunksize=chunk_size,
        sep=r",(?![^[]*])",  # Regex separator: comma not inside brackets
        engine='python' # Needed for regex separators
    )

    all_starts = []
    all_ends = []

    # Process each chunk individually
    for i, chunk in tqdm(enumerate(chunks), total=total_chunks, desc=f"Processing chunks of {filename}"):
        # Now that the 'type' column is correctly parsed, extract the Block{} part
        chunk['type'] = chunk['type'].str.extract(r"(Block{[^}]+})")
        chunk = chunk.dropna(subset=['y', 'z', 'x', 'type'])  # Drop rows with missing values after extraction
        chunk[['y', 'z', 'x']] = chunk[['y', 'z', 'x']].astype('int32')
        
        start, end, layer_counts = parse_data(chunk)
        all_starts.append(start)
        all_ends.append(end)

        for y, counts in layer_counts.items():
            if y not in all_data['layers']:
                all_data['layers'][y] = counts
            else:
                all_data['layers'][y].update(counts) #Update the counts

    if all_starts:
        all_data['start'] = min(all_starts, key=lambda x: (x['x'], x['y'], x['z']))
    if all_ends:
        all_data['end'] = max(all_ends, key=lambda x: (x['x'], x['y'], x['z']))

    # Calculate top 3 types for each layer AFTER processing all chunks
    total_blocks_per_layer = 589824
    for y, counts in all_data['layers'].items():
        type_counts_percentage = (pd.Series(counts) / total_blocks_per_layer) * 100
        top_types = type_counts_percentage.nlargest(3)
        all_data['layers'][y] = [{'block': block, 'frequency': f"{freq:.2f}%"} for block, freq in top_types.items()]

    # Convert layers dictionary to list for JSON output
    all_data['layers'] = [{'y': y, 'types': types} for y, types in all_data['layers'].items()]
    all_data['layers'].sort(key=lambda x: x['y'])

    # Write the complete JSON data
    if all_data['layers']:
        with open(json_path, 'w') as json_file:
            json.dump(all_data, json_file, indent=4)
        print(f"Data successfully converted and saved to {json_path}")
    else:
        print(f"File {filename} is empty, no JSON file created")