package io.nayuki.flac.decode;


import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DecoderTest {

    private static final String TRACK07_FLAC = "media/Track07.flac";

    @Test
    public void test() throws URISyntaxException, IOException {
        Path path = Paths.get(getClass().getClassLoader().getResource(TRACK07_FLAC).toURI());
        byte[] mediaBytes = Files.readAllBytes(path);
        BufferedInputStream in = new BufferedInputStream(new ByteArrayInputStream(mediaBytes));
        FlacDecoder decoder = new FlacDecoder(in);
        decoder.readAndHandleMetadataBlock();
        assertNotNull(decoder.streamInfo);
        assertEquals(1, decoder.streamInfo.numChannels);
        assertEquals(16, decoder.streamInfo.sampleDepth);
        assertEquals(16_000, decoder.streamInfo.sampleRate);
        assertEquals(1_353_814, decoder.streamInfo.numSamples);
    }

}
