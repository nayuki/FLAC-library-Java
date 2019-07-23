/* 
 * FLAC library (Java)
 * 
 * Copyright (c) Project Nayuki
 * https://www.nayuki.io/page/flac-library-java
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program (see COPYING.txt and COPYING.LESSER.txt).
 * If not, see <http://www.gnu.org/licenses/>.
 */

package io.nayuki.flac.encode;

import io.nayuki.flac.common.StreamInfo;

import java.io.IOException;
import java.util.List;


public final class FlacEncoder {

	/**
	 * Takes a chunk of samples from some channel starting from offset,
	 * copies and casting len of samples to long[].
	 */
	@FunctionalInterface
	private interface Slicer {
		long[] apply(int ch, int offset, int len);
	}


	public FlacEncoder(StreamInfo info, int[][] samples, int blockSize, SubframeEncoder.SearchOptions opt, BitOutputStream out) throws IOException {
		Slicer slicer = (ch, off, len) -> {
			int[] src = samples[ch];
			long[] dest = new long[len];
			for (int j = 0; j < len; j++) {
				dest[j] = src[off + j];
			}
			return dest;
		};

		init(info, blockSize, slicer, opt, out);
	}

	public FlacEncoder(StreamInfo info, List<Integer>[] samples, int blockSize, SubframeEncoder.SearchOptions opt, BitOutputStream out) throws IOException {
		Slicer slicer = (ch, off, len) -> {
			List<Integer> src = samples[ch];
			long[] dest = new long[len];
			for (int j = 0; j < len; j++) {
				dest[j] = src.get(off + j);
			}
			return dest;
		};
		init(info, blockSize, slicer, opt, out);
	}

	private void init(StreamInfo info,
					  int blockSize,
					  Slicer slicer,
					  SubframeEncoder.SearchOptions opt,
					  BitOutputStream out) throws IOException
	{
		info.minBlockSize = blockSize;
		info.maxBlockSize = blockSize;
		info.minFrameSize = 0;
		info.maxFrameSize = 0;

		for (int pos = 0; pos < info.numSamples;) {
			int n = Math.min(Long.valueOf(info.numSamples).intValue() - pos, blockSize);
			long[][] subsamples = getRange(slicer, info.numChannels, pos, n);
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
	private static long[][] getRange(Slicer slicer, int numChannels, int off, int len) {
		long[][] result = new long[numChannels][0];
		for (int i = 0; i < numChannels; i++) {
			result[i] = slicer.apply(i, off, len);
		}
		return result;
	}
	
}
