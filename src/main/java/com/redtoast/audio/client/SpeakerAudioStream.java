package com.redtoast.audio.client;

import com.redtoast.audio.Mp3Decoder;
import net.minecraft.client.sound.AudioStream;
import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Arrays;

public final class SpeakerAudioStream implements AudioStream {
    private static final AudioFormat FORMAT = new AudioFormat(44100f, 16, 1, true, false);

    private static final long PRIME_NANOS = 500_000_000L;

    private final Mp3Decoder decoder = new Mp3Decoder();
    private final ArrayDeque<ByteBuffer> ready = new ArrayDeque<>();
    private byte[] previousPacket = null;

    private long queuedNanos = 0;
    private volatile boolean stopped = false;

    public synchronized void push(byte[] mp3) {
        if (stopped) return;

        short[] pcm;
        if (previousPacket == null) {
            pcm = decoder.decode(mp3);
        } else {
            short[] alone = decoder.decode(mp3);
            if (alone == null || alone.length == 0) {
                previousPacket = mp3;
                return;
            }
            byte[] both = new byte[previousPacket.length + mp3.length];
            System.arraycopy(previousPacket, 0, both, 0, previousPacket.length);
            System.arraycopy(mp3, 0, both, previousPacket.length, mp3.length);
            short[] primed = decoder.decode(both);
            if (primed != null && primed.length >= alone.length) {
                pcm = Arrays.copyOfRange(primed, primed.length - alone.length, primed.length);
            } else {
                pcm = alone;
            }
        }
        previousPacket = mp3;

        if (pcm == null || pcm.length == 0) return;

        ByteBuffer buffer = ByteBuffer.allocate(pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : pcm) buffer.putShort(sample);
        buffer.flip();
        ready.addLast(buffer);
        queuedNanos += nanos(pcm.length);
    }

    public synchronized boolean isPrimed() {
        return queuedNanos >= PRIME_NANOS;
    }

    public synchronized boolean hasAudio() {
        return !ready.isEmpty();
    }

    public void stop() {
        stopped = true;
    }

    public boolean isStopped() {
        return stopped;
    }

    @Override
    public AudioFormat getFormat() {
        return FORMAT;
    }

    @Override
    public synchronized @Nullable ByteBuffer read(int capacity) {
        if (stopped || ready.isEmpty()) return null;

        ByteBuffer out = ByteBuffer.allocateDirect(capacity).order(ByteOrder.LITTLE_ENDIAN);
        while (out.hasRemaining()) {
            ByteBuffer head = ready.peekFirst();
            if (head == null) break;

            int toCopy = Math.min(head.remaining(), out.remaining());
            out.put(out.position(), head, head.position(), toCopy);
            out.position(out.position() + toCopy);
            head.position(head.position() + toCopy);
            queuedNanos -= nanos(toCopy / 2);

            if (head.hasRemaining()) break;
            ready.pollFirst();
        }
        out.flip();
        return out.hasRemaining() ? out : null;
    }

    @Override
    public void close() {
        stop();
        synchronized (this) {
            ready.clear();
            queuedNanos = 0;
        }
    }

    private static long nanos(int samples) {
        return samples * 1_000_000_000L / 44100L;
    }
}
