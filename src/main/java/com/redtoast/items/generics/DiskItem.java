package com.redtoast.items.generics;

import com.redtoast.simulation.FS.DiskError;
import com.redtoast.simulation.FS.DiskSystem;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.UUID;

public interface DiskItem {
    int getAddress(ItemStack stack, World world);
    String getDiskId(ItemStack stack);
    String getTemplate(ItemStack stack);
    boolean isDefault(ItemStack stack);
    DiskSystem generateSystem(ItemStack stack, UUID uuid, BlockEntity caller) throws DiskError;
    void markBootable(ItemStack stack, boolean state, BlockEntity caller);
    boolean isBootable(ItemStack stack, World world);
}
