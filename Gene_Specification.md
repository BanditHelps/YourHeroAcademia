The following document is intended to describe a full feature set for a Minecraft 26.1 neoforge mod. This is a gameplay schematic that shows the full gameplay process. Use it to not only create a detailed specification guide, but also to help delegate tasks to sub agents with the goal of achieving a working implementation.
## DNA
Most mobs in the world are going to generate with an associated DNA. This is not determined when the entities are created, rather it is determined procedurally based on the UUID of the entity. This means that extracting the DNA of the same entity will give the same DNA. Players however, are excluded from this, as their DNA can be overridden. Each DNA is made up of the following:
- Name indicating the UUID's DNA
- The uuid it is associated with
- A list of Genes apart of the DNA sequence
- The time in game ticks that the DNA was harvested (after it has been extracted)

The DNA strands do not exist in the world until it has been extracted from a mob or player. This extraction is done using the "Tissue Extractor" item. This will store the sample and the generated DNA inside of the extractor for later processing. 

Each mob/player's DNA can hold up to 6 genes inside of it.

## Genes
Each DNA sample will have an associated set of Genes apart of it. The main idea is that these genes will each pertain to a certain ability, stat change, or even power.

A gene is made up of the following:
- Randomly generated Alpha-Character Only name (5 digits)
- Category
- Gene Type (what the gene actually does)
- Description
- Quality - A value representing how potent the gene is. Often used in the implementation of a gene to scale the power, similar to a potion amplifier
- Side Effect(s) - One or more gene types that are "bonded" to this gene. Most of the time, these are negative side effects of the gene. These are not unique to the gene type, rather they are per instance of the gene. For example, if a sample is extracted from a villager and contains Gene A with the side effects of permanent hunger, any time Gene A is extracted from that villager, it will have the same effects. 

To create the potential options for each gene, the gene types and side effects will be data driven, meaning that they can be added via data packs. 

### Gene Categories
There are blank categories of genes:
- Builder - These genes do not do anything functional, rather they are used in later processes for combining individual genes together in an attempt to mutate them into a different gene.
- Attribute - These genes modify a certain attribute of the entity player. Things like speed, jump boost, etc.
- Resistance - Genes related to increasing the player's resistance to certain types of damages, temperatures, effects, etc.
- Cosmetic - Genes that purely change the cosmetics of the player. Things like making them glow, making them taller, etc.
- Ability - These genes do not give full on powers, rather they unlock a special ability that is smaller in scale and can't be classified as a full power. Things like double jumping,  night vision, etc. Most of these will be granted via the Gene Power that all players will have (similar to how the quirk.json power controls every base player's physical stats)
- Quirk - Genes that lead to full on quirks, or powers. (This is a My Hero Academia themed mod). The idea is that these are the best version of the genes, requiring experimentation to achieve. They grant one or more powers to the player.

### Side Effects
Side effects are not to be data driven, since they are a little more complicated to implement. Almost any category of gene can spawn with a side effect, although it is a small (configurable) chance. The exempt categories are the Builder and Cosmetic categories. The side effects should be implemented in a way that it is easy to not only determine what side effects the player has in their DNA, but also to add more side effects in code later. Ideas for side effects are as follows:
- Small Lungs - player loses more oxygen faster than normal
- Brittle Bones - Take more damage than normal
- Meat Allergy - Eating any sort of meat food gives the player severe hunger instead
- Fruit Allergy - Eating any sort of fruit gives the player severe hunger instead
- Vegetable Allergy - Eating any sort of vegetable gives the player severe hunger instead
- Spontaneous Combustion - Player has a higher than 0 chance to burst into flames
- Hallucinations - Player will occasionally see creepers/mobs that are not there
- Quirk Fatigue - Player will lose 1 to their quirk factor attribute (min of 0)
- Heavy Bones - Gives the player a permanent mining fatique. Sink in water.
- Vertigo - Gives the player severe nausea above a certain mining level
- Hydrophobia - Gives slowness whenever getting wet via water or rain

This is not an exhaustive list, but the implementation needs to be done in a way that adding more is easy. Also, it should be as optimized as possible, maybe using a sort of event system to determine if a player is affected by a certain side effect or not.

### Gene Data Definition
As I said before, the genes need to be data driven, meaning they are to be defined via data pack files. A valid gene json format should have the following properties:
- id: the Identifier of the gene. ex: modid:gene.alpha
- category: a string placing it in one of the aforementioned categories
- rarity: used to determine how common the gene is found/generated. [common, uncommon, rare, epic, legendary]
- quality_range: An array where the first element is the lowest quality that the gene can spawn with, and the second element is the highest quality the gene can spawn with. This is randomly chosen when the gene is created.
- combinable: a boolean indicating whether this gene can be created through the combination of 2 or more other genes.
- description: A short string to describe what the gene does.
- mobs: an array of Identifiers that pertain to minecraft mobs. These represents what mobs the gene could possibly be extracted from. If no mobs are provided, it will not spawn in the wild, and needs to be fused together to be created. (see Gene Fusing)
- combination: An object that describes the required genetic components to combine together to create this gene. Only needed when "combinable" is true. Here is an example that allows the user to create a new gene if they combine the gene "minor_object_attraction" with a minimum quality of 50, with a builder gene:
  "combination": {  
  "requires": [  
    { "id": "modid:gene.minor_object_attraction", "min_quality": 50 }  
  ],  
  "builder": { "count": 1, "min_quality": 50 },
	"success_rate": 33
	}

What is important here is that "builder" is generic and applies to the entire category instead of a specific gene. This is because any gene that has a combination that uses a builder, will be randomized per world. See the Gene Fusing section for more detail.

### Gene Fusing
In order to incentivize exploring the world and experimenting, not all genes will be obtainable via a tissue extractor. Instead, they will be the products of "fusing". Fusing is the act of combining two or more genes together to create a new gene. To keep it interesting between every playthrough, the recipes for the genes will be pseudo-random. To accomplish this, we will use the builder genes. Above in the Gene Data Definition section, you can see there is a property for combination. To make the gene, you see we listed a specific Identifier for one of the components. That one will not change between playthroughs. The "builder" component will though. By defining the builders as generic, and only specifying a count, I want it so that the world itself will decide what builders are needed. This way, each time a new world is generated, it will have a different value for the builder genes, randomizing the recipe some.

To keep track of these recipes, and to ensure that they do not change throughout a playthrough on a single world, it is important that the entire graph of possible genes and gene recipes is generated and mapped out at the first world loading. Storing this graph will not only make lookups for recipes easier, it will also provide a way for server owners to view the "master combination" for everything. It is important that we have debug methods to view this, whether it be a generated markdown file, or stored in some other way.

## Blocks/Mechanics
The experimentation with the genes is the most important part, as I want players to be able to do experiments on themselves to modify their genome. To do this, they will need a few new machines.

### DNA Analyzer
This machine has a special hit box that the player can right click with a tissue sample they extracted from a mob. They can then open the special GUI for the machine, and it will show an image of a DNA, with 6 slots for genes. these gene slots could be full or empty, depending on the DNA. 

From this screen, players can hover over the gene slots to see the tooltips for the genes, including their random name, their quality, their category, etc. The only thing the player wouldn't see is the side effects if they are present. If the player wants, they can select the gene slot and go to the rename section. This will rename the gene, removing the random name. Now, anytime that player looks at that gene, (and any further genes extracted from the original target) are going to have the given name, instead of the random generated name.

The player can decide now to extract a gene sequence from the DNA, by toggling on extraction mode (a button press). This extraction depends on the intelligence of the player, which will be a new attribute that doesn't exist yet. If the intelligence is 0, then the player must extract 3 gene slots at a time. They cannot choose a specific gene. This really means they can extract all of the left side, or all of the right side. At higher intelligence, they can extract at smaller levels, such as 2 only, and then the optimal single gene extraction. 

Once the extraction is done, the tissue sample will be destroyed, the gui will close, and a gene vial containing the genome will spawn on top of the gene's location.

### DNA Splicer
The DNA splicer is mainly used to combine any Player DNA with a gene sequence that has been extracted. The idea is the player will use a tissue extractor on themselves to get their DNA. Then, they place both the DNA and the Gene Sequence into the Splicer. Using the GUI, they need to determine where they are going to input their genes, as it is a destructive process. In the example where they extracted a 3 slot gene sequence, they can choose to replace the genes on the left or right side of their DNA. Any slot on the gene sequence that is not empty will REPLACE the conflicting gene in the DNA. Once the splice is complete, it will create an injector for the player. Upon injecting themselves with the injector, their DNA will get replaced with the new DNA.

#### DNA Replacement
When replacing the DNA, this is when the player's will learn about the side effects of the genes they have just injected themselves with. For any gene that had a side effect, it will now appear in the DNA Analyzer when re-examined. For example, in the case of the extraction from the villager, if we looked at a new tissue sample from that villager again, we would see the same information as before, but now with side-effects listed in the tooltip. 

Once a DNA swap has occurred, players are given the DNA Fatigue effect for 20 minutes. This effect cannot be removed by any means. With it active, the player cannot swap their DNA. This prevents quick swaps of DNA.

### Gene Combination Factory
This block is responsible for combining gene's together to attempt to create new genes. For now, this block will not be implemented.

## Gameplay Loop
Here is a run down of the gameplay loop to ensure that all of the steps are known ahead of time, to make sure that planning and implementation of the infrastructure is solid, and matches the intended gameplay loop.
1. A player spawns in a world. The config option (spawn_with_dna) is enabled, they will have a random dna sequence assigned to them, which is generated randomly, and abiding by the rarities of the defined genes.
2. A player decides they want to edit their genome. To do that, they first go off to find a villager. They then craft the tissue extractor, and use it to extract a DNA sample from said villager. They now have a DNA Sample labeled the same UUID of the villager.
3. With the sample in their inventory, they now have a limited time, so they store it inside of the "Sample Refrigerator" to ensure the sample does not expire.
4. Now, the player is ready to analyze the sample, so they take it out of the refrigerator and place the sample in the DNA Analyzer. Once they open the GUI and click "Analyze", a short process begins. Once it is done processing, the entire genome is displayed, and it seems that the villager had the builder genes "EWWEW" and "HYRRE", as well as the attribute gene labeled "SRRED". At this point, they cannot see the side effects if they exist, but they can tell that EWWEW is the gamma builder, HYRRE is the alpha builder, and both are at 56% quality. The SRRED reads as an attribute of strength + 2.
5. The player wants to extract the strength + 2 attribute. However, since they only have the basic intelligence, they are only skilled enough to either extract the three gene slots on the left of the DNA, or the 3 on the right. Since the attribute is on the right, they extract that and with it comes the HYRRE gene. The tissue sample is destroyed, but the spliced genome is returned to the player as an item vial.
6. The player now wants to splice that with their own dna. To do this, they first take a sample from themselves. Next, they place it in the DNA Splicer. Now, they can choose what side they want to splice the gene slots they extracted. Either the left or the right side. Once they choose, it creates a new injection, and gives it to the player. Once the player injects it into themself, they gain the abilities of their new DNA.