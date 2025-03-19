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
import shutil
from PIL import Image

# Define the target color (RGB format)
target_color = (192, 216, 255)  # #c0d8ff in RGB
folder_path = '../mc-project/src/main/biome_examples/regions/screenshots'  # Path to the folder containing PNG images
filter_folder = '../mc-project/src/main/biome_examples/regions/screenshots/filtered'  # Path to the "filter" folder

# Ensure the filter folder exists
if not os.path.exists(filter_folder):
    os.makedirs(filter_folder)

# Function to check how much of the image is the target color
def check_color_percentage(image_path):
    with Image.open(image_path) as img:
        img = img.convert("RGB")  # Convert image to RGB
        width, height = img.size
        total_pixels = width * height
        target_color_count = 0

        # Iterate through all the pixels
        for x in range(width):
            for y in range(height):
                pixel = img.getpixel((x, y))
                if pixel == target_color:
                    target_color_count += 1
        
        # Calculate the percentage
        color_percentage = (target_color_count / total_pixels) * 100
        return color_percentage

# Function to move the image to the "filter" folder
def move_to_filter(image_path):
    shutil.move(image_path, os.path.join(filter_folder, os.path.basename(image_path)))
    print(f"Moved {os.path.basename(image_path)} to filter folder.")

# Iterate through all PNG files in the folder
for filename in os.listdir(folder_path):
    if filename.lower().endswith('.png'):
        image_path = os.path.join(folder_path, filename)
        color_percentage = check_color_percentage(image_path)

        # If the percentage of the target color is more than 50%, move the file
        if color_percentage > 50:
            move_to_filter(image_path)
