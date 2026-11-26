package com.redtoast.audio;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;

public final class SpeakerStream {

    public static final long BUFFER_NANOS = 3_000_000_000L;

    private static final long LEAD_NANOS = 2_000_000_000L;

    private static final int PACKET_SAMPLES = 1152 * 10;

    private final Mp3Encoder encoder;
    private final ArrayDeque<Packet> packets = new ArrayDeque<>();

    private final short[] pending = new short[PACKET_SAMPLES];
    private int pendingSamples = 0;

    private long queuedNanos = 0;

    private long playoutEndNanos = 0;
    private boolean stopped = true;
    private float volume = 1.0f;

    public SpeakerStream() {
        this(160);
    }

    public SpeakerStream(int bitrateKbps) {
        encoder = new Mp3Encoder(bitrateKbps);
    }

    public synchronized boolean push(short[] pcm, int offset, int length) {
        if (length <= 0) return true;
        long incomingNanos = nanos(length);
        if (queuedNanos + incomingNanos > BUFFER_NANOS) return false;

        stopped = false;
        queuedNanos += incomingNanos;

        int read = offset;
        int remaining = length;
        while (remaining > 0) {
            int take = Math.min(PACKET_SAMPLES - pendingSamples, remaining);
            System.arraycopy(pcm, read, pending, pendingSamples, take);
            pendingSamples += take;
            read += take;
            remaining -= take;
            if (pendingSamples == PACKET_SAMPLES) flushDefaultPacket();
        }
        return true;
    }

    private void flushDefaultPacket() {
        if (pendingSamples == 0) return;
        byte[] mp3 = encoder.encode(pending, 0, pendingSamples);
        if (mp3.length > 0) packets.addLast(new Packet(mp3, nanos(pendingSamples)));
        pendingSamples = 0;
    }

    public synchronized @Nullable Packet poll(long now) {
        if (stopped) return null;

        if (packets.isEmpty()) {
            flushDefaultPacket();
            if (packets.isEmpty()) return null;
        }

        if (now + LEAD_NANOS < playoutEndNanos) return null;

        Packet packet = packets.pollFirst();
        if (packet == null) return null;
        queuedNanos -= packet.nanos;
        if (queuedNanos < 0) queuedNanos = 0;

        long start = Math.max(now, playoutEndNanos);
        playoutEndNanos = start + packet.nanos;
        return packet;
    }

    public synchronized void stop() {
        stopped = true;
        packets.clear();
        pendingSamples = 0;
        queuedNanos = 0;
        playoutEndNanos = 0;
    }

    public synchronized boolean isPlaying(long now) {
        if (stopped) return false;
        return !packets.isEmpty() || pendingSamples > 0 || now < playoutEndNanos;
    }

    public synchronized float getVolume() {
        return volume;
    }

    public synchronized void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(3f, volume));
    }

    public synchronized boolean isStopped() {
        return stopped;
    }

    public synchronized void close() {
        stop();
        encoder.close();
    }

    private static long nanos(int samples) {
        return samples * 1_000_000_000L / Mp3Encoder.SAMPLE_RATE;
    }

    public record Packet(byte[] mp3, long nanos) {
    }
}
