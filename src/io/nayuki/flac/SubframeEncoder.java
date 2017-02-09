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
	
	public static SizeEstimate<SubframeEncoder> computeBestNew(long[] data, int sampleDepth) {
		SubframeEncoder enc = computeBest(data, sampleDepth);
		return new SizeEstimate<>(enc.getEncodedBitLength(), enc);
	}
	
	
	public static SubframeEncoder computeBest(long[] data, int sampleDepth) {
		// Check arguments
		Objects.requireNonNull(data);
		if (sampleDepth < 1 || sampleDepth > 33)
			throw new IllegalArgumentException();
		
		// Encode with constant if possible
		SizeEstimate<SubframeEncoder> result = ConstantEncoder.computeBest(data, 0, sampleDepth);
		if (result != null) {
			result.encoder.encodedBitLength = (int)result.sizeEstimate;
			return result.encoder;
		}
		
		// Detect number of trailing zero bits
		int shift = computeWastedBits(data);
		sampleDepth -= shift;
		
		// Start with verbatim as fallback
		result = VerbatimEncoder.computeBest(data, shift, sampleDepth);
		
		// Try fixed prediction encoding
		for (int order = 0; order <= 4; order++) {
			SizeEstimate<SubframeEncoder> temp = FixedPredictionEncoder.computeBest(data, shift, sampleDepth, order);
			if (temp.sizeEstimate < result.sizeEstimate)
				result = temp;
		}
		
		// Try linear predictive coding
		FastDotProduct fdp = new FastDotProduct(data, 32);
		for (int order = 2; order <= 32; order++) {
			SizeEstimate<SubframeEncoder> temp = LinearPredictiveEncoder.computeBest(data, shift, sampleDepth, order, fdp);
			if (temp.sizeEstimate < result.sizeEstimate)
				result = temp;
		}
		
		// Return the encoder found with the lowest bit length
		result.encoder.encodedBitLength = (int)result.sizeEstimate;
		return result.encoder;
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
