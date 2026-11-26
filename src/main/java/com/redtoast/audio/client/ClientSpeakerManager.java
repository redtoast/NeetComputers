package com.redtoast.audio.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundManager;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ClientSpeakerManager {
    private static final Map<UUID, Entry> speakers = new HashMap<>();

    private static final long IDLE_TIMEOUT_MS = 4000L;

    private ClientSpeakerManager() {
    }

    public static void onAudio(MinecraftClient client, UUID source, double x, double y, double z, float volume, byte[] mp3) {
        if (client == null) return;
        Entry entry = speakers.get(source);
        if (entry == null || entry.stream.isStopped()) {
            if (entry != null) {
                entry.stream.stop();
                client.getSoundManager().stop(entry.instance);
            }
            entry = new Entry(new SpeakerAudioStream());
            speakers.put(source, entry);
        }
        entry.lastPacket = System.currentTimeMillis();
        entry.instance.setPosition(x, y, z);
        entry.instance.setVolume(volume);
        entry.stream.push(mp3);

        SoundManager soundManager = client.getSoundManager();
        if (entry.stream.isPrimed()) {
            if (entry.playing && !soundManager.isPlaying(entry.instance)) {
                entry.playing = false;
            }
            if (!entry.playing) {
                soundManager.play(entry.instance);
                entry.playing = true;
            }
        }
    }

    public static void onStop(MinecraftClient client, UUID source) {
        Entry entry = speakers.remove(source);
        if (entry == null) return;
        entry.stream.stop();
        if (client != null) client.getSoundManager().stop(entry.instance);
    }

    public static void tick(MinecraftClient client) {
        if (client == null) return;
        long now = System.currentTimeMillis();
        SoundManager soundManager = client.getSoundManager();
        Iterator<Map.Entry<UUID, Entry>> it = speakers.entrySet().iterator();
        while (it.hasNext()) {
            Entry entry = it.next().getValue();
            boolean timedOut = now - entry.lastPacket > IDLE_TIMEOUT_MS;
            boolean finished = entry.playing && !entry.stream.hasAudio()
                    && !soundManager.isPlaying(entry.instance)
                    && now - entry.lastPacket > 1000L;
            if (timedOut || finished) {
                entry.stream.stop();
                if (entry.playing) soundManager.stop(entry.instance);
                it.remove();
            }
        }
    }

    @Nullable
    public static SpeakerSoundInstance getInstance(UUID source) {
        Entry entry = speakers.get(source);
        return entry == null ? null : entry.instance;
    }

    public static void clear() {
        speakers.clear();
    }

    private static final class Entry {
        final SpeakerAudioStream stream;
        final SpeakerSoundInstance instance;
        boolean playing = false;
        long lastPacket = System.currentTimeMillis();

        Entry(SpeakerAudioStream stream) {
            this.stream = stream;
            this.instance = new SpeakerSoundInstance(stream, 0, 0, 0, 1.0f);
        }
    }
}
