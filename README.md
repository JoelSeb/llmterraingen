# llmterraingen
Master's Dissertation Project titled "Generating Dynamic Virtual Environments Using Large Language Models"

## Dynamic Content Generation In Action!

![Demo](demo.gif)

The majority of project files, especially those required to run the modded Minecraft version, take up gigabytes of storage. For the sake of brevity, only the most relevant files have been included in this repository.

## Directory Structure

### `experiment_data/`
Contains the data from the user study, including:
- Combined short-form responses and longer feedback forms.
- A Jupyter notebook for calculating metrics and generating charts/plots.

### `code/minecraft-code/`
Stores the code written for the Minecraft mod, such as custom commands and items.

### `code/scripts/`
Contains various scripts used throughout the project lifecycle:

- **`block_data_compression.py`** – Converts raw block data from a Minecraft world into a list of frequencies for each layer above sea level.
- **`JSON_automation.mjs`** – Automates the stringification of JSON files (RLE data and processed data) and triggers `block_data_compression.py` when a new raw_data file is detected.
- **`model.js`** – The main NodeJS server handling communication between Google Gemini and Minecraft.
- **`quantitative_analysis.ipynb`** – A Jupyter notebook that compares terrains quantitatively.
- **`screenshot_automation.ahk`** – An AutoHotKey script used for capturing 300 screenshots for model tuning.
- **`screenshot_filtering.py`** – Moves screenshots with insufficient terrain rendering to another folder.
- **`worldgen.ipynb`** – A Jupyter notebook used to generate "random" terrains with constraining parameters.

### `model_outputs/`
A folder containing example responses from the model used in terrain generation.

## Licences
- **Code**: Licensed under the [Apache 2.0 License](LICENCE), allowing modification and redistribution with attribution.  
- **Report & Documentation**: Licensed under the [Creative Commons Attribution 4.0 License](LICENCE_REPORT), permitting sharing and adaptation with proper credit.  

For more details, see the respective licence files in this repository.