package org.xlengua.audio.converters;

import fr.delthas.javamp3.Sound;
import io.nayuki.flac.common.StreamInfo;
import io.nayuki.flac.encode.BitOutputStream;
import io.nayuki.flac.encode.FlacEncoder;
import io.nayuki.flac.encode.SubframeEncoder;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Builder to convert from source to target with required parameters
 * (number of channels, sample rate, etc.)
 *
 */
public class Builder {
    private static final int BLOCK_SIZE = 4096;
    private static final int DEFAULT_MAX_SIZE = 1_000_000;

    @FunctionalInterface
    private interface ConverterFunc {
        void apply(List<Integer>[] samples, int blockSize, OutputStream out) throws IOException;
    }

    private InputStream input;
    private int maxSize;
    private StreamInfo streamInfo;
    private Sound sourceMp3;
    private List<Integer>[] samples;
    private Function<InputStream, List<Integer>[]> decoder;
    private ConverterFunc converter;
    private Integer transformSampleDepth;
    private Integer transformSampleRate;

    /**
     * Samples scaling task.
     */
// TODO add low-pass filter
    public Function<Integer, Integer> changeSampleDepth = sample -> {
        int scaleExp = transformSampleDepth - streamInfo.sampleDepth;
        return (int) Math.round(sample * Math.pow(2, scaleExp));
    };

    /**
     * Downsampling task composites with sample scaling (if defined).
     */
    public Function<List<Integer>, List<Integer>> changeSampleDepthAndRate = samples -> {
        Function<Integer, Integer> composite = samples::get;
        if (this.transformSampleDepth != null) {
            composite = composite.andThen(this.changeSampleDepth);
        }

        int downscale = streamInfo.sampleRate / this.transformSampleRate;
        List<Integer> result = new ArrayList<>(samples.size() / downscale);
        for (int i = 0, j = 0; i < samples.size(); i += downscale, j++) {
            result.add(j, composite.apply(i));
        }
        return result;
    };

    public Builder() {
    }

    /**
     * Decodes mp3-stream (defines stream info and reads all raw samples into memory).
     *
     * @param in mp3 stream
     * @return the same builder
     * @throws IOException
     */
    public Builder sourceMp3(InputStream in) throws IOException {
        return sourceMp3(in, DEFAULT_MAX_SIZE);
    }

    /**
     * The same as w/o maxSize.
     *
     * @param in mp3 stream
     * @param maxSize default size of the in-memory arrays (per each channel)
     * @return the same builder
     * @throws IOException
     */
    public Builder sourceMp3(InputStream in, int maxSize) throws IOException {
        this.input = in;
        this.maxSize = maxSize;
        this.streamInfo = new StreamInfo();

        this.sourceMp3 = new Sound(new BufferedInputStream(this.input));
        this.streamInfo.sampleRate = Math.round(sourceMp3.getAudioFormat().getSampleRate());
        this.streamInfo.numChannels = sourceMp3.getAudioFormat().getChannels();
        this.streamInfo.sampleDepth = sourceMp3.getAudioFormat().getSampleSizeInBits();

        this.decoder = this::mp3ToRaw;
        this.samples = this.decoder.apply(sourceMp3);

        return this;
    }

    /**
     * Defines samples scaling.
     *
     * @param sampleDepth new sample depth in [0..32]
     * @return the same builder
     */
    public Builder transformSampleDepth(int sampleDepth) {
        if (sampleDepth < 0 || sampleDepth > 32) {
            throw new IllegalArgumentException();
        }
        this.transformSampleDepth = sampleDepth;

        return this;
    }

    /**
     * Defines samples rate downsampling.
     *
     * @param sampleRate new sample rate
     * @return the same builder
     */
    public Builder downscaleSampleRate(int sampleRate) {
        if (this.streamInfo.sampleRate % sampleRate != 0) {
            throw new IllegalArgumentException();
        }
        this.transformSampleRate = sampleRate;

        return this;
    }

    /**
     * Transforming raw samples read according new parameters
     * (changes channels, makes downsampling, etc.)
     *
     * @param numChannels to write in a target
     * @return the same builder
     */
    public Builder transform(int numChannels) {
        if (numChannels > streamInfo.numChannels) {
            throw new IllegalArgumentException();
        }

        if (this.transformSampleRate != null) {
            Function<Integer, List<Integer>> transform = ch -> this.samples[ch];
            transform = transform.andThen(this.changeSampleDepthAndRate);
            for (int ch = 0; ch < numChannels; ch++) {
                this.samples[ch] = transform.apply(ch);
            }

            if (this.transformSampleDepth != null) {
                this.streamInfo.sampleDepth = this.transformSampleDepth;
            }
            this.streamInfo.sampleRate = this.transformSampleRate;
            this.streamInfo.numSamples = this.samples[0].size();
        } else if (this.transformSampleDepth != null) {
            for (int ch = 0; ch < numChannels; ch++) {
                Function<Integer, Integer> transform = this.samples[ch]::get;
                transform = transform.andThen(this.changeSampleDepth);
                for (int i = 0; i < streamInfo.numSamples; i++) {
                    samples[ch].set(i, transform.apply(i));
                }
            }
            streamInfo.sampleDepth = this.transformSampleDepth;
        }

        if (numChannels < streamInfo.numChannels) {
            List<Integer>[] reduced = new List[numChannels];
            for (int ch = 0; ch < numChannels; ch++) {
                reduced[ch] = this.samples[ch];
            }
            this.samples = reduced;
            streamInfo.numChannels = numChannels;
        }

        return this;
    }

    /**
     * Setting up the target as a WAV-formatted stream.
     *
     * @return the same builder
     */
    public Builder targetWav() {
// TODO implement
        return this;
    }

    /**
     * Setting up the target as a FLAC-formatted stream.
     *
     * @return the same builder
     */
    public Builder targetFlac() {
        this.converter = this::rawToFlac;

        return this;
    }

    /**
     * Stream info for the source (mp3) stream.
     *
     * @return stream info
     * @throws IOException
     */
    public StreamInfo streamInfo() throws IOException {
        this.sourceMp3.close();

        return this.streamInfo;
    }

    /**
     * Converts to target-encoded stream in-memory samples read.
     *
     * @param out target stream
     * @return stream info of encoded target
     * @throws IOException
     */
    public StreamInfo convert(OutputStream out) throws IOException {
        this.converter.apply(samples, BLOCK_SIZE, out);
        this.sourceMp3.close();

        return this.streamInfo;
    }

    private List<Integer>[] mp3ToRaw(InputStream in) {
        int sampleDepth = this.streamInfo.sampleDepth;
        int bytesPerSample = sampleDepth / 8;
        // convert
        List<Integer>[] samples = new List[2];
        for (int i = 0; i < this.streamInfo.numChannels; i++) {
            samples[i] = new ArrayList<>(this.maxSize);
        }
        outer:
        for (;;) {
            for (int ch = 0; ch < this.streamInfo.numChannels; ch++) {
                try {
                    int val = readLittleUint(in, bytesPerSample);
                    if (sampleDepth == 8)
                        val -= 128;
                    else
                        val = (val << (32 - sampleDepth)) >> (32 - sampleDepth);
                    samples[ch].add(val);
                } catch (IOException e) {
                    break outer;
                }
            }
        }

        // set up total amount of samples
        this.streamInfo.numSamples = samples[0].size();
        this.streamInfo.md5Hash = StreamInfo.getMd5Hash(samples, sampleDepth);
        return samples;
    }

    private void rawToFlac(List<Integer>[] samples, int blockSize, OutputStream out) throws IOException {
        // Encode all frames
        SeekableByteArrayOutputStream memOut = new SeekableByteArrayOutputStream();
        BitOutputStream bOut = new BitOutputStream(memOut);
        bOut.writeInt(32, 0x664C6143);
        // skip metadata block by write it with not yet defined values (0s)
        this.streamInfo.write(true, bOut);

        // streamInfo mutated (setting up the metadata)
        new FlacEncoder(this.streamInfo, samples, blockSize, SubframeEncoder.SearchOptions.SUBSET_BEST, bOut);
        bOut.flush();

        // rewrite the stream info metadata block, which is
        // located at a fixed offset in the file by definition
        memOut.seek(4);
        this.streamInfo.write(true, bOut);
        memOut.writeTo(out);
    }

    // Reads n bytes (0 <= n <= 4) from the given stream, interpreting
    // them as an unsigned integer encoded in little endian.
    private static int readLittleUint(InputStream in, int n) throws IOException {
        int result = 0;
        for (int i = 0; i < n; i++) {
            int b = in.read();
            if (b == -1)
                throw new EOFException();
            result |= b << (i * 8);
        }
        return result;
    }


    static class SeekableByteArrayOutputStream extends ByteArrayOutputStream {
        private int seekPos = -1;

        /**
         * Allows to update underlying array.
         *
         * @param pos if -1 nothing happen, otherwise starting to write from the pos
         */
        void seek(int pos) {
            this.seekPos = pos;
        }

        @Override
        public synchronized void write(int b) {
            if (seekPos == -1) {
                super.write(b);
            } else {
                buf[seekPos] = (byte) b;
                seekPos += 1;
            }
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            if (seekPos == -1) {
                super.write(b, off, len);
            } else {
                System.arraycopy(b, off, buf, seekPos, len);
                seekPos += len;
            }
        }
    }
}
