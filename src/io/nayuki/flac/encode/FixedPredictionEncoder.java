/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.encode;

import java.io.IOException;
import java.util.Objects;


/* 
 * Under the fixed prediction coding mode of some order, this provides size calculations on and bitstream encoding of audio sample data.
 */
final class FixedPredictionEncoder extends SubframeEncoder {
	
	// Computes the best way to encode the given values under the fixed prediction coding mode of the given order,
	// returning a size plus a new encoder object associated with the input arguments. The maxRiceOrder argument
	// is used by the Rice encoder to estimate the size of coding the residual signal.
	public static SizeEstimate<SubframeEncoder> computeBest(long[] samples, int shift, int depth, int order, int maxRiceOrder) {
		FixedPredictionEncoder enc = new FixedPredictionEncoder(samples, shift, depth, order);
		samples = samples.clone();
		for (int i = 0; i < samples.length; i++)
			samples[i] >>= shift;
		LinearPredictiveEncoder.applyLpc(samples, COEFFICIENTS[order], 0);
		long temp = RiceEncoder.computeBestSizeAndOrder(samples, order, maxRiceOrder);
		enc.riceOrder = (int)(temp & 0xF);
		long size = 1 + 6 + 1 + shift + order * depth + (temp >>> 4);
		return new SizeEstimate<SubframeEncoder>(size, enc);
	}
	
	
	
	private final int order;
	public int riceOrder;
	
	
	public FixedPredictionEncoder(long[] samples, int shift, int depth, int order) {
		super(shift, depth);
		if (order < 0 || order >= COEFFICIENTS.length || samples.length < order)
			throw new IllegalArgumentException();
		this.order = order;
	}
	
	
	public void encode(long[] samples, BitOutputStream out) throws IOException {
		Objects.requireNonNull(samples);
		Objects.requireNonNull(out);
		if (samples.length < order)
			throw new IllegalArgumentException();
		
		writeTypeAndShift(8 + order, out);
		samples = samples.clone();
		for (int i = 0; i < samples.length; i++)
			samples[i] >>= sampleShift;
		
		for (int i = 0; i < order; i++)  // Warmup
			out.writeInt(sampleDepth - sampleShift, (int)samples[i]);
		LinearPredictiveEncoder.applyLpc(samples, COEFFICIENTS[order], 0);
		RiceEncoder.encode(samples, order, riceOrder, out);
	}
	
	
	// The linear predictive coding (LPC) coefficients for fixed prediction of orders 0 to 4 (inclusive).
	private static final int[][] COEFFICIENTS = {
		{},
		{1},
		{2, -1},
		{3, -3, 1},
		{4, -6, 4, -1},
	};
	
}
