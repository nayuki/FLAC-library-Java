/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.encode;

import java.io.IOException;
import java.util.Objects;


abstract class SubframeEncoder {
	
	/*---- Static functions ----*/
	
	public static SizeEstimate<SubframeEncoder> computeBest(long[] data, int sampleDepth, SearchOptions opt) {
		// Check arguments
		Objects.requireNonNull(data);
		if (sampleDepth < 1 || sampleDepth > 33)
			throw new IllegalArgumentException();
		
		// Encode with constant if possible
		SizeEstimate<SubframeEncoder> result = ConstantEncoder.computeBest(data, 0, sampleDepth);
		if (result != null) {
			return result;
		}
		
		// Detect number of trailing zero bits
		int shift = computeWastedBits(data);
		sampleDepth -= shift;
		
		// Start with verbatim as fallback
		result = VerbatimEncoder.computeBest(data, shift, sampleDepth);
		
		// Try fixed prediction encoding
		for (int order = opt.minFixedOrder; order <= opt.maxFixedOrder; order++) {
			SizeEstimate<SubframeEncoder> temp = FixedPredictionEncoder.computeBest(data, shift, sampleDepth, order, opt.maxRiceOrder);
			if (temp.sizeEstimate < result.sizeEstimate)
				result = temp;
		}
		
		// Try linear predictive coding
		FastDotProduct fdp = new FastDotProduct(data, 32);
		for (int order = opt.minLpcOrder; order <= opt.maxLpcOrder; order++) {
			SizeEstimate<SubframeEncoder> temp = LinearPredictiveEncoder.computeBest(data, shift, sampleDepth, order, Math.min(opt.lpcRoundVariables, order), fdp, opt.maxRiceOrder);
			if (temp.sizeEstimate < result.sizeEstimate)
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
	
	
	protected SubframeEncoder(int shift, int depth) {
		if (shift < 0 || depth < 1 || depth > 33)
			throw new IllegalArgumentException();
		sampleShift = shift;
		sampleDepth = depth;
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
	
	
	
	/*---- Helper structure ----*/
	
	// Objects of this class are immutable.
	public static final class SearchOptions {
		
		/*-- Fields --*/
		
		public final int minFixedOrder;
		public final int maxFixedOrder;
		public final int minLpcOrder;
		public final int maxLpcOrder;
		public final int lpcRoundVariables;
		public final int maxRiceOrder;
		
		
		/*-- Constructors --*/
		
		public SearchOptions(int minFixedOrder, int maxFixedOrder, int minLpcOrder, int maxLpcOrder, int lpcRoundVars, int maxRiceOrder) {
			if ((minFixedOrder != -1 || maxFixedOrder != -1) &&
					!(0 <= minFixedOrder && minFixedOrder <= maxFixedOrder && maxFixedOrder <= 4))
				throw new IllegalArgumentException();
			if ((minLpcOrder != -1 || maxLpcOrder != -1) &&
					!(1 <= minLpcOrder && minLpcOrder <= maxLpcOrder && maxLpcOrder <= 32))
				throw new IllegalArgumentException();
			if (lpcRoundVars < 0 || lpcRoundVars > 30)
				throw new IllegalArgumentException();
			if (maxRiceOrder < 0 || maxRiceOrder > 15)
				throw new IllegalArgumentException();
			this.minFixedOrder = minFixedOrder;
			this.maxFixedOrder = maxFixedOrder;
			this.minLpcOrder = minLpcOrder;
			this.maxLpcOrder = maxLpcOrder;
			this.lpcRoundVariables = lpcRoundVars;
			this.maxRiceOrder = maxRiceOrder;
		}
		
		
		/*-- Constants for recommended defaults --*/
		
		// These search ranges conform to the FLAC subset.
		public static final SearchOptions SUBSET_ONLY_FIXED = new SearchOptions(0, 4, -1, -1, 0, 8);
		public static final SearchOptions SUBSET_BEST = new SearchOptions(0, 1, 2, 12, 0, 8);
		
		// These search ranges do conform to the FLAC subset (i.e. they are lax).
		public static final SearchOptions LAX_MEDIUM = new SearchOptions(0, 1, 2, 22, 0, 15);
		public static final SearchOptions LAX_BEST = new SearchOptions(0, 1, 2, 32, 0, 15);
		
	}
	
}
