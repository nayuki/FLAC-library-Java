/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac;

import java.io.IOException;


final class ConstantEncoder extends SubframeEncoder {
	
	public ConstantEncoder(long[] data, int shift, int depth) {
		super(shift, depth);
		if (isConstant(data))
			encodedBitLength = 1 + 6 + 1 + shift + depth;
		else
			encodedBitLength = Integer.MAX_VALUE;
	}
	
	
	public void encode(long[] data, BitOutputStream out) throws IOException {
		if (!isConstant(data))
			throw new IllegalArgumentException("Data is not constant-valued");
		if ((data[0] >> sampleShift) << sampleShift != data[0])
			throw new IllegalArgumentException("Invalid shift value for data");
		writeTypeAndShift(0, out);
		out.writeInt(sampleDepth, (int)(data[0] >> sampleShift));
	}
	
	
	// Returns true iff the set of unique values in the array has size exactly 1.
	private static boolean isConstant(long[] data) {
		if (data.length == 0)
			return false;
		long val = data[0];
		for (int i = 1; i < data.length; i++) {
			if (data[i] != val)
				return false;
		}
		return true;
	}
	
}
