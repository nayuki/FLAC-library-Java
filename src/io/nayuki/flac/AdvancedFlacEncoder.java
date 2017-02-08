/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac;

import java.io.IOException;
import java.util.Arrays;


public final class AdvancedFlacEncoder {
	
	private int[][] samples;
	private final int sampleDepth;
	private final int sampleRate;
	private BitOutputStream out;
	
	
	public AdvancedFlacEncoder(int[][] samples, int sampleDepth, int sampleRate, BitOutputStream out) throws IOException {
		this.samples = samples;
		this.sampleDepth = sampleDepth;
		this.sampleRate = sampleRate;
		this.out = out;
		
		out.writeInt(32, 0x664C6143);
		out.writeInt(1, 1);
		out.writeInt(7, 0);
		out.writeInt(24, 34);
		writeStreamInfoBlock();
		
		int numSamples = samples[0].length;
		int baseSize = 1024;
		int[] sizeMultiples = {3, 4, 5, 6};
		
		// Calculate compressed sizes for many block positions and sizes
		FrameEncoder[][] encoders = new FrameEncoder[sizeMultiples.length][(numSamples + baseSize - 1) / baseSize];
		for (int i = 0; i < encoders.length; i++) {
			int blockSize = sizeMultiples[i] * baseSize;
			for (int j = 0; j < encoders[i].length; j++) {
				int pos = j * baseSize;
				int n = Math.min(blockSize, numSamples - pos);
				long[][] subsamples = getRange(samples, pos, n);
				encoders[i][j] = new FrameEncoder(pos, subsamples, 16, sampleRate);
				System.err.printf("size=%d  position=%d  %.2f%%  bits=%d%n", blockSize, pos, 100.0 * pos / numSamples, encoders[i][j].getEncodedBitLength());
			}
		}
		
		// Initialize arrays to prepare for dynamic programming
		FrameEncoder[] bestEncoders = new FrameEncoder[encoders[0].length];
		long[] bestSizes = new long[bestEncoders.length];
		Arrays.fill(bestSizes, Integer.MAX_VALUE);
		
		// Use dynamic programming to calculate optimum block size switching
		for (int i = 0; i < encoders.length; i++) {
			for (int j = bestSizes.length - 1; j >= 0; j--) {
				int size = encoders[i][j].getEncodedBitLength();
				if (j + sizeMultiples[i] < bestSizes.length)
					size += bestSizes[j + sizeMultiples[i]];
				if (size < bestSizes[j]) {
					bestSizes[j] = size;
					bestEncoders[j] = encoders[i][j];
				}
			}
		}
		
		// Do the actual encoding and writing
		for (int i = 0; i < bestEncoders.length; ) {
			FrameEncoder enc = bestEncoders[i];
			int pos = i * baseSize;
			int n = Math.min(enc.blockSize, numSamples - pos);
			System.err.println(n);
			long[][] subsamples = getRange(samples, pos, n);
			bestEncoders[i].encode(subsamples, out);
			i += (n + baseSize - 1) / baseSize;
		}
	}
	
	
	private void writeStreamInfoBlock() throws IOException {
		out.writeInt(16,   256);  // Minimum block samples
		out.writeInt(16, 32768);  // Maximum block samples
		out.writeInt(24, 0);  // Minimum frame bytes
		out.writeInt(24, 0);  // Maximum frame bytes
		out.writeInt(20, sampleRate);
		out.writeInt(3, samples.length - 1);  // Number of channels
		out.writeInt(5, sampleDepth - 1);
		out.writeInt(18, samples[0].length >>> 18);  // Number of samples (high bits)
		out.writeInt(18, samples[0].length & ((1 << 18) - 1));  // Number of samples (low bits)
		byte[] hash = Md5Hasher.getHash(samples, sampleDepth);
		for (byte b : hash)  // MD5 hash (16 bytes)
			out.writeInt(8, b);
	}
	
	
	private static long[][] getRange(int[][] array, int off, int len) {
		long[][] result = new long[array.length][len];
		for (int i = 0; i < array.length; i++) {
			int[] src = array[i];
			long[] dest = result[i];
			for (int j = 0; j < len; j++)
				dest[j] = src[off + j];
		}
		return result;
	}
	
}
