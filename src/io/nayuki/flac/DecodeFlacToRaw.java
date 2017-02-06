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
import java.util.zip.DataFormatException;


public final class DecodeFlacToRaw {
	
	public static void main(String[] args) throws IOException, DataFormatException {
		File inFile  = new File(args[0]);
		File outFile = new File(args[1]);
		
		FlacDecoder dec;
		try (BitInputStream in = new BitInputStream(
				new BufferedInputStream(new FileInputStream(inFile)))) {
			dec = new FlacDecoder(in);
		}
		if (dec.sampleDepth != 16)
			throw new UnsupportedOperationException("Only 16-bit sample depth supported");
		
		try (DataOutputStream out = new DataOutputStream(
				new BufferedOutputStream(new FileOutputStream(outFile)))) {
			int[][] samples = dec.samples;
			for (int i = 0; i < samples[0].length; i++) {
				for (int j = 0; j < samples.length; j++)
					out.writeShort(samples[j][i]);  // Big-endian
			}
		}
	}
	
}
