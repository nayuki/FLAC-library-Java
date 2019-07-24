package io.nayuki.flac.decode;


import io.nayuki.flac.common.StreamInfo;
import io.nayuki.flac.encode.BitOutputStream;
import io.nayuki.flac.encode.RandomAccessFileOutputStream;
import jdk.nashorn.internal.ir.annotations.Ignore;
import org.junit.jupiter.api.Test;
import org.xlengua.audio.converters.Builder;

import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;

public class DecoderTest {

    private static final String TRACK07_MP3 = "media/Track07.mp3";
    private static final String TRACK07_FLAC = "media/Track07.flac";
    private static final int MP3_SAMPLE_DEPTH = 16;
    private static final int MP3_SAMPLE_RATE = 44_100;
    private static final int MP3_NUM_CHANNELS = 2;
    private static final int MP3_NUM_SAMPLES = 3_734_784;
    private static final int FLAC_SAMPLE_DEPTH = 16;
    private static final int FLAC_SAMPLE_RATE = 16_000;
    private static final int FLAC_NUM_CHANNELS = 1;
    private static final int FLAC_NUM_SAMPLES = 1_353_814;

    @Test
    void decoderTest() throws URISyntaxException, IOException {
        Path path = Paths.get(getClass().getClassLoader().getResource(TRACK07_FLAC).toURI());
        byte[] mediaBytes = Files.readAllBytes(path);
        BufferedInputStream in = new BufferedInputStream(new ByteArrayInputStream(mediaBytes));
        FlacDecoder decoder = new FlacDecoder(in);
        decoder.readAndHandleMetadataBlock();
        assertNotNull(decoder.streamInfo);
        assertEquals(FLAC_NUM_CHANNELS, decoder.streamInfo.numChannels);
        assertEquals(FLAC_SAMPLE_DEPTH, decoder.streamInfo.sampleDepth);
        assertEquals(FLAC_SAMPLE_RATE, decoder.streamInfo.sampleRate);
        assertEquals(FLAC_NUM_SAMPLES, decoder.streamInfo.numSamples);
    }

    @Test
    void streamInfoTest() throws URISyntaxException, IOException {
        Path path = Paths.get(getClass().getClassLoader().getResource(TRACK07_MP3).toURI());
        byte[] mediaBytes = Files.readAllBytes(path);
        BufferedInputStream in = new BufferedInputStream(new ByteArrayInputStream(mediaBytes));

        StreamInfo streamInfo = new Builder()
                .sourceMp3(in)
                .streamInfo();

        assertEquals(MP3_SAMPLE_DEPTH, streamInfo.sampleDepth);
        assertEquals(MP3_SAMPLE_RATE, streamInfo.sampleRate);
        assertEquals(MP3_NUM_CHANNELS, streamInfo.numChannels);
        assertEquals(MP3_NUM_SAMPLES, streamInfo.numSamples);
    }

    @Test
    void identityConvertTest() throws URISyntaxException, IOException {
        Path path = Paths.get(getClass().getClassLoader().getResource(TRACK07_MP3).toURI());
        byte[] mediaBytes = Files.readAllBytes(path);
        BufferedInputStream in = new BufferedInputStream(new ByteArrayInputStream(mediaBytes));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StreamInfo streamInfo = new Builder()
                .sourceMp3(in)
                .targetFlac()
                .convert(out);

        assertEquals(MP3_SAMPLE_DEPTH, streamInfo.sampleDepth);
        assertEquals(MP3_SAMPLE_RATE, streamInfo.sampleRate);
        assertEquals(MP3_NUM_CHANNELS, streamInfo.numChannels);
        assertEquals(MP3_NUM_SAMPLES, streamInfo.numSamples);

        assertEquals(5_004_961, out.size());
        byte[] raw = out.toByteArray();
        assertEquals(out.size(), raw.length);
        // analyse flac-metadata
        assertArrayEquals(new byte[] {0x66, 0x4C, 0x61, 0x43}, Arrays.copyOfRange(raw, 0, 4));
        // last -- 1, 0 -- streaminfo, 34 -- length in bytes
        assertArrayEquals(new byte[] {-128, 0, 0, 34}, Arrays.copyOfRange(raw, 4, 8));
        assertArrayEquals(new byte[] {  (byte) (streamInfo.minBlockSize >> 8 & 0xff),
                                        (byte) (streamInfo.minBlockSize & 0xff)}, Arrays.copyOfRange(raw, 8, 10));
        assertArrayEquals(new byte[] {  (byte) (streamInfo.maxBlockSize >> 8 & 0xff),
                                        (byte) (streamInfo.maxBlockSize & 0xff)}, Arrays.copyOfRange(raw, 10, 12));
        assertArrayEquals(new byte[] {0, 0, 14}, Arrays.copyOfRange(raw, 12, 15));
        assertArrayEquals(new byte[] {0, 45, 99}, Arrays.copyOfRange(raw, 15, 18));
        BitSet bits = BitSet.valueOf(Arrays.copyOfRange(raw, 18, 27)); // takes 68 bits
        // 20 + 3 bits
        assertArrayEquals(new byte[] {  (byte)(streamInfo.sampleRate >> 12 & 0xf),
                                        (byte)(streamInfo.sampleRate >> 4 & 0xff),
                                        (byte)(((streamInfo.sampleRate & 0xf) << 4) | streamInfo.numChannels)}, bits.get(0, 24).toByteArray());
        // 5 bits
        assertArrayEquals(new byte[] {  (byte)streamInfo.sampleDepth}, bits.get(24, 29).toByteArray());
        // 18 + 18 bits
        assertEquals((byte) 0x38, raw[23]);
        assertEquals((byte) 0xfd, raw[24]);
        assertEquals((byte) 0, raw[25]);
        // end of 4 + 4 + 18 == 26 bytes
        // next 16 bytes is MD5 digest
        assertArrayEquals(new byte[] {(byte) 4, (byte) 123, (byte) -36, (byte) -35}, Arrays.copyOfRange(raw, 26, 30));
    }

    @Test
    void transformSampleDepthTest() throws URISyntaxException, IOException {
        Path path = Paths.get(getClass().getClassLoader().getResource(TRACK07_MP3).toURI());
        byte[] mediaBytes = Files.readAllBytes(path);
        BufferedInputStream in = new BufferedInputStream(new ByteArrayInputStream(mediaBytes));

        Builder builder = new Builder().sourceMp3(in);
        StreamInfo streamInfo = builder.streamInfo();
        assertEquals(MP3_SAMPLE_DEPTH, streamInfo.sampleDepth);

        // downscaling
        builder.sampleDepth(8);
        assertEquals(256, builder.transformSampleDepth.apply(65536));
        assertEquals(1, builder.transformSampleDepth.apply(256));
        assertEquals(0, builder.transformSampleDepth.apply(64));

        // upscaling
        builder.sampleDepth(24);
        assertEquals(16777216, builder.transformSampleDepth.apply(65536));
        assertEquals(32768, builder.transformSampleDepth.apply(128));
        assertEquals(16384, builder.transformSampleDepth.apply(64));
    }

    @Test
    void transformSampleRateTest() throws URISyntaxException, IOException {
        Path path = Paths.get(getClass().getClassLoader().getResource(TRACK07_MP3).toURI());
        byte[] mediaBytes = Files.readAllBytes(path);
        BufferedInputStream in = new BufferedInputStream(new ByteArrayInputStream(mediaBytes));

        Builder builder = new Builder().sourceMp3(in);
        StreamInfo streamInfo = builder.streamInfo();
        assertEquals(MP3_SAMPLE_RATE, streamInfo.sampleRate);
        builder.downSampleRate(MP3_SAMPLE_RATE / 2);

        List<Integer> source = Stream.of(1, 2, 3, 4, 5, 6, 7).collect(toList());
        List<Integer> downSampled = builder.changeSampleDepthAndRate.apply(source);
        assertEquals(Stream.of(1, 3, 5, 7).collect(toList()), downSampled);
    }

    @Test
    void transformSampleRateAndDepthTest() throws URISyntaxException, IOException {
        Path path = Paths.get(getClass().getClassLoader().getResource(TRACK07_MP3).toURI());
        byte[] mediaBytes = Files.readAllBytes(path);
        BufferedInputStream in = new BufferedInputStream(new ByteArrayInputStream(mediaBytes));

        Builder builder = new Builder().sourceMp3(in);
        StreamInfo streamInfo = builder.streamInfo();
        assertEquals(MP3_SAMPLE_DEPTH, streamInfo.sampleDepth);
        assertEquals(MP3_SAMPLE_RATE, streamInfo.sampleRate);
        builder
                .sampleDepth(8)
                .downSampleRate(MP3_SAMPLE_RATE / 2);

        List<Integer> source = Stream.of(512, 2, 256, 4, 64, 6, 1024).collect(toList());
        List<Integer> downSampled = builder.changeSampleDepthAndRate.apply(source);
        assertEquals(Stream.of(2, 1, 0, 4).collect(toList()), downSampled);
    }

    @Ignore
    void fileOutputTest() throws URISyntaxException, IOException {
        Path path = Paths.get(getClass().getClassLoader().getResource(TRACK07_MP3).toURI());
        byte[] mediaBytes = Files.readAllBytes(path);
        BufferedInputStream in = new BufferedInputStream(new ByteArrayInputStream(mediaBytes));

        try (RandomAccessFile raf = new RandomAccessFile("./test.flac", "rw")) {
            new Builder()
                    .sourceMp3(in)
                    .mono()
                    .sampleDepth(12)
                    .downSampleRate(MP3_SAMPLE_RATE / 2)
                    .targetFlac()
                    .convert(new RandomAccessFileOutputStream(raf));

        }
    }

    @Test
    void bitOutputStreamTest() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BitOutputStream bOut = new BitOutputStream(out);
        int ff = 0xffffffff;
        bOut.writeInt(0, ff);
        bOut.flush();
        assertEquals(0, out.toByteArray().length);

        bOut.writeInt(7, ff);
        bOut.flush();
        assertEquals(0, out.toByteArray().length);

        bOut.writeInt(1, ff);
        bOut.flush();
        assertEquals(1, out.toByteArray().length);
        assertEquals(-1, out.toByteArray()[0]);
    }

    @Test
    void imageOutputStreamTest() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MemoryCacheImageOutputStream iOut = new MemoryCacheImageOutputStream(out);
        int ff = 0xffffffff;
        iOut.writeBits(ff, 0);
        iOut.flush();
        assertEquals(0, out.toByteArray().length);

        iOut.writeBits(ff, 7);
        iOut.flush();
        assertEquals(0, out.toByteArray().length);

        iOut.writeBits(ff, 1);
        iOut.flush();
        assertEquals(1, out.toByteArray().length);
        assertEquals(-1, out.toByteArray()[0]);
    }

    @Test
    void seekImageOutputStreamTest() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MemoryCacheImageOutputStream iOut = new MemoryCacheImageOutputStream(out);
        int ff = 0xff;

        iOut.writeBits(ff, 8);

        iOut.flushBefore(0);
        iOut.seek(0);
        int aa = 0xaa;
        iOut.writeBits(aa, 8);
        iOut.flush();
        assertEquals(1, out.toByteArray().length);
        assertEquals(-86, out.toByteArray()[0]);
    }
}
