package com.redtoast.blocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.component.ComponentType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.UUID;

public record ComputerDataComponent(boolean hasFiles, int address, boolean isOn, UUID id, String template, String diskId) {
    public static final Codec<ComputerDataComponent> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            Codec.BOOL.fieldOf("hasFiles").forGetter(ComputerDataComponent::hasFiles),
            Codec.INT.fieldOf("address").forGetter(ComputerDataComponent::address),
            Codec.BOOL.fieldOf("isOn").forGetter(ComputerDataComponent::isOn),
            Codec.STRING.fieldOf("id").forGetter(ComputerDataComponent::getId),
            Codec.STRING.fieldOf("template").forGetter(ComputerDataComponent::template),
            Codec.STRING.fieldOf("diskId").forGetter(ComputerDataComponent::diskId)
            ).apply(builder, ComputerDataComponent::reconstruct));
    public static ComponentType<ComputerDataComponent> TYPE;

    public static ComputerDataComponent reconstruct(boolean hasFiles, int address, boolean isOn, String idSerial, String template, String diskId){
        return new ComputerDataComponent(hasFiles, address, isOn, UUID.fromString(idSerial), template, diskId);
    }

    private String getId(){
        return id().toString();
    }
}
