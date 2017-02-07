/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac;

import java.io.IOException;
import java.util.Objects;


abstract class SubframeEncoder {
	
	/*---- Static functions ----*/
	
	public static SubframeEncoder computeBest(long[] data, int sampleDepth) {
		// Check arguments
		Objects.requireNonNull(data);
		if (sampleDepth < 1 || sampleDepth > 33)
			throw new IllegalArgumentException();
		
		// Encode with constant if possible
		SubframeEncoder result = new ConstantEncoder(data, 0, sampleDepth);
		if (result.encodedBitLength != Integer.MAX_VALUE)
			return result;
		
		// Detect number of trailing zero bits
		int shift = computeWastedBits(data);
		sampleDepth -= shift;
		
		// Start with verbatim as fallback
		result = new VerbatimEncoder(data, shift, sampleDepth);
		
		// Try fixed prediction encoding
		for (int order = 0; order <= 4; order++) {
			FixedPredictionEncoder temp = new FixedPredictionEncoder(data, shift, sampleDepth, order);
			if (temp.encodedBitLength < result.encodedBitLength)
				result = temp;
		}
		
		// Return the encoder found with the lowest bit length
		return result;
	}
	
	
	private static int computeWastedBits(long[] data) {
		long accumulator = 0;
		for (int i = 0; i < data.length; i++)
			accumulator |= data[i];
		return Long.numberOfTrailingZeros(accumulator);
	}
	
	
	
	/*---- Instance members ----*/
	
	protected final int sampleShift;  // At least 0
	protected final int sampleDepth;  // In the range [1, 33]
	protected int encodedBitLength;   // Must be at least 0
	
	
	protected SubframeEncoder(int shift, int depth) {
		if (shift < 0 || depth < 1 || depth > 33)
			throw new IllegalArgumentException();
		sampleShift = shift;
		sampleDepth = depth;
	}
	
	
	public final int getEncodedBitLength() {
		if (encodedBitLength < 0)
			throw new IllegalStateException();
		return encodedBitLength;
	}
	
	
	public abstract void encode(long[] data, BitOutputStream out) throws IOException;
	
	
	protected final void writeTypeAndShift(int type, BitOutputStream out) throws IOException {
		// Check arguments
		Objects.requireNonNull(out);
		if ((type >>> 6) != 0)
			throw new IllegalArgumentException();
		
		// Write some fields
		out.writeInt(1, 0);
		out.writeInt(6, type);
		
		// Write shift value in quasi-unary
		if (sampleShift == 0)
			out.writeInt(1, 0);
		else {
			out.writeInt(1, 1);
			for (int i = 0; i < sampleShift - 1; i++)
				out.writeInt(1, 0);
			out.writeInt(1, 1);
		}
	}
	
}
