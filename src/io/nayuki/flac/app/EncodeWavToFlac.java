/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.app;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import io.nayuki.flac.common.Md5Hasher;
import io.nayuki.flac.common.StreamInfo;
import io.nayuki.flac.decode.DataFormatException;
import io.nayuki.flac.encode.BitOutputStream;
import io.nayuki.flac.encode.FlacEncoder;
import io.nayuki.flac.encode.RandomAccessFileOutputStream;


public final class EncodeWavToFlac {
	
	public static void main(String[] args) throws IOException {
		// Handle command line arguments
		if (args.length != 2) {
			System.err.println("Usage: java EncodeWavToFlac InFileChan0.raw [InFileChan1.raw ...] OutFile.flac");
			System.exit(1);
			return;
		}
		File inFile  = new File(args[0]);
		File outFile = new File(args[1]);
		
		// Read WAV file headers and audio sample data
		int[][] samples;
		int sampleRate;
		int sampleDepth;
		try (InputStream in = new BufferedInputStream(new FileInputStream(inFile))) {
			// Parse and check WAV header
			if (!readString(in, 4).equals("RIFF"))
				throw new DataFormatException("Invalid RIFF file header");
			readLittleUint(in, 4);  // Remaining data length
			if (!readString(in, 4).equals("WAVE"))
				throw new DataFormatException("Invalid WAV file header");
			
			// Handle the format chunk
			if (!readString(in, 4).equals("fmt "))
				throw new DataFormatException("Unrecognized WAV file chunk");
			if (readLittleUint(in, 4) != 16)
				throw new DataFormatException("Unsupported WAV file type");
			if (readLittleUint(in, 2) != 0x0001)
				throw new DataFormatException("Unsupported WAV file codec");
			int numChannels = readLittleUint(in, 2);
			if (numChannels < 0 || numChannels > 8)
				throw new RuntimeException("Too many (or few) audio channels");
			sampleRate = readLittleUint(in, 4);
			if (sampleRate <= 0 || sampleRate >= (1 << 20))
				throw new RuntimeException("Sample rate too large or invalid");
			int byteRate = readLittleUint(in, 4);
			int blockAlign = readLittleUint(in, 2);
			sampleDepth = readLittleUint(in, 2);
			if (sampleDepth == 0 || sampleDepth > 32 || sampleDepth % 8 != 0)
				throw new RuntimeException("Unsupported sample depth");
			int bytesPerSample = sampleDepth / 8;
			if (bytesPerSample * numChannels != blockAlign)
				throw new RuntimeException("Invalid block align value");
			if (bytesPerSample * numChannels * sampleRate != byteRate)
				throw new RuntimeException("Invalid byte rate value");
			
			// Handle the data chunk
			if (!readString(in, 4).equals("data"))
				throw new DataFormatException("Unrecognized WAV file chunk");
			int sampleDataLen = readLittleUint(in, 4);
			if (sampleDataLen <= 0 || sampleDataLen % (numChannels * bytesPerSample) != 0)
				throw new DataFormatException("Invalid length of audio sample data");
			int numSamples = sampleDataLen / (numChannels * bytesPerSample);
			samples = new int[numChannels][numSamples];
			for (int i = 0; i < numSamples; i++) {
				for (int ch = 0; ch < numChannels; ch++) {
					int val = readLittleUint(in, bytesPerSample);
					if (sampleDepth == 8)
						val -= 128;
					else
						val = (val << (32 - sampleDepth)) >> (32 - sampleDepth);
					samples[ch][i] = val;
				}
			}
			// Note: There might be chunks after "data", but they can be ignored
		}
		
		// Open output file and encode samples to FLAC
		try (RandomAccessFile raf = new RandomAccessFile(outFile, "rw")) {
			raf.setLength(0);  // Truncate an existing file
			BitOutputStream out = new BitOutputStream(
				new BufferedOutputStream(new RandomAccessFileOutputStream(raf)));
			out.writeInt(32, 0x664C6143);
			
			// Populate and write the stream info structure
			StreamInfo info = new StreamInfo();
			info.sampleRate = sampleRate;
			info.numChannels = samples.length;
			info.sampleDepth = sampleDepth;
			info.numSamples = samples[0].length;
			info.md5Hash = Md5Hasher.getHash(samples, sampleDepth);
			info.write(true, out);
			
			// Encode all frames
			new FlacEncoder(info, samples, 4096, out);
			out.flush();
			
			// Rewrite the stream info metadata block, which is
			// located at a fixed offset in the file by definition
			raf.seek(4);
			info.write(true, out);
			out.flush();
		}
	}
	
	
	private static String readString(InputStream in, int len) throws IOException {
		byte[] temp = new byte[len];
		for (int i = 0; i < temp.length; i++) {
			int b = in.read();
			if (b == -1)
				throw new EOFException();
			temp[i] = (byte)b;
		}
		return new String(temp, StandardCharsets.UTF_8);
	}
	
	
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
	
}
