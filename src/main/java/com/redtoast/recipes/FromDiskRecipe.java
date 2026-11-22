package com.redtoast.recipes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.redtoast.blocks.ComputerDataComponent;
import com.redtoast.items.generics.DiskItem;
import com.redtoast.neet.NeetComputersServer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

import java.util.UUID;

public class FromDiskRecipe extends ShapedRecipe {
    private final RawShapedRecipe raw;

    public FromDiskRecipe(String group, CraftingRecipeCategory category, RawShapedRecipe raw, ItemStack result, boolean showNotification) {
        super(group, category, raw, result, showNotification);
        this.raw = raw;
    }

    public RawShapedRecipe getRaw() {
        return raw;
    }

    @Override
    public boolean matches(CraftingRecipeInput craftingRecipeInput, World world) {
        for (int i = 0; i < craftingRecipeInput.getHeight(); i++) {
            for (int j = 0; j < craftingRecipeInput.getWidth(); j++) {
                Ingredient ingredient = getIngredients().get(j + i * craftingRecipeInput.getWidth());

                ItemStack itemStack = craftingRecipeInput.getStackInSlot(j, i);
                if (i==1 && j==1 && !(!itemStack.isEmpty() && itemStack.getItem() instanceof DiskItem diskItem && diskItem.isBootable(itemStack, null))) {
                    return false;
                }
                if (!ingredient.test(itemStack)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput craftingRecipeInput, RegistryWrapper.WrapperLookup wrapperLookup) {
        ItemStack output = super.craft(craftingRecipeInput, wrapperLookup);
        if (craftingRecipeInput.getStacks().size()>=4) {
            ItemStack disk = craftingRecipeInput.getStackInSlot(1,1);
            if (!disk.isEmpty() && disk.getItem() instanceof DiskItem diskItem) {
                output.set(ComputerDataComponent.TYPE, new ComputerDataComponent(true, diskItem.getAddress(disk, null), false, UUID.randomUUID(), disk.get(NeetComputersServer.TEMPLATE_COMPONENT),UUID.randomUUID().toString()));
            }
        }
        return output;
    }

    @Override
    public RecipeSerializer<? extends Recipe<CraftingRecipeInput>> getSerializer() {
        return NeetComputersServer.OPTIONAL_DISK_SERIALIZER;
    }

    public static class Serializer implements RecipeSerializer<FromDiskRecipe> {
        public static final MapCodec<FromDiskRecipe> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                                Codec.STRING.optionalFieldOf("group", "").forGetter(ShapedRecipe::getGroup),
                                CraftingRecipeCategory.CODEC.fieldOf("category").orElse(CraftingRecipeCategory.MISC).forGetter(ShapedRecipe::getCategory),
                                RawShapedRecipe.CODEC.forGetter(recipe -> recipe.raw),
                                ItemStack.VALIDATED_CODEC.fieldOf("result").forGetter(recipe -> recipe.getResult(null)),
                                Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(ShapedRecipe::showNotification)
                        )
                        .apply(instance, FromDiskRecipe::new)
        );
        public static final PacketCodec<RegistryByteBuf, FromDiskRecipe> PACKET_CODEC = PacketCodec.ofStatic(
                FromDiskRecipe.Serializer::write, FromDiskRecipe.Serializer::read
        );

        @Override
        public MapCodec<FromDiskRecipe> codec() {
            return CODEC;
        }

        @Override
        public PacketCodec<RegistryByteBuf, FromDiskRecipe> packetCodec() {
            return PACKET_CODEC;
        }

        private static FromDiskRecipe read(RegistryByteBuf buf) {
            String string = buf.readString();
            CraftingRecipeCategory craftingRecipeCategory = buf.readEnumConstant(CraftingRecipeCategory.class);
            RawShapedRecipe rawShapedRecipe = RawShapedRecipe.PACKET_CODEC.decode(buf);
            ItemStack itemStack = ItemStack.PACKET_CODEC.decode(buf);
            boolean bl = buf.readBoolean();
            return new FromDiskRecipe(string, craftingRecipeCategory, rawShapedRecipe, itemStack, bl);
        }

        private static void write(RegistryByteBuf buf, FromDiskRecipe recipe) {
            buf.writeString(recipe.getGroup());
            buf.writeEnumConstant(recipe.getCategory());
            RawShapedRecipe.PACKET_CODEC.encode(buf, recipe.raw);
            ItemStack.PACKET_CODEC.encode(buf, recipe.getResult(null));
            buf.writeBoolean(recipe.showNotification());
        }
    }
}
