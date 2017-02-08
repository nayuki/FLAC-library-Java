/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac;

import java.io.IOException;


final class RiceEncoder {
	
	/*---- Functions for size calculation ---*/
	
	// Calculates the number of bits needed to encode the sequence of values data[warmup : data.length].
	public static long computeBestSizeAndOrder(long[] data, int warmup) {
		long bestSize = Integer.MAX_VALUE;
		int bestOrder = -1;
		
		int[] escapeBits = null;
		int[] bitsAtParam = null;
		for (int order = 15; order >= 0; order--) {
			int partSize = data.length >>> order;
			if ((partSize << order) != data.length || partSize < warmup)
				continue;
			int numPartitions = 1 << order;
			
			if (escapeBits == null) {
				escapeBits = new int[numPartitions];
				bitsAtParam = new int[numPartitions * 16];
				for (int i = warmup; i < data.length; i++) {
					int j = i / partSize;
					long val = data[i];
					escapeBits[j] = Math.max(65 - Long.numberOfLeadingZeros(val ^ (val >> 63)), escapeBits[j]);
					val = (val >= 0) ? (val << 1) : (((-val) << 1) - 1);
					for (int param = 0; param < 15; param++, val >>>= 1)
						bitsAtParam[param + j * 16] += val + 1 + param;
				}
			} else {
				for (int i = 0; i < numPartitions; i++) {
					int j = i << 1;
					escapeBits[i] = Math.max(escapeBits[j], escapeBits[j + 1]);
					for (int param = 0; param < 15; param++)
						bitsAtParam[param + i * 16] = bitsAtParam[param + j * 16] + bitsAtParam[param + (j + 1) * 16];
				}
			}
			
			long size = 4 + (4 << order);
			for (int i = 0; i < numPartitions; i++) {
				int min = 5 + escapeBits[i] * (partSize - (i == 0 ? warmup : 0));
				for (int param = 0; param < 15; param++)
					min = Math.min(bitsAtParam[param + i * 16], min);
				size += min;
			}
			if (size < bestSize) {
				bestSize = size;
				bestOrder = order;
			}
		}
		
		if (bestSize == Integer.MAX_VALUE)
			throw new AssertionError();
		return bestSize << 4 | bestOrder;
	}
	
	
	// Calculates the number of bits needed to encode the sequence of values
	// data[start : end] with an optimally chosen Rice parameter.
	private static long computeBestSizeAndParam(long[] data, int start, int end) {
		// Use escape code
		int bestParam;
		long bestSize = 4 + 5;
		{
			long accumulator = 0;
			for (int i = start; i < end; i++) {
				long val = data[i];
				accumulator |= val ^ (val >> 63);
			}
			int numBits = 65 - Long.numberOfLeadingZeros(accumulator);
			bestSize += (end - start) * numBits;
			bestParam = 16 + numBits;
			if ((bestParam >>> 7) != 0)
				throw new AssertionError();
		}
		
		// Use Rice coding
		for (int param = 0; param <= 14; param++) {
			long size = 4;
			for (int i = start; i < end; i++) {
				long val = data[i];
				if (val >= 0)
					val <<= 1;
				else
					val = ((-val) << 1) - 1;
				size += (val >>> param) + 1 + param;
			}
			if (size < bestSize) {
				bestSize = size;
				bestParam = param;
			}
		}
		return bestSize << 7 | bestParam;
	}
	
	
	
	/*---- Functions for encoding data ---*/
	
	// Encodes the sequence of values data[warmup : data.length] with an appropriately chosen order and Rice parameters.
	public static void encode(long[] data, int warmup, BitOutputStream out) throws IOException {
		out.writeInt(2, 0);
		int order = (int)computeBestSizeAndOrder(data, warmup) & 0xF;
		out.writeInt(4, order);
		int numPartitions = 1 << order;
		int start = warmup;
		int end = data.length >>> order;
		for (int i = 0; i < numPartitions; i++) {
			int param = (int)computeBestSizeAndParam(data, start, end) & 0x7F;
			encode(data, start, end, param, out);
			start = end;
			end += data.length >>> order;
		}
	}
	
	
	// Encodes the sequence of values data[start : end] with the given Rice parameter.
	private static void encode(long[] data, int start, int end, int param, BitOutputStream out) throws IOException {
		if (param < 15) {
			out.writeInt(4, param);
			for (int j = start; j < end; j++)
				writeRiceSignedInt(data[j], param, out);
		} else {
			out.writeInt(4, 15);
			int numBits = param - 16;
			out.writeInt(5, numBits);
			for (int j = start; j < end; j++)
				out.writeInt(numBits, (int)data[j]);
		}
	}
	
	
	private static void writeRiceSignedInt(long val, int param, BitOutputStream out) throws IOException {
		long unsigned = val >= 0 ? val << 1 : ((-val) << 1) - 1;
		int unary = (int)(unsigned >>> param);
		for (int i = 0; i < unary; i++)
			out.writeInt(1, 0);
		out.writeInt(1, 1);
		out.writeInt(param, (int)unsigned);
	}
	
}
