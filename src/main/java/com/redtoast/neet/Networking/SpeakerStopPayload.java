package com.redtoast.neet.Networking;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record SpeakerStopPayload(UUID source) implements CustomPayload {
    public static final Id<SpeakerStopPayload> ID = new Id<>(Identifier.of("neetcomputers", "speaker_stop"));
    public static final PacketCodec<RegistryByteBuf, SpeakerStopPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeUuid(value.source()),
            (buf) -> new SpeakerStopPayload(buf.readUuid())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
