/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.encode;

import java.io.IOException;
import io.nayuki.flac.common.Md5Hasher;
import io.nayuki.flac.common.StreamInfo;


public final class FlacEncoder {
	
	private int[][] samples;
	private final int sampleDepth;
	private final int sampleRate;
	private BitOutputStream out;
	public int minFrameSize;  // In bytes, or -1 if no frames encoded
	public int maxFrameSize;  // In bytes, or -1 if no frames encoded
	
	
	public FlacEncoder(int[][] samples, int sampleDepth, int sampleRate, BitOutputStream out) throws IOException {
		this.samples = samples;
		this.sampleDepth = sampleDepth;
		this.sampleRate = sampleRate;
		this.out = out;
		minFrameSize = Integer.MAX_VALUE;
		maxFrameSize = 0;
		
		out.writeInt(32, 0x664C6143);
		StreamInfo info = new StreamInfo();
		info.minBlockSize = 256;
		info.maxBlockSize = 32768;
		info.minFrameSize = 0;
		info.maxFrameSize = 0;
		info.sampleRate = sampleRate;
		info.numChannels = samples.length;
		info.sampleDepth = sampleDepth;
		info.numSamples = samples[0].length;
		info.md5Hash = Md5Hasher.getHash(samples, sampleDepth);
		info.write(true, out);
		
		for (int i = 0, pos = 0; pos < samples[0].length; i++) {
			System.err.printf("frame=%d  position=%d  %.2f%%%n", i, pos, 100.0 * pos / samples[0].length);
			int n = Math.min(samples[0].length - pos, 4096);
			long[][] subsamples = getRange(samples, pos, n);
			FrameEncoder enc = FrameEncoder.computeBest(pos, subsamples, sampleDepth, sampleRate, SubframeEncoder.SearchOptions.SUBSET_BEST).encoder;
			long startByte = out.getByteCount();
			enc.encode(subsamples, out);
			long frameSize = out.getByteCount() - startByte;
			if (frameSize < 0 || (int)frameSize != frameSize)
				throw new AssertionError();
			if (minFrameSize == -1 || frameSize < minFrameSize)
				minFrameSize = (int)frameSize;
			if (maxFrameSize == -1 || frameSize > maxFrameSize)
				maxFrameSize = (int)frameSize;
			pos += n;
		}
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
