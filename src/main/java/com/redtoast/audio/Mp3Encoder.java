package com.redtoast.audio;

import de.sciss.jump3r.mp3.BitStream;
import de.sciss.jump3r.mp3.BRHist;
import de.sciss.jump3r.mp3.GainAnalysis;
import de.sciss.jump3r.mp3.GetAudio;
import de.sciss.jump3r.mp3.ID3Tag;
import de.sciss.jump3r.mp3.Lame;
import de.sciss.jump3r.mp3.LameGlobalFlags;
import de.sciss.jump3r.mp3.MPEGMode;
import de.sciss.jump3r.mp3.Parse;
import de.sciss.jump3r.mp3.Presets;
import de.sciss.jump3r.mp3.PsyModel;
import de.sciss.jump3r.mp3.Quantize;
import de.sciss.jump3r.mp3.QuantizePVT;
import de.sciss.jump3r.mp3.Reservoir;
import de.sciss.jump3r.mp3.Takehiro;
import de.sciss.jump3r.mp3.VBRTag;
import de.sciss.jump3r.mp3.Version;
import de.sciss.jump3r.mpg.Common;
import de.sciss.jump3r.mpg.Interface;
import de.sciss.jump3r.mpg.MPGLib;

public final class Mp3Encoder implements AutoCloseable {
    public static final int SAMPLE_RATE = 44100;

    private final Lame lame;
    private final LameGlobalFlags flags;
    private final byte[] output = new byte[1 << 16];
    private int[] left = new int[16];
    private int[] right = new int[16];
    private static final byte[] EMPTY = new byte[0];

    public Mp3Encoder(int bitrateKbps) {
        lame = new Lame();
        GetAudio gaud = new GetAudio();
        GainAnalysis gain = new GainAnalysis();
        BitStream bs = new BitStream();
        Presets presets = new Presets();
        QuantizePVT quantizePvt = new QuantizePVT();
        Quantize quantize = new Quantize();
        VBRTag vbr = new VBRTag();
        Version version = new Version();
        ID3Tag id3 = new ID3Tag();
        Reservoir reservoir = new Reservoir();
        Takehiro takehiro = new Takehiro();
        Parse parse = new Parse();
        BRHist hist = new BRHist();
        MPGLib mpg = new MPGLib();
        Interface intf = new Interface();
        Common common = new Common();

        lame.setModules(gain, bs, presets, quantizePvt, quantize, vbr, version, id3, mpg);
        bs.setModules(gain, mpg, version, vbr);
        id3.setModules(bs, version);
        presets.setModules(lame);
        quantize.setModules(bs, reservoir, quantizePvt, takehiro);
        quantizePvt.setModules(takehiro, reservoir, new PsyModel());
        reservoir.setModules(bs);
        takehiro.setModules(quantizePvt);
        vbr.setModules(lame, bs, version);
        gaud.setModules(parse, mpg);
        parse.setModules(version, id3, presets);
        mpg.setModules(intf, common);
        intf.setModules(vbr, common);

        flags = lame.lame_init();
        flags.in_samplerate = SAMPLE_RATE;
        flags.out_samplerate = SAMPLE_RATE;
        flags.num_channels = 1;
        flags.brate = bitrateKbps;
        flags.mode = MPEGMode.MONO;
        flags.quality = 5;
        flags.disable_reservoir = true;
        flags.write_id3tag_automatic = false;
        flags.bWriteVbrTag = false;

        if (lame.lame_init_params(flags) < 0) {
            throw new IllegalStateException("Failed to initialise MP3 encoder");
        }
    }

    public byte[] encode(short[] pcm, int offset, int length) {
        if (length <= 0) return EMPTY;
        if (left.length < length) {
            left = new int[length];
            right = new int[length];
        }
        for (int i = 0; i < length; i++) left[i] = pcm[offset + i] << 16;

        int written = lame.lame_encode_buffer_int(flags, left, right, length, output, 0, output.length);
        if (written <= 0) return EMPTY;

        byte[] out = new byte[written];
        System.arraycopy(output, 0, out, 0, written);
        return out;
    }

    @Override
    public void close() {
        lame.lame_close(flags);
    }
}
