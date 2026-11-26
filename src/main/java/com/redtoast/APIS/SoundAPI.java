package com.redtoast.APIS;

import com.redtoast.audio.SpeakerStream;
import com.redtoast.blocks.Generics.SpeakerBlockEntity;
import com.redtoast.simulation.annotations.CanBeNull;
import com.redtoast.simulation.annotations.Exposed;
import com.redtoast.simulation.base.API;
import com.redtoast.simulation.base.ExposedError;
import com.redtoast.simulation.value.ValueTypes.Tuple;

public class SoundAPI implements API {
    private static final int MAX_PCM_BYTES = 44100 * 2 * 10;

    private final SpeakerBlockEntity speakerBlockEntity;

    public SoundAPI(SpeakerBlockEntity speakerBlockEntity) {
        this.speakerBlockEntity = speakerBlockEntity;
    }

    @Override
    public String getLabel() {
        return "sound";
    }

    @Exposed
    public boolean playAudio(byte[] pcm, @CanBeNull Double volume) {
        if (pcm.length == 0) throw new ExposedError("audio buffer is empty");
        if (pcm.length % 2 != 0) throw new ExposedError("audio buffer length must be a multiple of 2 (16-bit samples)");
        if (pcm.length > MAX_PCM_BYTES) throw new ExposedError("audio buffer is too large (max " + MAX_PCM_BYTES + " bytes)");
        int samples = pcm.length / 2;

        SpeakerStream stream = speakerBlockEntity.getSpeakerStream();
        if (volume != null) stream.setVolume(volume.floatValue());

        short[] decoded = new short[samples];
        for (int i = 0; i < samples; i++) {
            int lo = pcm[i * 2] & 0xFF;
            int hi = pcm[i * 2 + 1] & 0xFF;
            decoded[i] = (short) ((hi << 8) | lo);
        }

        boolean accepted = stream.push(decoded, 0, samples);
        return accepted;
    }

    @Exposed
    public float getVolume() {
        return speakerBlockEntity.getSpeakerStream().getVolume();
    }

    @Exposed
    public void setVolume(float volume) {
        speakerBlockEntity.getSpeakerStream().setVolume(volume);
    }

    @Exposed
    public void stop() {
        speakerBlockEntity.getSpeakerStream().stop();
    }

    @Exposed
    public boolean isPlaying() {
        return speakerBlockEntity.getSpeakerStream().isPlaying(System.nanoTime());
    }

    /**
     * @return the PCM format to produce: {@code (sampleRate, bitsPerSample, channels)},
     * i.e. {@code (44100, 16, 1)}
     */
    @Exposed
    public Tuple getFormat() {
        return new Tuple(44100, 16, 1);
    }
}
