/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.encode;

import java.io.IOException;


/* 
 * Under the constant coding mode, this provides size calculations on and bitstream encoding of audio sample data.
 * Note that unlike the other subframe encoders which are fully general, not all data can be encoded using constant mode.
 * The encoded size depends on the shift and bit depth, but not on the data length or contents.
 */
final class ConstantEncoder extends SubframeEncoder {
	
	// Computes the best way to encode the given values under the constant coding mode,
	// returning an exact size plus a new encoder object associated with the input arguments.
	// However if the sample data is non-constant then null is returned instead,
	// to indicate that the data is impossible to represent in this mode.
	public static SizeEstimate<SubframeEncoder> computeBest(long[] samples, int shift, int depth) {
		if (!isConstant(samples))
			return null;
		ConstantEncoder enc = new ConstantEncoder(samples, shift, depth);
		long size = 1 + 6 + 1 + shift + depth;
		return new SizeEstimate<SubframeEncoder>(size, enc);
	}
	
	
	// Constructs a constant encoder for the given data, right shift, and sample depth.
	public ConstantEncoder(long[] samples, int shift, int depth) {
		super(shift, depth);
	}
	
	
	// Encodes the given vector of audio sample data to the given bit output stream using
	// the this encoding method (and the superclass fields sampleShift and sampleDepth).
	// This requires the data array to have the same values (but not necessarily
	// the same object reference) as the array that was passed to the constructor.
	public void encode(long[] samples, BitOutputStream out) throws IOException {
		if (!isConstant(samples))
			throw new IllegalArgumentException("Data is not constant-valued");
		if ((samples[0] >> sampleShift) << sampleShift != samples[0])
			throw new IllegalArgumentException("Invalid shift value for data");
		writeTypeAndShift(0, out);
		out.writeInt(sampleDepth - sampleShift, (int)(samples[0] >> sampleShift));
	}
	
	
	// Returns true iff the set of unique values in the array has size exactly 1. Pure function.
	private static boolean isConstant(long[] data) {
		if (data.length == 0)
			return false;
		long val = data[0];
		for (long x : data) {
			if (x != val)
				return false;
		}
		return true;
	}
	
}
