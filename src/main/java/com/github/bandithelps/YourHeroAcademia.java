package com.github.bandithelps;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.attributes.QuirkAttributes;
import com.github.bandithelps.attributes.IntelligenceAttributes;
import com.github.bandithelps.attributes.StrengthAttributes;
import com.github.bandithelps.blocks.*;
import com.github.bandithelps.capabilities.body.BodyAttachments;
import com.github.bandithelps.capabilities.dna.DNAAttachments;
import com.github.bandithelps.capabilities.creation.CreationAttachments;
import com.github.bandithelps.capabilities.loadout.AbilityLoadoutAttachments;
import com.github.bandithelps.capabilities.stamina.StaminaAttachments;
import com.github.bandithelps.client.renderers.entity.BlackwhipChainEntityRenderer;
import com.github.bandithelps.client.renderers.entity.BlackwhipEntityRenderer;
import com.github.bandithelps.client.renderers.entity.BlackwhipSegmentEntityRenderer;
import com.github.bandithelps.client.renderers.entity.BlackwhipTossedBlockRenderer;
import com.github.bandithelps.client.renderers.entity.PotionGeneratorEntityRenderer;
import com.github.bandithelps.client.renderers.entity.RgbaDisplayEntityRenderer;
import com.github.bandithelps.client.renderers.entity.CreationProductRenderer;
import com.github.bandithelps.client.renderers.entity.SmokeCanisterProjectileRenderer;
import com.github.bandithelps.commands.*;
import com.github.bandithelps.conditions.ConditionRegister;
import com.github.bandithelps.conditions.cost.CostRegister;
import com.github.bandithelps.values.ValueRegister;
import com.github.bandithelps.effects.ModEffects;
import com.github.bandithelps.entities.ModEntities;
import com.github.bandithelps.entities.PotionEffectGeneratorEntity;
import com.github.bandithelps.creation.CreationCatalog;
import com.github.bandithelps.creation.CreationEnchantCatalog;
import com.github.bandithelps.gene.GeneRegistry;
import com.github.bandithelps.gene.combination.CombinationManager;
import com.github.bandithelps.gui.menu.ModMenus;
import com.github.bandithelps.gui.actions.YhaDialogActions;
import com.github.bandithelps.items.SmokeCanisterItem;
import com.github.bandithelps.items.TissueExtractorItem;
import com.github.bandithelps.items.TissueSampleItem;
import com.github.bandithelps.items.GeneVialItem;
import com.github.bandithelps.items.DNAInjectorItem;
import com.github.bandithelps.network.YhaNetwork;
import com.github.bandithelps.particles.ModParticles;
import com.github.bandithelps.recipes.ModRecipeSerializers;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(YourHeroAcademia.MODID)
@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class YourHeroAcademia {


    public static final String MODID = "yha";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "yourheroacademia" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredItem<BlockItem> TREADMILL_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("treadmill", ModBlocks.TREADMILL_BLOCK);
    public static final DeferredItem<BlockItem> DNA_ANALYZER_ITEM = ITEMS.registerSimpleBlockItem("dna_analyzer", ModBlocks.DNA_ANALYZER);
    public static final DeferredItem<BlockItem> DNA_SPLICER_ITEM = ITEMS.registerSimpleBlockItem("dna_splicer", ModBlocks.DNA_SPLICER);
    public static final DeferredItem<BlockItem> GENE_COMBINER_ITEM = ITEMS.registerSimpleBlockItem("gene_combiner", ModBlocks.GENE_COMBINER);
    public static final DeferredItem<BlockItem> BIO_PRINTER_ITEM = ITEMS.registerSimpleBlockItem("bio_printer", ModBlocks.BIO_PRINTER);
    public static final DeferredItem<BlockItem> RESEARCH_TABLE_ITEM = ITEMS.registerSimpleBlockItem("research_table", ModBlocks.RESEARCH_TABLE);

    public static final DeferredItem<Item> EMPTY_CANISTER = ITEMS.registerSimpleItem("empty_canister");
    public static final DeferredItem<Item> FILLED_SMOKE_CANISTER = ITEMS.register("filled_smoke_canister", () -> new SmokeCanisterItem(new Item.Properties()
            .stacksTo(16)
            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "filled_smoke_canister")))));
    public static final DeferredItem<Item> INFUSED_SMOKE_CANISTER = ITEMS.register("infused_smoke_canister", () -> new SmokeCanisterItem(new Item.Properties()
            .stacksTo(16)
            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "infused_smoke_canister")))));
    public static final DeferredItem<Item> PIPETTE = ITEMS.registerSimpleItem("pipette");
    public static final DeferredItem<Item> TISSUE_EXTRACTOR = ITEMS.register("tissue_extractor", () -> new TissueExtractorItem(new Item.Properties()
            .stacksTo(1)
            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "tissue_extractor")))));
    public static final DeferredItem<Item> TISSUE_SAMPLE = ITEMS.register("tissue_sample", () -> new TissueSampleItem(new Item.Properties()
            .stacksTo(16)
            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "tissue_sample")))));
    public static final DeferredItem<Item> EMPTY_GENE_VIAL = ITEMS.register("empty_gene_vial", () -> new Item(new Item.Properties()
            .stacksTo(16)
            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "empty_gene_vial")))));
    public static final DeferredItem<Item> GENE_VIAL = ITEMS.register("gene_vial", () -> new GeneVialItem(new Item.Properties()
            .stacksTo(1)
            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "gene_vial")))));
    public static final DeferredItem<Item> DNA_INJECTOR = ITEMS.register("dna_injector", () -> new DNAInjectorItem(new Item.Properties()
            .stacksTo(1)
            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "dna_injector")))));
    public static final DeferredItem<Item> GENETIC_SLOP = ITEMS.register("genetic_slop", () -> new Item(new Item.Properties()
            .stacksTo(64)
            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "genetic_slop")))));
    public static final DeferredItem<BlockItem> SAMPLE_REFRIGERATOR = ITEMS.registerSimpleBlockItem("sample_refrigerator", ModBlocks.EXAMPLE_BLOCK);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> YHA_MAIN_TAB = CREATIVE_MODE_TABS.register("yha_main_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.yourheroacademia"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> DNA_ANALYZER_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(DNA_ANALYZER_ITEM.get());
                output.accept(TREADMILL_BLOCK_ITEM.get());
                output.accept(DNA_SPLICER_ITEM.get());
                output.accept(GENE_COMBINER_ITEM.get());
                output.accept(BIO_PRINTER_ITEM.get());
                output.accept(RESEARCH_TABLE_ITEM.get());
                output.accept(EMPTY_CANISTER.get());
                output.accept(FILLED_SMOKE_CANISTER.get());
                output.accept(INFUSED_SMOKE_CANISTER.get());
                output.accept(PIPETTE.get());
                output.accept(TISSUE_EXTRACTOR.get());
                output.accept(TISSUE_SAMPLE.get());
                output.accept(EMPTY_GENE_VIAL.get());
                output.accept(GENE_VIAL.get());
                output.accept(DNA_INJECTOR.get());
                output.accept(GENETIC_SLOP.get());
                output.accept(SAMPLE_REFRIGERATOR.get());
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public YourHeroAcademia(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);


        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        // Custom palladium stuff
        AbilityRegister.ABILITIES.register(modEventBus);
        ConditionRegister.CONDITIONS.register(modEventBus);
        CostRegister.COST_SERIALIZERS.register(modEventBus);
        ValueRegister.VALUES.register(modEventBus);

        YhaDialogActions.ACTIONS.register(modEventBus);

        QuirkAttributes.ATTRIBUTES.register(modEventBus);
        IntelligenceAttributes.ATTRIBUTES.register(modEventBus);
        StrengthAttributes.ATTRIBUTES.register(modEventBus);
        StaminaAttachments.ATTACHMENTS.register(modEventBus);
        BodyAttachments.ATTACHMENTS.register(modEventBus);
        DNAAttachments.ATTACHMENTS.register(modEventBus);
        AbilityLoadoutAttachments.ATTACHMENTS.register(modEventBus);
        CreationAttachments.ATTACHMENTS.register(modEventBus);

        ModEffects.MOD_EFFECTS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModParticles.PARTICLE_TYPES.register(modEventBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (YourHeroAcademia) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
//        NeoForge.EVENT_BUS.register(this);

        modEventBus.addListener(YhaNetwork::registerPayloads);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
//        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("Server Starting...");
        GeneRegistry.getInstance().reload(event.getServer().getResourceManager());
        CreationCatalog.getInstance().reload(event.getServer().getResourceManager());
        CreationEnchantCatalog.getInstance().reload(event.getServer().getResourceManager());
        CombinationManager.rebuildForServer(event.getServer());
    }


    /**
     * Command Registration: Here, we register the first command "/yha" by itself.
     * It defines a new event that we can then assign commands to below to automatically
     * place them as a sub category to the /yha command.
     * @param event
     */
    @SubscribeEvent
    static void registerCommands(RegisterCommandsEvent event) {
        YhaCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    @SubscribeEvent
    static void yhaCommands(RegisterYhaCommandsEvent event) {
        StaminaCommand.register(event.getBuilder(), event.getBuildContext());
        BodyCommand.register(event.getBuilder(), event.getBuildContext());
        BdCommand.register(event.getBuilder(), event.getBuildContext());
        GeneCommand.register(event.getBuilder(), event.getBuildContext());
        BlackwhipCommand.register(event.getBuilder(), event.getBuildContext());
        LoadoutCommand.register(event.getBuilder(), event.getBuildContext());
        TreeEditorCommand.register(event.getBuilder(), event.getBuildContext());
    }

    @SubscribeEvent
    public static void onAttributeCreate(EntityAttributeCreationEvent event) {
        event.put(ModEntities.POTION_GENERATOR.get(), PotionEffectGeneratorEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.POTION_GENERATOR.get(), PotionGeneratorEntityRenderer::new);
        event.registerEntityRenderer(ModEntities.RGBA_DISPLAY.get(), RgbaDisplayEntityRenderer::new);
        event.registerEntityRenderer(ModEntities.SMOKE_CANISTER_PROJECTILE.get(), SmokeCanisterProjectileRenderer::new);
        event.registerEntityRenderer(ModEntities.BLACKWHIP_TOSSED_BLOCK.get(), BlackwhipTossedBlockRenderer::new);
        event.registerEntityRenderer(ModEntities.BLACKWHIP.get(), BlackwhipEntityRenderer::new);
        event.registerEntityRenderer(ModEntities.BLACKWHIP_CHAIN.get(), BlackwhipChainEntityRenderer::new);
        event.registerEntityRenderer(ModEntities.BLACKWHIP_SEGMENT.get(), BlackwhipSegmentEntityRenderer::new);
        event.registerEntityRenderer(ModEntities.CREATION_PRODUCT.get(), CreationProductRenderer::new);
    }
}
