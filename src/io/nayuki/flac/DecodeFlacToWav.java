/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DataFormatException;


public final class DecodeFlacToWav {
	
	public static void main(String[] args) throws IOException, DataFormatException {
		File inFile  = new File(args[0]);
		File outFile = new File(args[1]);
		if (args.length != 2) {
			System.err.println("Usage: java DecodeFlacToWav InFile.flac OutFile.wav");
			System.exit(1);
			return;
		}
		
		FlacDecoder dec;
		try (InputStream in = new BufferedInputStream(new FileInputStream(inFile))) {
			dec = new FlacDecoder(in);
		}
		if (dec.sampleDepth != 16)
			throw new UnsupportedOperationException("Only 16-bit sample depth supported");
		
		try (DataOutputStream out = new DataOutputStream(
				new BufferedOutputStream(new FileOutputStream(outFile)))) {
			DecodeFlacToWav.out = out;
			
			int[][] samples = dec.samples;
			int sampleDataLen = samples[0].length * dec.numChannels * dec.sampleDepth / 8;
			out.writeInt(0x52494646);  // "RIFF" chunk
			writeLittleInt32(sampleDataLen + 36);
			out.writeInt(0x57415645);
			
			out.writeInt(0x666D7420);  // "fmt " chunk
			writeLittleInt32(16);
			writeLittleInt16(0x0001);
			writeLittleInt16(dec.numChannels);
			writeLittleInt32(dec.sampleRate);
			writeLittleInt32(dec.sampleRate * dec.numChannels * dec.sampleDepth / 8);
			writeLittleInt16(dec.numChannels * dec.sampleDepth / 8);
			writeLittleInt16(dec.sampleDepth);
			
			out.writeInt(0x64617461);  // "data" chunk
			writeLittleInt32(sampleDataLen);
			for (int i = 0; i < samples[0].length; i++) {
				for (int j = 0; j < samples.length; j++)
					writeLittleInt16(samples[j][i]);
			}
		}
	}
	
	
	private static DataOutputStream out;
	
	
	private static void writeLittleInt16(int x) throws IOException {
		out.writeShort(Integer.reverseBytes(x) >>> 16);
	}
	
	
	private static void writeLittleInt32(int x) throws IOException {
		out.writeInt(Integer.reverseBytes(x));
	}
	
}
