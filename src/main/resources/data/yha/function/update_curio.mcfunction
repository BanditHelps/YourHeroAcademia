# 1. Store the target player's durability score into Minecraft's data storage
execute store result storage yha:temp durability int 1.0 run scoreboard players get @s curio_durability

# 2. Trigger the macro function, forwarding the storage variables into it
function yha:apply_curio_macro with storage yha:temp
