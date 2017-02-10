/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.app;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import io.nayuki.flac.encode.BitOutputStream;
import io.nayuki.flac.encode.FlacEncoder;
import io.nayuki.flac.encode.RandomAccessFileOutputStream;


// Handles raw 16-bit big-endian audio files only.
public final class EncodeRawToFlac {
	
	public static void main(String[] args) throws IOException {
		// Check command line arguments
		if (args.length < 2) {
			System.err.println("Usage: java EncodeRawToFlac InFileChan0.raw [InFileChan1.raw ...] OutFile.flac");
			System.exit(1);
			return;
		}
		
		// Read raw audio samples, one channel per file
		int numSamples = (int)(new File(args[0]).length() / 2);
		int[][] samples = new int[args.length - 1][];
		for (int i = 0; i < args.length - 1; i++) {
			int[] smpl = new int[numSamples];
			try (DataInputStream in = new DataInputStream(
					new BufferedInputStream(new FileInputStream(args[i])))) {
				for (int j = 0; j < numSamples; j++)
					smpl[j] = in.readShort();
			}
			samples[i] = smpl;
		}
		
		// Encode to FLAC and write output file
		File outFile = new File(args[args.length - 1]);
		try (RandomAccessFile raf = new RandomAccessFile(outFile, "rw")) {
			raf.setLength(0);  // Truncate an existing file
			
			// Encode all frames
			BitOutputStream out = new BitOutputStream(
				new BufferedOutputStream(new RandomAccessFileOutputStream(raf)));
			FlacEncoder enc = new FlacEncoder(samples, 16, 44100, out);
			out.flush();
			
			// Rewrite parts of the stream info metadata block, which
			// is located at a fixed offset in the file by definition
			raf.seek(4 + 1 + 3 + 2 + 2);
			out.writeInt(24, enc.minFrameSize);
			out.writeInt(24, enc.maxFrameSize);
			out.flush();
		}
	}
	
}
