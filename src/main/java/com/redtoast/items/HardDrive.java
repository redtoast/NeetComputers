package com.redtoast.items;

import com.redtoast.neet.NeetComputersServer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class HardDrive extends Disk{
    public HardDrive(Settings settings, String key) {
        super(settings, key);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        if (Boolean.TRUE.equals(stack.get(NeetComputersServer.BOOTABLE_COMPONENT))) tooltip.add(Text.of("Can be crafted into a computer").copy().formatted(Formatting.GRAY, Formatting.ITALIC));
        tooltip.add(Text.of("Disk ID: " + stack.get(NeetComputersServer.DISKID_COMPONENT)).copy().formatted(Formatting.GRAY, Formatting.ITALIC));
        tooltip.add(Text.of("From: " + stack.get(NeetComputersServer.UUID_COMPONENT)).copy().formatted(Formatting.GRAY, Formatting.ITALIC));
        tooltip.add(Text.translatable("itemGroup.neetcomputers.main_item_group").formatted(Formatting.BLUE));
    }
}
