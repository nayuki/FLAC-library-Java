# Purpose

Creates library allows to convert mp3-audio files to other formats (raw, wav, flac).
Parameters of target file like samples rate, samples depth, number of channels might be changed during converting.

Example of use:
```
        try (RandomAccessFile raf = new RandomAccessFile("./test.flac", "rw")) {
            new Builder()
                    .sourceMp3(in)
                    .mono() // only one channel
                    .sampleDepth(12) // from 16 -> 12 bits
                    .downSampleRate(MP3_SAMPLE_RATE / 2) // from 44100 -> 22050 Hz
                    .targetFlac()
                    .convert(new RandomAccessFileOutputStream(raf));

        }
```
# Dependencies

This repository is fork of https://www.nayuki.io/page/flac-library-java and also
uses another one open-source library at https://github.com/delthas/JavaMP3

# How to build

```
mvn clean install
```
