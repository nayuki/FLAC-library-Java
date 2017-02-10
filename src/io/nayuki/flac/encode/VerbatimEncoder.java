/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.encode;

import java.io.IOException;


final class VerbatimEncoder extends SubframeEncoder {
	
	public static SizeEstimate<SubframeEncoder> computeBest(long[] data, int shift, int depth) {
		VerbatimEncoder enc = new VerbatimEncoder(data, shift, depth);
		long size = 1 + 6 + 1 + shift + data.length * depth;
		return new SizeEstimate<SubframeEncoder>(size, enc);
	}
	
	
	public VerbatimEncoder(long[] data, int shift, int depth) {
		super(shift, depth);
	}
	
	
	public void encode(long[] data, BitOutputStream out) throws IOException {
		writeTypeAndShift(1, out);
		for (long val : data)
			out.writeInt(sampleDepth, (int)(val >> sampleShift));
	}
	
}
