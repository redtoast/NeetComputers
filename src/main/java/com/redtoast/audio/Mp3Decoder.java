package com.redtoast.audio;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

public final class Mp3Decoder {

    public short @Nullable [] decode(byte[] mp3) {
        if (mp3.length == 0) return null;

        Decoder decoder = new Decoder();
        Bitstream bitstream = new Bitstream(new ByteArrayInputStream(mp3));
        short[] growing = null;
        int filled = 0;
        try {
            Header header;
            while ((header = bitstream.readFrame()) != null) {
                SampleBuffer samples = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                int count = samples.getBufferLength();
                if (growing == null) {
                    growing = new short[Math.max(count, 1024)];
                } else if (filled + count > growing.length) {
                    growing = Arrays.copyOf(growing, filled + Math.max(count, growing.length));
                }
                System.arraycopy(samples.getBuffer(), 0, growing, filled, count);
                filled += count;
                bitstream.closeFrame();
            }
        } catch (BitstreamException | DecoderException | RuntimeException e) {

        } finally {
            try {
                bitstream.close();
            } catch (BitstreamException ignored) {
            }
        }
        return filled == 0 ? null : Arrays.copyOf(growing, filled);
    }
}
