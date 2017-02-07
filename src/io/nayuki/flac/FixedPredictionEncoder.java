/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac;

import java.io.IOException;
import java.util.Objects;


final class FixedPredictionEncoder extends SubframeEncoder {
	
	private final int order;
	
	
	public FixedPredictionEncoder(long[] data, int shift, int depth, int order) {
		super(shift, depth);
		if (order < 0 || order >= COEFFICIENTS.length || data.length < order)
			throw new IllegalArgumentException();
		this.order = order;
		
		data = data.clone();
		for (int i = 0; i < data.length; i++)
			data[i] >>= shift;
		
		applyLpc(data, COEFFICIENTS[order], 0);
		int temp = (int)(RiceEncoder.computeBestSizeAndOrder(data, order) >>> 4);
		encodedBitLength = 1 + 6 + 1 + shift + order * depth + temp;
	}
	
	
	public void encode(long[] data, BitOutputStream out) throws IOException {
		Objects.requireNonNull(data);
		Objects.requireNonNull(out);
		if (data.length < order)
			throw new IllegalArgumentException();
		
		writeTypeAndShift(8 + order, out);
		data = data.clone();
		for (int i = 0; i < data.length; i++)
			data[i] >>= sampleShift;
		
		for (int i = 0; i < order; i++)  // Warmup
			out.writeInt(sampleDepth, (int)data[i]);
		applyLpc(data, COEFFICIENTS[order], 0);
		RiceEncoder.encode(data, order, out);
	}
	
	
	private static void applyLpc(long[] data, int[] coefs, int shift) {
		for (int i = data.length - 1; i >= coefs.length; i--) {
			long sum = 0;
			for (int j = 0; j < coefs.length; j++)
				sum += data[i - 1 - j] * coefs[j];
			data[i] -= sum >> shift;
		}
	}
	
	
	private static final int[][] COEFFICIENTS = {
		{},
		{1},
		{2, -1},
		{3, -3, 1},
		{4, -6, 4, -1},
	};
	
}
