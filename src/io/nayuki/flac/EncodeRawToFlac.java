/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;


// Handles raw 16-bit big-endian audio files only.
public final class EncodeRawToFlac {
	
	public static void main(String[] args) throws IOException {
		if (args.length < 2) {
			System.err.println("Usage: java EncodeRawToFlac InFileChan0.raw [InFileChan1.raw ...] OutFile.flac");
			System.exit(1);
			return;
		}
		
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
		
		File outFile = new File(args[args.length - 1]);
		try (BitOutputStream out = new BitOutputStream(
				new BufferedOutputStream(new FileOutputStream(outFile)))) {
			new FlacEncoder(samples, 16, 44100, out);
		}
	}
	
}
