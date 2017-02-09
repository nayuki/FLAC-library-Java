/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac;

import java.io.IOException;


public final class FlacEncoder {
	
	private int[][] samples;
	private final int sampleDepth;
	private final int sampleRate;
	private BitOutputStream out;
	
	
	public FlacEncoder(int[][] samples, int sampleDepth, int sampleRate, BitOutputStream out) throws IOException {
		this.samples = samples;
		this.sampleDepth = sampleDepth;
		this.sampleRate = sampleRate;
		this.out = out;
		
		out.writeInt(32, 0x664C6143);
		out.writeInt(1, 1);
		out.writeInt(7, 0);
		out.writeInt(24, 34);
		writeStreamInfoBlock();
		
		for (int i = 0, pos = 0; pos < samples[0].length; i++) {
			System.err.printf("frame=%d  position=%d  %.2f%%%n", i, pos, 100.0 * pos / samples[0].length);
			int n = Math.min(samples[0].length - pos, 4096);
			long[][] subsamples = getRange(samples, pos, n);
			FrameEncoder enc = FrameEncoder.computeBest(pos, subsamples, 16, sampleRate).encoder;
			enc.encode(subsamples, out);
			pos += n;
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
