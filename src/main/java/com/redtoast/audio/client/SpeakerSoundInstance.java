package com.redtoast.audio.client;

import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.util.concurrent.CompletableFuture;

public final class SpeakerSoundInstance extends AbstractSoundInstance implements FabricSoundInstance {

    public static final Identifier ID = Identifier.of("neetcomputers", "speaker_stream");

    private final SpeakerAudioStream stream;

    public SpeakerSoundInstance(SpeakerAudioStream stream, double x, double y, double z, float volume) {
        super(ID, SoundCategory.RECORDS, Random.create());
        this.stream = stream;
        this.x = x;
        this.y = y;
        this.z = z;
        this.volume = volume;
        this.relative = false;
        this.attenuationType = SoundInstance.AttenuationType.LINEAR;
    }

    void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    void setVolume(float volume) {
        this.volume = volume;
    }

    @Override
    public CompletableFuture<AudioStream> getAudioStream(SoundLoader loader, Identifier id, boolean repeatInstantly) {
        return CompletableFuture.completedFuture(stream);
    }

    @Override
    public boolean isRelative() {
        return false;
    }

    @Override
    public boolean canPlay() {
        return true;
    }
}
