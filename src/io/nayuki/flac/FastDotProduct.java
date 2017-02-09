/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac;

import java.util.Objects;


// A helper class for LinearPredictiveEncoder.
final class FastDotProduct {
	
	private long[] data;
	private double[] precomputed;
	
	
	public FastDotProduct(long[] data, int maxDelta) {
		Objects.requireNonNull(data);
		if (maxDelta < 0 || maxDelta >= data.length)
			throw new IllegalArgumentException();
		
		this.data = data;
		precomputed = new double[maxDelta + 1];
		for (int i = 0; i < precomputed.length; i++) {
			double sum = 0;
			for (int j = 0; i + j < data.length; j++)
				sum += (double)data[j] * data[i + j];
			precomputed[i] = sum;
		}
	}
	
	
	public double dotProduct(int off0, int off1, int len) {
		if (off0 > off1)
			return dotProduct(off1, off0, len);
		if (off0 < 0 || off1 < 0 || len < 0 || data.length - len < off1)
			throw new IndexOutOfBoundsException();
		int delta = off1 - off0;
		if (delta > precomputed.length)
			throw new IllegalArgumentException();
		
		double removal = 0;
		for (int i = 0; i < off0; i++)
			removal += (double)data[i] * data[i + delta];
		for (int i = off1 + len; i < data.length; i++)
			removal += (double)data[i] * data[i - delta];
		return precomputed[delta] - removal;
	}
	
}
