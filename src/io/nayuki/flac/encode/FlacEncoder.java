/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.encode;

import java.io.IOException;
import io.nayuki.flac.common.StreamInfo;


public final class FlacEncoder {
	
	public FlacEncoder(StreamInfo info, int[][] samples, int blockSize, SubframeEncoder.SearchOptions opt, BitOutputStream out) throws IOException {
		info.minBlockSize = blockSize;
		info.maxBlockSize = blockSize;
		info.minFrameSize = 0;
		info.maxFrameSize = 0;
		
		for (int i = 0, pos = 0; pos < samples[0].length; i++) {
			System.err.printf("frame=%d  position=%d  %.2f%%%n", i, pos, 100.0 * pos / samples[0].length);
			int n = Math.min(samples[0].length - pos, blockSize);
			long[][] subsamples = getRange(samples, pos, n);
			FrameEncoder enc = FrameEncoder.computeBest(pos, subsamples, info.sampleDepth, info.sampleRate, opt).encoder;
			long startByte = out.getByteCount();
			enc.encode(subsamples, out);
			long frameSize = out.getByteCount() - startByte;
			if (frameSize < 0 || (int)frameSize != frameSize)
				throw new AssertionError();
			if (info.minFrameSize == 0 || frameSize < info.minFrameSize)
				info.minFrameSize = (int)frameSize;
			if (frameSize > info.maxFrameSize)
				info.maxFrameSize = (int)frameSize;
			pos += n;
		}
	}
	
	
	// Returns the subrange array[ : ][off : off + len] upcasted to long.
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
