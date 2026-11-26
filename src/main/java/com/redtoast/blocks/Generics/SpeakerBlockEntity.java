package com.redtoast.blocks.Generics;

import com.redtoast.audio.SpeakerStream;
import com.redtoast.neet.Networking.SpeakerAudioPayload;
import com.redtoast.neet.Networking.SpeakerStopPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.List;

public abstract class SpeakerBlockEntity extends BlockEntity {
    private final SpeakerStream speakerStream = new SpeakerStream();

    public SpeakerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public SpeakerStream getSpeakerStream() {
        return speakerStream;
    }

    public abstract void queueEvent(String eventName, Object... args);

    public void tickSpeaker(ServerWorld world) {
        SpeakerStream stream = speakerStream;
        if (stream.isStopped()) return;

        List<ServerPlayerEntity> listeners = playersWatching(world);
        if (listeners.isEmpty()) return;

        long now = System.nanoTime();
        SpeakerStream.Packet packet;
        while ((packet = stream.poll(now)) != null) {
            SpeakerAudioPayload payload = new SpeakerAudioPayload(
                    getSourceUuid(),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    stream.getVolume(),
                    packet.mp3()
            );
            for (ServerPlayerEntity player : listeners) {
                ServerPlayNetworking.send(player, payload);
            }
            queueEvent("speaker_audio_empty");
        }
    }

    private List<ServerPlayerEntity> playersWatching(ServerWorld world) {
        return world.getChunkManager().chunkLoadingManager.getPlayersWatchingChunk(new ChunkPos(getPos()), false);
    }

    public void stopSpeaker() {
        speakerStream.stop();
        if (getWorld() instanceof ServerWorld serverWorld) {
            SpeakerStopPayload payload = new SpeakerStopPayload(getSourceUuid());
            for (ServerPlayerEntity player : playersWatching(serverWorld)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    @Override
    public void markRemoved() {
        super.markRemoved();
        stopSpeaker();
        speakerStream.close();
    }

    public abstract java.util.UUID getSourceUuid();
}
