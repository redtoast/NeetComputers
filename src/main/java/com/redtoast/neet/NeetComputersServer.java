package com.redtoast.neet;

import com.mojang.serialization.Codec;
import com.redtoast.APIS.Cryptography.CryptoAPI;
import com.redtoast.APIS.graphics.ScreenAPI;
import com.redtoast.Computer;
import com.redtoast.Connections.Connections;
import com.redtoast.Connections.PipeType;
import com.redtoast.blocks.ColorDisplay.ColorDisplayBlock;
import com.redtoast.blocks.ColorDisplay.ColorDisplayBlockEntity;
import com.redtoast.blocks.ComputerDataComponent;
import com.redtoast.blocks.DesktopComputer.DesktopBlockComputer;
import com.redtoast.blocks.DesktopComputer.DesktopEntityComputer;
import com.redtoast.blocks.DriveBay.DriveBayBlock;
import com.redtoast.blocks.DriveBay.DriveBayBlockEntity;
import com.redtoast.blocks.DynamicLight.DynamicLightBlock;
import com.redtoast.blocks.DynamicLight.DynamicLightBlockEntity;
import com.redtoast.blocks.Keyboard.KeyboardBlock;
import com.redtoast.blocks.Keyboard.KeyboardBlockEntity;
import com.redtoast.blocks.LargeComputer.LargeBlockComputer;
import com.redtoast.blocks.LargeComputer.LargeEntityComputer;
import com.redtoast.blocks.OfficeComputer.OfficeBlockComputer;
import com.redtoast.blocks.OfficeComputer.OfficeEntityComputer;
import com.redtoast.blocks.Generics.ComputerBlock;
import com.redtoast.blocks.RedstoneController.RedstoneControllerBlock;
import com.redtoast.blocks.RedstoneController.RedstoneControllerBlockEntity;
import com.redtoast.blocks.SimpleDisplay.SimpleDisplayBlock;
import com.redtoast.blocks.SimpleDisplay.SimpleDisplayBlockEntity;
import com.redtoast.graphics.screens.*;
import com.redtoast.items.Disk;
import com.redtoast.items.PeripheralTool;
import com.redtoast.items.generics.DisplayPipes;
import com.redtoast.items.networkingCable;
import com.redtoast.items.peripheralCable;
import com.redtoast.APIS.*;
import com.redtoast.Connections.CableManager;
import com.redtoast.neet.Networking.*;
import com.redtoast.neet.config.ConfigLoader;
import com.redtoast.recipes.FromDiskRecipe;
import com.redtoast.recipes.TransitiveSingleRecipe;
import com.redtoast.simulation.FS.DataNode;
import com.redtoast.simulation.APILoader;
import com.redtoast.simulation.base.LanguageGeneric;
import com.redtoast.simulation.events.EventLabel;
import com.redtoast.simulation.value.Value;
import com.redtoast.simulation.value.VarType;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.block.Block;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.component.ComponentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;

public class NeetComputersServer implements ModInitializer {

	//create packet id's and screen handler
	private static final ExtendedScreenHandlerType<RGBScreenHandler, ComputerScreenInitPayload> HANDLER = new ExtendedScreenHandlerType<>(RGBScreenHandler::new, ComputerScreenInitPayload.CODEC);
	private static final ExtendedScreenHandlerType<PeripheralToolScreenHandler, PeripheralToolScreenInitPayload> HANDLER2 = new ExtendedScreenHandlerType<>(PeripheralToolScreenHandler::new, PeripheralToolScreenInitPayload.CODEC);
	private static final ExtendedScreenHandlerType<KeyboardScreenHandler, KeyboardScreenHandler.Payload> HANDLER3 = new ExtendedScreenHandlerType<>(KeyboardScreenHandler::new, KeyboardScreenHandler.Payload.CODEC);
	public static final ScreenHandlerType<RGBScreenHandler> GRAPHICS_SCREEN_HANDLER = BulkRegistry.register("graphics", Registries.SCREEN_HANDLER, HANDLER);
	public static final ScreenHandlerType<PeripheralToolScreenHandler> PERIPHERAL_TOOL_SCREEN_HANDLER = BulkRegistry.register("peripheral_tool", Registries.SCREEN_HANDLER, HANDLER2);
	public static final ScreenHandlerType<DriveBayScreenHandler> DRIVE_BAY_SCREEN_HANDLER = BulkRegistry.register("drive_bay", Registries.SCREEN_HANDLER, new ScreenHandlerType<>(DriveBayScreenHandler::new, FeatureSet.empty()));
	public static final ScreenHandlerType<KeyboardScreenHandler> KEYBOARD_SCREEN_HANDLER = BulkRegistry.register("keyboard", Registries.SCREEN_HANDLER, HANDLER3);
	public static CableManager cableManager = null;
	public static MinecraftServer server = null;
	public static final ComponentType<String> TEMPLATE_COMPONENT = Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of("neetcomputers", "template"), ComponentType.<String>builder().codec(Codec.string(0,15)).build());
	public static final ComponentType<Integer> POINTER_COMPONENT = Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of("neetcomputers", "pointer"), ComponentType.<Integer>builder().codec(Codec.INT).build());
	public static final ComponentType<Boolean> BOOTABLE_COMPONENT = Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of("neetcomputers", "bootable"), ComponentType.<Boolean>builder().codec(Codec.BOOL).build());
	public static final TransitiveSingleRecipe.Serializer TRANSITIVE_SINGLE_SERIALIZER = Registry.register(Registries.RECIPE_SERIALIZER, Identifier.of("neetcomputers", "transitive_single"), new TransitiveSingleRecipe.Serializer());
	public static final FromDiskRecipe.Serializer OPTIONAL_DISK_SERIALIZER = Registry.register(Registries.RECIPE_SERIALIZER, Identifier.of("neetcomputers", "transfer_disk"), new FromDiskRecipe.Serializer());
	private static int nextPointer = -1;
	public static boolean DO_LOGGING = false;

    static {
		Registry.register(Registries.RECIPE_TYPE, Identifier.of("neetcomputers", "transitive_single"), new RecipeType<TransitiveSingleRecipe>(){});
		Registry.register(Registries.RECIPE_TYPE, Identifier.of("neetcomputers", "transfer_disk"), new RecipeType<FromDiskRecipe>(){});
	}

	//internal config
	public static String version = "NeetComputers ";

	//important resources
	public static final Logger LOGGER = LoggerFactory.getLogger("NeetComputers");
	public static ResourceManager datahandling;
	public static Path worldPath = null;
	public static Long timeBenchMark = null;

	//internal language processing
	protected static LanguageGeneric[] LanguageCache;
	private final static LinkedList<LanguageGeneric> languageGenerics = new LinkedList<>();

	@Override
	public void onInitialize() {
		BuildData.updateDat();
		version += BuildData.VERSION;

		LOGGER.info(version+" running using YSLua "+ BuildData.LUA_VERSION);
		if (DO_LOGGING) LOGGER.info("mod build from "+BuildData.BUILD_TIME);

		ServerLifecycleEvents.SERVER_STARTING.register(NeetComputersServer::updateServer);
		ServerLifecycleEvents.SERVER_STARTED.register(server1 -> updateClientPipes());
		ServerTickEvents.START_SERVER_TICK.register(Identifier.of("neetcomputers:tick"), server -> {
			if (timeBenchMark!=null && timeBenchMark + 1000 < System.currentTimeMillis()) {
				updateClientPipes();
				timeBenchMark = System.currentTimeMillis();
			}
		});
		ServerLifecycleEvents.AFTER_SAVE.register((server,a,b) -> {
			File file = worldPath.resolve("neetcomputers/pipes.bin").toFile();
			if (cableManager!=null){
                try {
					try (FileOutputStream stream = new FileOutputStream(file)) {
						try (FileChannel channel = stream.getChannel()){
							channel.write(cableManager.save().nioBuffer());
						}
					}
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
			}
			File pointerPath = worldPath.resolve("neetcomputers/nextAddress.txt").toFile();
			try(FileWriter writer = new FileWriter(pointerPath)) {
				writer.write((((Integer) nextPointer).toString()));
            } catch (IOException ignored) {}
        });

		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of("neetcomputers", "");
			}

			@Override
			public void reload(ResourceManager manager) {
				datahandling = manager;
				DataNode.loadData();
			}
		});

		//register stuff

		BlockSoundGroup computerSound = new BlockSoundGroup(
				1.0F,
				1.0F,
				SoundEvents.BLOCK_NETHERITE_BLOCK_BREAK,
				SoundEvents.BLOCK_NETHERITE_BLOCK_BREAK,
				SoundEvents.BLOCK_COPPER_BULB_PLACE,
				SoundEvents.BLOCK_COPPER_BULB_HIT,
				SoundEvents.BLOCK_ANVIL_FALL
		);

		ComputerDataComponent.TYPE = Registry.register(
				Registries.DATA_COMPONENT_TYPE,
				RegistryKey.of(RegistryKeys.DATA_COMPONENT_TYPE, Identifier.of("neetcomputers", "computerdata")),
				ComponentType.<ComputerDataComponent>builder().codec(ComputerDataComponent.CODEC).build()
		);

		BulkRegistry.setNamespace("neetcomputers");
		Block largeComputer = new LargeBlockComputer(Block.Settings.create().strength(3.0f).hardness(2.0f).sounds(computerSound).luminance(state -> state.get(ComputerBlock.STATE)!=0 && emitLight() ? 8 : 0));
		BulkRegistry.register("large_computer",largeComputer, LargeEntityComputer::new,true);
		RegistryKey<ItemGroup> group = BulkRegistry.registerGroup("main_item_group", BulkRegistry.fetchItemObject("large_computer"));
		BulkRegistry.register(BulkRegistry.fetchItemObject("large_computer"), group);

		Block desktopComputer = new DesktopBlockComputer(Block.Settings.create().strength(2.0f).hardness(1.5f).sounds(computerSound).nonOpaque().luminance(state -> state.get(ComputerBlock.STATE)!=0 && emitLight() ? 5 : 0));
		BulkRegistry.register("desktop_computer",desktopComputer, DesktopEntityComputer::new,true);
		BulkRegistry.register(BulkRegistry.fetchItemObject("desktop_computer"), group);

		Block officeComputer = new OfficeBlockComputer(Block.Settings.create().strength(2.0f).hardness(1.5f).sounds(computerSound).nonOpaque().luminance(state -> state.get(ComputerBlock.STATE)!=0 && emitLight() ? 5 : 0));
		BulkRegistry.register("office_computer",officeComputer, OfficeEntityComputer::new,true);
		BulkRegistry.register(BulkRegistry.fetchItemObject("office_computer"), group);

		Block redstoneController = new RedstoneControllerBlock(Block.Settings.create().strength(3.0f).hardness(2f).sounds(BlockSoundGroup.METAL));
		BulkRegistry.register("redstone_controller",redstoneController, RedstoneControllerBlockEntity::new,true);
		BulkRegistry.register(BulkRegistry.fetchItemObject("redstone_controller"), group);

		Block dynamicLight = new DynamicLightBlock(Block.Settings.create().strength(1.0f).hardness(0.1f).sounds(BlockSoundGroup.GLASS).luminance(state -> state.get(DynamicLightBlock.LUMINANCE)));
		BulkRegistry.register("dynamic_light",dynamicLight, DynamicLightBlockEntity::new,true);
		BulkRegistry.register(BulkRegistry.fetchItemObject("dynamic_light"), group);

		Block diskBay = new DriveBayBlock(Block.Settings.create().strength(1.0f).hardness(0.2f).sounds(BlockSoundGroup.METAL));
		BulkRegistry.register("drive_bay",diskBay, DriveBayBlockEntity::new,true);
		BulkRegistry.register(BulkRegistry.fetchItemObject("drive_bay"), group);

		Item disk = new Disk(new Item.Settings().maxCount(1).component(TEMPLATE_COMPONENT, "blank").component(POINTER_COMPONENT, 0).component(BOOTABLE_COMPONENT, false), "item.neetcomputers.disk");
		BulkRegistry.register("disk", disk);
		BulkRegistry.register(disk, group);

		Item neetosdisk = new Disk(new Item.Settings().maxCount(1).component(TEMPLATE_COMPONENT, "neetos").component(POINTER_COMPONENT, 0).component(BOOTABLE_COMPONENT, true), "item.neetcomputers.neetos_disk");
		BulkRegistry.register("neetos_disk", neetosdisk);
		BulkRegistry.register(neetosdisk, group);

		Item microChip = new Item(new Item.Settings().maxCount(64));
		BulkRegistry.register("micro_chip", microChip);
		BulkRegistry.register(microChip, group);

		Block simpleDisplay = new SimpleDisplayBlock(Block.Settings.create().strength(1.0f).hardness(0.1f).sounds(computerSound).luminance(state -> emitLight() ? 7 : 0));
		BulkRegistry.register("simple_display",simpleDisplay, SimpleDisplayBlockEntity::new,true);
		BulkRegistry.register(BulkRegistry.fetchItemObject("simple_display"), group);

		Block colorDisplay = new ColorDisplayBlock(Block.Settings.create().strength(1.0f).hardness(0.1f).sounds(computerSound).luminance(state -> emitLight() ? 7 : 0));
		BulkRegistry.register("color_display",colorDisplay, ColorDisplayBlockEntity::new,true);
		BulkRegistry.register(BulkRegistry.fetchItemObject("color_display"), group);

		Block KeyboardBlock = new KeyboardBlock(Block.Settings.create().breakInstantly().sounds(BlockSoundGroup.STONE).pistonBehavior(PistonBehavior.DESTROY).noCollision());
		BulkRegistry.register("keyboard",KeyboardBlock,KeyboardBlockEntity::new,true);
		BulkRegistry.register(BulkRegistry.fetchItemObject("keyboard"), group);

		Item peripheralTool = new PeripheralTool(new Item.Settings().maxCount(1));
		BulkRegistry.register("peripheral_tool", peripheralTool);
		BulkRegistry.register(peripheralTool, group);

		Item peripheralCableItem = new peripheralCable(new Item.Settings().maxCount(1));
		BulkRegistry.register("peripheral_cable", peripheralCableItem);
		BulkRegistry.register(peripheralCableItem, group);

		Item networkingCableItem = new networkingCable(new Item.Settings().maxCount(1));
		BulkRegistry.register("networking_cable", networkingCableItem);
		BulkRegistry.register(networkingCableItem, group);

		PayloadTypeRegistry.playC2S().register(EventUploadPayload.ID, EventUploadPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SetPeripheralTagPayload.ID, SetPeripheralTagPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SubmitCommandPayload.ID, SubmitCommandPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ReturnMessagePayload.ID, ReturnMessagePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(BinaryGraphicsPayload.ID, BinaryGraphicsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ColorDisplayGraphicsPayload.ID, ColorDisplayGraphicsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(RGBComputerPayload.ID, RGBComputerPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CloseRGBPayload.ID, CloseRGBPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PipeBufferPayload.ID, PipeBufferPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(EventUploadPayload.ID, (payload, context) -> {
			if ((context.player().currentScreenHandler!=null && context.player().currentScreenHandler.syncId == payload.syncId() && context.player().currentScreenHandler instanceof RGBScreenHandler handler)){
				Computer computer = handler.comp;
				computer.queueEvent(payload.event(), EventLabel.USER);
			}
			if ((context.player().currentScreenHandler!=null && context.player().currentScreenHandler.syncId == payload.syncId() && context.player().currentScreenHandler instanceof KeyboardScreenHandler handler)){
				handler.keyboard.queueEvent(payload.event().getName(), (Object[]) payload.event().getValues().toArray());
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(SetPeripheralTagPayload.ID, (payload, context) -> {
			if ((context.player().currentScreenHandler!=null && context.player().currentScreenHandler.syncId == payload.syncId() && context.player().currentScreenHandler instanceof PeripheralToolScreenHandler handler)){
				handler.setTag(payload.tag());
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(SubmitCommandPayload.ID, (payload, context) -> {
			if ((context.player().currentScreenHandler!=null && context.player().currentScreenHandler.syncId == payload.syncId() && context.player().currentScreenHandler instanceof PeripheralToolScreenHandler handler)){
				String[] parts = payload.command().split("(?!\\B\\\"[^\\\"]*)[, \\.\\(\\)](?![^\\\"]*\\\"\\B)");
				try{
					String functionname = "";
					ArrayList<Value<?>> parameters = new ArrayList<>();
					for (String part : parts){
						if (!part.isBlank()){
							if (functionname.isBlank()){
								if (part.matches("^[a-zA-Z]*\\z")){
									functionname = part;
								}else{
									throw new NumberFormatException();
								}
							}else if (part.equals("true")){
								parameters.add(Value.TRUE);
							}else if(part.equals("false")){
								parameters.add(Value.FALSE);
							}else if(part.matches("^\\\"[^\\\"]*\\\"\\z")){
								parameters.add(Value.of(part.substring(1, part.length()-1)));
							}else{
								parameters.add(Value.of(Integer.parseInt(part)));
							}
						}
                    }
					if (functionname.isBlank()) throw new NumberFormatException();
					Value<?> done = handler.call(functionname, parameters);
					ServerPlayNetworking.send(context.player(), new ReturnMessagePayload(done.isNull() ? "No result" : done.getValue().toString(), done.getType()));
				}catch (NumberFormatException e){
					ServerPlayNetworking.send(context.player(), new ReturnMessagePayload("Invalid Command", VarType.EXCEPTION));
				}
			}
		});

		APILoader.register(ChipAPI::new);
		APILoader.register(IOAPI::new);
		APILoader.register(ScreenAPI::new);
		APILoader.register(EventAPI::new);
		APILoader.register(CryptoAPI::new);
		APILoader.register(InternetAPI::new);
		APILoader.register(FilesAPI::new);
		APILoader.register(HeadsUpAPI::new);
    }

	public static boolean emitLight(){
		return true;//(boolean) ConfigLoader.getClientConfig("computers-emit-light");
	}

	public static void updateClientPipes(){
		if (server==null) return;
		PlayerManager playerManager = server.getPlayerManager();
		for (String name : server.getPlayerNames()){
			ServerPlayerEntity player = playerManager.getPlayer(name);
			boolean isHoldingConnector = false;
			PipeType type = null;
            assert player != null;
            if (player.getOffHandStack().getItem() instanceof DisplayPipes connectorItem){
				isHoldingConnector = true;
				type = connectorItem.getType();
			}else if (player.getMainHandStack().getItem() instanceof DisplayPipes connectorItem){
				isHoldingConnector = true;
				type = connectorItem.getType();
			}
			if (isHoldingConnector){
				sendPipeBufferToPlayer(player, type);
			}
		}
	}

	public static void sendPipeBufferToPlayer(ServerPlayerEntity player, PipeType type){
		CableManager cableManager = CableManager.getInstance();
		if (cableManager==null) return;
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		Position pos = new Position() {
			@Override
			public double getX() {
				return x;
			}

			@Override
			public double getY() {
				return y;
			}

			@Override
			public double getZ() {
				return z;
			}
		};
		BlockPos[] buffer = cableManager.getPipesForRendering(player.getWorld(), type, (blockpos) -> BlockPos.fromLong(blockpos).isWithinDistance(pos, 40));
		CustomPayloadS2CPacket packet = new CustomPayloadS2CPacket(new PipeBufferPayload(type, buffer));
		player.networkHandler.sendPacket(packet);
	}

	public static void registerLanguage(LanguageGeneric language){
		for (LanguageGeneric lang : languageGenerics){
			if (lang.getName().equals(language.getName())){
				return;
			}
		}
		languageGenerics.add(language);
	}

	public static void updateServer(MinecraftServer server) {
		NeetComputersServer.server = server;
		languageGenerics.clear();
		timeBenchMark = System.currentTimeMillis();
		worldPath = server.getSavePath(WorldSavePath.ROOT);
		BinaryLoader.load(datahandling, server.getPath("luaBinaries"));
		ConfigLoader.loadServerConfig(server);
		Connections.COMPATIBILITY = (boolean) ConfigLoader.getServerConfig("cct-compatibility");
		DO_LOGGING = (boolean) ConfigLoader.getServerConfig("log-system-notifications");
		if (!worldPath.resolve("neetcomputers").toFile().exists()){
			if (DO_LOGGING) LOGGER.info("Generating neetcomputers world directory");
			worldPath.resolve("neetcomputers").toFile().mkdir();
		}
		Path pointerPath = worldPath.resolve("neetcomputers/nextAddress.txt");
		boolean success = false;
		if (pointerPath.toFile().exists()){
			try {
				nextPointer = Integer.parseInt(Files.readAllLines(pointerPath).getFirst());
				success = true;
			}catch (Throwable ignored) {
				pointerPath.toFile().delete();
			}
        }
		if (!success) {
			nextPointer = 0;
			for (String path : worldPath.resolve("neetcomputers").toFile().list()) {
				try {
					int num = Integer.parseInt(path);
					if (num > nextPointer) nextPointer = num;
				}catch (Throwable ignored) {}
			}
			nextPointer++;
		}
		File file = worldPath.resolve("neetcomputers/pipes.bin").toFile();
		if (file.exists() && !file.isDirectory()){
			try{
				cableManager = CableManager.read(Unpooled.copiedBuffer(Files.readAllBytes(file.toPath())));
			} catch (Throwable error) {
				cableManager = new CableManager();
			}
        }else{
			cableManager = new CableManager();
		}

		//process lang translaters
		LanguageCache = new LanguageGeneric[languageGenerics.size()];
		for (int i = 0; i < languageGenerics.size(); i++){
			LanguageCache[i] = languageGenerics.get(i);
		}
	}

	public static int getNextPointer() {
        return nextPointer++;
	}

	public static String[] getLangs(){
		String[] output = new String[LanguageCache.length];
		for (int i = 0; i < LanguageCache.length; i++){
			output[i] = LanguageCache[i].getName();
		}
		return output;
	}

	public static LanguageGeneric getLanguage(String lang){
		for (int i = 0; i < LanguageCache.length; i++){
			if (LanguageCache[i].getName().equals(lang)){
				return LanguageCache[i];
			}
		}
		return null;
	}

	public static boolean hasLanguage(String lang){
		for (int i = 0; i < LanguageCache.length; i++){
			if (LanguageCache[i].getName().equals(lang)){
				return true;
			}
		}
		return false;
	}
}