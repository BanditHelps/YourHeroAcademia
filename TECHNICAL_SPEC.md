# Technical Implementation Specification - Gene System

## 基因编辑系统兼容性状态

**兼容性版本:** NeoForge 1.26.1
**最后更新状态:** BUILD SUCCESSFUL

## 1. Java Package Structure and Class Organization

### Package Hierarchy

```
com.github.bandithelps.genes
├── data/                    # Gene data registries and loaders
│   ├── GeneRegistry.java   # Central registry for gene definitions
│   ├── GeneLoader.java     # Data pack loader for gene JSONs
│   └── GeneDefinition.java # Immutable gene data from JSON
├── dna/                    # Core DNA and Gene classes
│   ├── DNA.java           # DNA container with 6 gene slots
│   ├── Gene.java          # Individual gene instance
│   ├── GeneInstance.java # Runtime gene with quality/side effects
│   └── DNAGenerator.java  # Procedural DNA generation from UUID
├── category/               # Gene categories
│   ├── GeneCategory.java # Enum for 6 gene categories
│   └── GeneRarity.java  # Rarity enum for gene generation
├── sideeffect/            # Side effect system
│   ├── SideEffect.java    # Base side effect interface
│   ├── SideEffectRegistry.java # Register for side effects
│   └── impl/            # Concrete side effect implementations
│       ├── SmallLungsEffect.java
│       ├── BrittleBonesEffect.java
│       ├── MeatAllergyEffect.java
│       ├── FruitAllergyEffect.java
│       ├── VegetableAllergyEffect.java
│       ├── SpontaneousCombustionEffect.java
│       ├── HallucinationsEffect.java
│       ├── QuirkFatigueEffect.java
│       ├── HeavyBonesEffect.java
│       ├── VertigoEffect.java
│       └── HydrophobiaEffect.java
├── type/                  # Gene type system
│   ├── GeneType.java     # GeneType enum with definitions
│   └── GeneTypeRegistry.java # Registry for data-driven types
├── combination/           # Gene fusing mechanics
│   ├── CombinationGraph.java # World-level recipe graph
│   ├── CombinationRecipe.java # Recipe definition
│   └── CombinationManager.java # Recipe execution
├── blocks/               # Gene system blocks
│   ├── DNAAnalyzerBlock.java
│   ├── DNAAnalyzerBlockEvents.java
│   ├── DNASplicerBlock.java
│   ├── DNASplicerBlockEvents.java
│   └── SampleRefrigeratorBlock.java
├── items/                # Gene system items
│   ├── TissueExtractorItem.java
│   ├── TissueSampleItem.java
│   ├── GeneVialItem.java
│   ├── DNAInjectorItem.java
│   └── SampleRefrigeratorItem.java
├── containers/            # NBT containers
│   ├── GeneContainer.java # Item container for gene data
│   └── DNAContainer.java # Item container for DNA data
├── capabilities/          # Player DNA capability
│   ├── DNAAttachments.java # DeferredRegister for attachments
│   ├── IDNAData.java    # Capability interface
│   └── DNAData.java    # Capability implementation
├── gui/                 # GUI screens
│   ├── screens/
│   │   ├── DNAAnalyzerScreen.java
│   │   ├── DNASplicerScreen.java
│   │   └── GeneInfoScreen.java
│   └── components/
│       ├── GeneSlotComponent.java
│       ├── DNAPreviewComponent.java
│       └── ExtractionControlsComponent.java
├── events/               # Player-side event handlers
│   └── GeneEffectEvents.java # Apply gene effects on player
└── util/                # Utilities
    ├── DNAUtil.java
    └── GeneUtil.java
```

## 2. Data Model Definitions

### 2.1 DNA Class
**File:** `src/main/java/com/github/bandithelps/genes/dna/DNA.java`

```java
public class DNA {
    private final UUID sourceUUID;
    private final List<GeneInstance> genes = new ArrayList<>(6);
    private final long harvestTick;
    private String customName; // Null until renamed via Analyzer

    // Fixed 6-slot array structure: indices 0-2 (left), 3-5 (right)
    // Getters/setters for each slot position
    // NBT serialization for item storage
}
```

### 2.2 GeneInstance Class
**File:** `src/main/java/com/github/bandithelps/genes/dna/GeneInstance.java`

```java
public class GeneInstance {
    private final String name; // 5-character alpha or custom name
    private final GeneDefinition definition;
    private final int quality; // 0-100 scale
    private final List<SideEffect> sideEffects;
    private final boolean isCustomNamed;

    // Apply effects to player via capability
    // Get tooltips for display
}
```

### 2.3 GeneDefinition Class
**File:** `src/main/java/com/github/bandithelps/genes/data/GeneDefinition.java`

```java
public class GeneDefinition {
    private final Identifier id;
    private final GeneCategory category;
    private final GeneRarity rarity;
    private final int[] qualityRange; // [min, max]
    private final boolean combinable;
    private final String description;
    private final List<Identifier> mobs; // Mob sources
    private final CombinationConfig combination; // If combinable

    // From JSON loader
}
```

### 2.4 GeneCategory Enum
**File:** `src/main/java/com/github/bandithelps/genes/category/GeneCategory.java`

```java
public enum GeneCategory {
    BUILDER,      // Used for combination recipes
    ATTRIBUTE,    // Stat modifiers
    RESISTANCE,   // Damage/effect resistance
    COSMETIC,     // Visual changes
    ABILITY,      // Small abilities
    QUIRK         // Full powers

    // Localized name, color for GUI
}
```

### 2.5 GeneRarity Enum
**File:** `src/main/java/com/github/bandithelps/genes/category/GeneRarity.java`

```java
public enum GeneRarity {
    COMMON(60),
    UNCOMMON(25),
    RARE(10),
    EPIC(4),
    LEGENDARY(1);

    private final int weight;

    // Used in weighted random generation
}
```

### 2.6 SideEffect System
**File:** `src/main/java/com/github/bandithelps/genes/sideeffect/SideEffect.java`

```java
public interface SideEffect {
    Identifier getId();
    Component getDisplayName();

    // Called on DNA swap to apply to player
    void apply(Player player, int quality);
    void remove(Player player);

    // Active check for optimization
    boolean isActive(Player player);
}
```

**Registry Pattern (follows QuirkAttributes):**
- `SideEffectRegistry.java` - DeferredRegister<SideEffect>
- Side effects registered statically in concrete classes

## 3. Block/Item Registration Patterns

### 3.1 Block Registration (in YourHeroAcademia.java)

```java
// Add to existing BLOCKS register
public static final DeferredBlock<Block> DNA_ANALYZER_BLOCK = BLOCKS.registerBlock(
    "dna_analyzer",
    DNAAnalyzerBlock::new,
    p -> p.mapColor(MapColor.METAL)
        .strength(2.5F)
        .sound(SoundType.METAL)
        .noOcclusion()
);

public static final DeferredBlock<Block> DNA_SPLICER_BLOCK = BLOCKS.registerBlock(
    "dna_splicer",
    DNASplicerBlock::new,
    p -> p.mapColor(MapColor.METAL)
        .strength(2.5F)
        .sound(SoundType.METAL)
        .noOcclusion()
);

public static final DeferredBlock<Block> SAMPLE_REFRIGERATOR_BLOCK = BLOCKS.registerBlock(
    "sample_refrigerator",
    SampleRefrigeratorBlock::new,
    p -> p.mapColor(MapColor.ICE)
        .strength(1.5F)
        .sound(SoundType.METAL)
);
```

### 3.2 Item Registration (in YourHeroAcademia.java)

```java
// Add to existing ITEMS register
public static final DeferredItem<Item> TISSUE_EXTRACTOR = ITEMS.registerItem(
    "tissue_extractor",
    props -> new TissueExtractorItem(props durability(100))
);

public static final DeferredItem<Item> TISSUE_SAMPLE = ITEMS.registerSimpleItem("tissue_sample");
public static final DeferredItem<Item> GENE_VIAL = ITEMS.registerSimpleItem("gene_vial");
public static final DeferredItem<Item> DNA_INJECTOR = ITEMS.registerSimpleItem("dna_injector");
public static final DeferredItem<Item> SAMPLE_REFRIGERATOR = ITEMS.registerSimpleBlockItem("sample_refrigerator", SAMPLE_REFRIGERATOR_BLOCK);
```

### 3.3 Item Classes

**TissueExtractorItem.java:**
```java
public class TissueExtractorItem extends Item {
    // Right-click on living entity extracts DNA
    // Stores in item NBT: sourceUUID, DNA data
    // Durability tracks remaining uses
}
```

**TissueSampleItem.java:**
```java
public class TissueSampleItem extends Item {
    // ContainerItem for DNA
    // Stores DNA in ItemStack NBT
    // Expiration based on harvest tick
}
```

## 4. GUI Screen Implementations

### 4.1 DNAAnalyzerScreen
**File:** `src/main/java/com/github/bandithelps/genes/gui/screens/DNAAnalyzerScreen.java`

**Features:**
- Displays DNA with 6 gene slots (2 rows of 3)
- Hover tooltips showing: name, category, type, quality, description
- "Analyze" button - shows full genome
- "Extract Left" / "Extract Right" buttons based on intelligence
- "Rename" button for custom naming
- Extraction mode toggle

**Client-Server Pattern:**
- Use YhaNetwork for server communication
- OpenGeneExperimentScreenPayload pattern for screen open
- Custom payloads for extraction actions

### 4.2 DNASplicerScreen
**File:** `src/main/java/com/github/bandithelps/genes/gui/screens/DNASplicerScreen.java`

**Features:**
- Player DNA display (left side)
- Gene sequence display (right side)
- "Splice Left" / "Splice Right" buttons
- Preview of resulting DNA
- Inject button to create DNAInjector

### 4.3 GeneInfoScreen (Tooltip popup)
- Dedicated screen for gene details
- Shows side effects when revealed

## 5. Network Payloads Required

### 5.1 New Payloads (add to YhaNetwork.java)

```java
// DNA data sync to client
public record DNASyncPayload(CompoundTag dnaTag) implements CustomPacketPayload {
    public static final Type<TYPE> TYPE = new Type<>(...);
    public static final StreamCodec<ByteBuf, DNASyncPayload> STREAM_CODEC = ...;
}

// Open DNA Analyzer screen
public record OpenDNAAnalyzerPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<TYPE> TYPE = new Type<>(...);
}

// Request gene extraction
public record GeneExtractionPayload(
    BlockPos machinePos,
    boolean extractLeft,
    boolean extractRight
) implements CustomPacketPayload {
    public static final Type<TYPE> TYPE = new Type<>(...);
}

// Gene extraction result (server to client)
public record GeneExtractionResultPayload(
    boolean success,
    ItemStack geneVial,
    CompoundTag remainingDNA
) implements CustomPacketPayload {
    public static final Type<TYPE> TYPE = new Type<>(...);
}

// DNA splice request
public record DNASplicePayload(
    BlockPos splicerPos,
    boolean spliceLeft,
    boolean spliceRight
) implements CustomPacketPayload {
    public static final Type<TYPE> TYPE = new Type<>(...);
}

// DNA splice result
public record DNASpliceResultPayload(
    boolean success,
    ItemStack injector
) implements CustomPacketPayload {
    public static final Type<TYPE> TYPE = new Type<>(...);
}

// Player DNA changed notification
public record PlayerDNAChangedPayload(CompoundTag newDnaTag) implements CustomPacketPayload {
    public static final Type<TYPE> TYPE = new Type<>(...);
}
```

### 5.2 Registration (in YhaNetwork.java)

```java
public static void registerPayloads(RegisterPayloadHandlersEvent event) {
    PayloadRegistrar registrar = event.registrar("2"); // Version 2

    // Existing payloads...

    // New gene system payloads
    registrar.playToClient(DNASyncPayload.TYPE, ...);
    registrar.playToClient(OpenDNAAnalyzerPayload.TYPE, ...);
    registrar.playToServer(GeneExtractionPayload.TYPE, ...);
    registrar.playToClient(GeneExtractionResultPayload.TYPE, ...);
    registrar.playToServer(DNASplicePayload.TYPE, ...);
    registrar.playToClient(DNASpliceResultPayload.TYPE, ...);
    registrar.playToClient(PlayerDNAChangedPayload.TYPE, ...);
}
```

## 6. Attribute Definitions

### 6.1 Intelligence Attribute
**File:** `src/main/java/com/github/bandithelps/attributes/GeneIntelligence.java`

```java
public final class GeneIntelligence {
    public static final double INTELLIGENCE_DEFAULT = 0.0D;
    public static final double INTELLIGENCE_MIN = 0.0D;
    public static final double INTELLIGENCE_MAX = 100.0D;

    public static final DeferredRegister<Attribute> ATTRIBUTES =
        DeferredRegister.create(Registries.ATTRIBUTE, YourHeroAcademia.MODID);

    public static final DeferredHolder<Attribute, Attribute> INTELLIGENCE = ATTRIBUTES.register(
        "intelligence",
        () -> new RangedAttribute(
            "attribute.yha.intelligence",
            INTELLIGENCE_DEFAULT,
            INTELLIGENCE_MIN,
            INTELLIGENCE_MAX
        ).setSyncable(true)
    );

    @EventBusSubscriber(modid = YourHeroAcademia.MODID)
    public static final class Events {
        @SubscribeEvent
        public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
            event.add(EntityType.PLAYER, INTELLIGENCE);
        }
    }
}
```

### 6.2 Extraction Level Logic

| Intelligence | Extraction Options |
|--------------|------------------|
| 0-9 | Extract 3 slots (full side only) |
| 10-29 | Extract 2 slots |
| 30-59 | Extract 1 slot |
| 60-89 | Full extraction control |
| 90-100 | +10% quality bonus |

## 7. Data-Driven JSON Schema for Genes

### 7.1 Gene Definition Files
**Location:** `src/main/resources/data/yha/genes/[category]/`

**Schema:**
```json
{
  "id": "yha:strength_builder",
  "category": "BUILDER",
  "rarity": "COMMON",
  "quality_range": [30, 70],
  "combinable": false,
  "description": "Used in gene combination recipes",
  "mobs": ["minecraft:zombie", "minecraft:husk"],
  "combination": null
}
```

```json
{
  "id": "yha:minor_strength",
  "category": "ATTRIBUTE",
  "rarity": "UNCOMMON",
  "quality_range": [10, 50],
  "combinable": true,
  "description": "Increases player attack damage by quality * 0.01",
  "mobs": ["minecraft:zombie", "minecraft:cave_spider"],
  "combination": {
    "requires": [
      {"id": "yha:minor_strength_boost", "min_quality": 30}
    ],
    "builder": {"count": 1, "min_quality": 40},
    "success_rate": 33
  }
}
```

### 7.2 Gene Type Definition Files
**Location:** `src/main/resources/data/yha/gene_types/`

```json
{
  "id": "yha:attribute_speed",
  "attribute": "minecraft:movement_speed",
  "operation": "add_value",
  "amount_formula": "quality * 0.001",
  "display_name": "Speed Enhancement",
  "negative_allowed": true
}
```

### 7.3 Side Effect Configuration (not data-driven per spec)
Side effects are code-only for performance. Add new effects by:
1. Create class implementing SideEffect
2. Register in SideEffectRegistry static block

### 7.4 World Combination Graph
**Generated on world load:** `src/main/java/com/github/bandithelps/genes/combination/CombinationGraph.java`

- Builds from all combinable gene definitions
- Resolves builder gene requirements per world seed
- Stores in memory for recipe lookups
- Debug export to JSON

### 7.5 Resistance Gene Payload (Current Backend)
`RESISTANCE` genes are loaded from datapacks through `GeneRegistry` and define one or more entries in `resistances`.

```json
{
  "id": "yha:heat_resistance",
  "category": "RESISTANCE",
  "rarity": "UNCOMMON",
  "qualityRange": [1, 100],
  "combinable": false,
  "resistances": [
    {
      "kind": "FIRE_TICK_DAMAGE",
      "minValue": 0.12,
      "maxValue": 0.55
    }
  ]
}
```

Supported `kind` values:
- `FIRE_TICK_DAMAGE` (requires `minValue`/`maxValue`)
- `POISON_DAMAGE_AVOIDANCE` (requires `minValue`/`maxValue`)
- `WITHER_NULLIFY` (binary; no values required)

## 8. Integration Points with Existing Mod

### 8.1 Capability Integration
**File:** `src/main/java/com/github/bandithelps/capabilities/dna/DNAAttachments.java`

```java
public static final DeferredRegister<Capability<?>> ATTACHMENTS =
    DeferredRegister.create(Registries.CAPABILITY, YourHeroAcademia.MODID);

public static final DeferredHolder<Capability<?>, Capability<IDNAData>> DNA_CAPABILITY =
    ATTACHMENTS.register("dna", () -> DNACapability.INSTANCE);
```

**Attachment to Player:**
- Use existing Player reference pattern from BodyAttachments/StaminaAttachments

### 8.2 DNA Fatigue Effect
**Add to ModEffects.java:**
- DNA_FATIGUE (status effect, 20 minute duration)
- Prevents DNA swapping while active

### 8.3 Gene Integration with Palladium
**Gene effects apply through:**
- Palladium attribute modifiers (for Attribute genes)
- Ability unlocking (for Ability/Quirk genes)
- Direct event listeners (for Resistance genes)

Resistance listener behavior currently includes:
- fire tick reduction only for `minecraft:on_fire` (does not affect lava)
- poison tick mitigation via quality-scaled random avoidance (capped below 100%)
- wither nullification by rejecting wither effect application

### 8.4 Config Options (add to Config.java)
```java
public static final ModConfigSpec.BooleanValue SPAWN_WITH_DNA = BUILDER
    .comment("Whether players spawn with random DNA")
    .define("spawnWithDNA", true);

public static final ModConfigSpec.FloatValue SIDE_EFFECT_CHANCE = BUILDER
    .comment("Chance for a gene to spawn with a side effect (0.0-1.0)")
    .define("sideEffectChance", 0.15F);

public static final ModConfigSpec.IntValue DNA_EXPIRATION_TICKS = BUILDER
    .comment("Ticks before tissue sample expires (default 6000 = 5 minutes)")
    .define("dnaExpirationTicks", 6000);
```

### 8.5 Recipe Integration
**DNA Splicer recipe:** Use existing recipe system
- Input: Tissue Sample + Gene Vial
- Output: DNA Injector

## 9. Implementation Priority Order

### Phase 1: Core DNA System
1. GeneDefinition + GeneLoader
2. DNA class + DNAGenerator
3. GeneCategory + GeneRarity enums
4. Basic capability attachment

### Phase 2: Items and Extraction
5. TissueExtractorItem
6. TissueSampleItem with NBT storage
7. DNA data sync payload

### Phase 3: DNA Analyzer
8. DNAAnalyzerBlock
9. DNAAnalyzerScreen
10. Gene extraction logic
11. GeneVialItem

### Phase 4: Side Effects
12. SideEffect interface
13. Initial 4-5 side effects
14. SideEffectRegistry

### Phase 5: DNA Splicer
15. DNASplicerBlock
16. DNASplicerScreen
17. DNAInjectorItem
18. DNA fatigue effect

### Phase 6: Intelligence Attribute
19. GeneIntelligence attribute
20. Extraction level logic

### Phase 7: Gene Combination (Future)
21. CombinationGraph
22. GeneCombinationFactory (not in initial spec)

### Phase 8: Additional Side Effects
23. Remaining 6-7 side effects per spec
