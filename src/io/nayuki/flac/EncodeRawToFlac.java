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


// Handles mono raw 16-bit big-endian audio files only.
public final class EncodeRawToFlac {
	
	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			System.err.println("Usage: java EncodeRawToFlac InFile.raw OutFile.flac");
			System.exit(1);
			return;
		}
		File inFile  = new File(args[0]);
		File outFile = new File(args[1]);
		
		int[] samples = new int[(int)(inFile.length() / 2)];
		try (DataInputStream in = new DataInputStream(
				new BufferedInputStream(new FileInputStream(inFile)))) {
			for (int i = 0; i < samples.length; i++)
				samples[i] = in.readShort();
		}
		
		try (BitOutputStream out = new BitOutputStream(
				new BufferedOutputStream(new FileOutputStream(outFile)))) {
			new FlacEncoder(new int[][]{samples}, 16, 44100, out);
		}
	}
	
}
