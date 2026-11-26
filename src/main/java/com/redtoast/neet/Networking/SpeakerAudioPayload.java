package com.redtoast.neet.Networking;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record SpeakerAudioPayload(UUID source, double x, double y, double z, float volume, byte[] audio) implements CustomPayload {
    public static final Id<SpeakerAudioPayload> ID = new Id<>(Identifier.of("neetcomputers", "speaker_audio"));
    public static final PacketCodec<RegistryByteBuf, SpeakerAudioPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeUuid(value.source());
                buf.writeDouble(value.x());
                buf.writeDouble(value.y());
                buf.writeDouble(value.z());
                buf.writeFloat(value.volume());
                buf.writeByteArray(value.audio());
            },
            (buf) -> new SpeakerAudioPayload(
                    buf.readUuid(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readFloat(),
                    buf.readByteArray()
            )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
