# How to add a new power

## Step 1: Create the power.json file

File location:  ```resources/data/yha/palladium/power```

This is where all the abilities and information about the power go.

Example format: 
```json
{
  "name": "Creation",
  "parent": "yha:quirk",
  "icon": "minecraft:crafting_table",
  "gui_display_type": "tree",
  "abilities": {
    "quirk_info": {
      "type": "palladium:dummy",
      "properties": {
        "title": "Creation",
        "description": "Convert your bodies internal lipids into real world objects. The only limits are your understanding of the world.",
        "icon": "minecraft:crafting_table",
        "hidden_in_gui": false,
        "hidden_in_bar": true,
        "gui_position": [
          0,
          0
        ]
      }
    }
  }
}
```
Note, it always has the ```quirk_info``` as the first ability.


## Step 2: Create the ui_layout.json

File location: ```resources/assets/yha/palladium/ui_layouts/power```

This file defines how the power upgrade screen looks. There are a few things that it must have, which include the upgrade points and the power tree itself.

Upgrade point widget:
```json
{
      "width": 70,
      "height": 25,
      "padding": 2,
      "background": {
        "type": "palladium:sprite",
        "sprite": "palladium:background/default"
      },
      "widgets": [
        {
          "type": "palladium:icon",
          "icon": "minecraft:command_block",
          "properties": {
            "alignment": "middle_left",
            "x": 4,
            "width": 16,
            "height": 16
          }
        },
        {
          "type": "yha:upgrade_points",
          "properties": {
            "alignment": "middle_left",
            "x": 24,
            "y": 6
          }
        }
      ]
    }
```

Power tree widget:
```json
{
      "width": 250,
      "height": 200,
      "widgets": [
        {
          "type": "yha:zoomable_power_tree",
          "power": "yha:creation",
          "default_zoom": 1.0,
          "background": {
            "type": "palladium:repeating_texture",
            "texture": "minecraft:textures/block/bookshelf.png"
          },
          "properties": {
            "alignment": "center",
            "width": 236,
            "height": 186
          }
        }
      ]
    }
```

Note: We use the ```zoomable_power_tree``` as it is nicer than the default palladium version.

## Step 3: Create the power_renderer

File location: ```resources/assets/yha/palladium/power_renderers```

This is where all of the render layers get registered. Also, for some reason the screen also needs to go in here, so that is the default value.

Example File:
```json
{
  "screen_layout": "yha:power/creation"
}
```
