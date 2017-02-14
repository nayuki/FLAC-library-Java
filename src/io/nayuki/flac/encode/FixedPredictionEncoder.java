/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.encode;

import java.io.IOException;
import java.util.Objects;


final class FixedPredictionEncoder extends SubframeEncoder {
	
	public static SizeEstimate<SubframeEncoder> computeBest(long[] data, int shift, int depth, int order, int maxRiceOrder) {
		FixedPredictionEncoder enc = new FixedPredictionEncoder(data, shift, depth, order);
		data = data.clone();
		for (int i = 0; i < data.length; i++)
			data[i] >>= shift;
		LinearPredictiveEncoder.applyLpc(data, COEFFICIENTS[order], 0);
		long temp = RiceEncoder.computeBestSizeAndOrder(data, order, maxRiceOrder);
		enc.riceOrder = (int)(temp & 0xF);
		long size = 1 + 6 + 1 + shift + order * depth + (temp >>> 4);
		return new SizeEstimate<SubframeEncoder>(size, enc);
	}
	
	
	
	private final int order;
	public int riceOrder;
	
	
	public FixedPredictionEncoder(long[] data, int shift, int depth, int order) {
		super(shift, depth);
		if (order < 0 || order >= COEFFICIENTS.length || data.length < order)
			throw new IllegalArgumentException();
		this.order = order;
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
			out.writeInt(sampleDepth - sampleShift, (int)data[i]);
		LinearPredictiveEncoder.applyLpc(data, COEFFICIENTS[order], 0);
		RiceEncoder.encode(data, order, riceOrder, out);
	}
	
	
	private static final int[][] COEFFICIENTS = {
		{},
		{1},
		{2, -1},
		{3, -3, 1},
		{4, -6, 4, -1},
	};
	
}
