/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.encode;

import java.io.IOException;
import java.util.Arrays;
import io.nayuki.flac.common.Md5Hasher;
import io.nayuki.flac.common.StreamInfo;


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
		
		int numSamples = samples[0].length;
		int baseSize = 1024;
		int[] sizeMultiples = {3, 4, 5, 6};
		
		// Calculate compressed sizes for many block positions and sizes
		@SuppressWarnings("unchecked")
		SizeEstimate<FrameEncoder>[][] encoderInfo = new SizeEstimate[sizeMultiples.length][(numSamples + baseSize - 1) / baseSize];
		long startTime = System.currentTimeMillis();
		for (int i = 0; i < encoderInfo[0].length; i++) {
			double progress = (double)i / encoderInfo[0].length;
			double timeRemain = (System.currentTimeMillis() - startTime) / 1000.0 / progress * (1 - progress);
			System.err.printf("\rprogress=%.2f%%    timeRemain=%ds", progress * 100, Math.round(timeRemain));
			
			int pos = i * baseSize;
			for (int j = 0; j < encoderInfo.length; j++) {
				int n = Math.min(sizeMultiples[j] * baseSize, numSamples - pos);
				long[][] subsamples = getRange(samples, pos, n);
				encoderInfo[j][i] = FrameEncoder.computeBest(pos, subsamples, sampleDepth, sampleRate, SubframeEncoder.SearchOptions.SUBSET_BEST);
			}
		}
		
		// Initialize arrays to prepare for dynamic programming
		FrameEncoder[] bestEncoders = new FrameEncoder[encoderInfo[0].length];
		long[] bestSizes = new long[bestEncoders.length];
		Arrays.fill(bestSizes, Integer.MAX_VALUE);
		
		// Use dynamic programming to calculate optimum block size switching
		for (int i = 0; i < encoderInfo.length; i++) {
			for (int j = bestSizes.length - 1; j >= 0; j--) {
				int size = (int)encoderInfo[i][j].sizeEstimate;
				if (j + sizeMultiples[i] < bestSizes.length)
					size += bestSizes[j + sizeMultiples[i]];
				if (size < bestSizes[j]) {
					bestSizes[j] = size;
					bestEncoders[j] = encoderInfo[i][j].encoder;
				}
			}
		}
		
		// Do the actual encoding and writing
		for (int i = 0, numBlocks = 0; i < bestEncoders.length; numBlocks++) {
			FrameEncoder enc = bestEncoders[i];
			int pos = i * baseSize;
			int n = Math.min(enc.blockSize, numSamples - pos);
			if (numBlocks % 20 == 0)
				System.err.println();
			else
				System.err.print(" ");
			System.err.print(n);
			long[][] subsamples = getRange(samples, pos, n);
			bestEncoders[i].encode(subsamples, out);
			i += (n + baseSize - 1) / baseSize;
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
