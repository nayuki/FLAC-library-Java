/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac;

import java.io.IOException;
import java.util.Objects;


final class LinearPredictiveEncoder extends SubframeEncoder {
	
	public static SizeEstimate<SubframeEncoder> computeBest(long[] data, int shift, int depth, int order, FastDotProduct fdp) {
		LinearPredictiveEncoder enc = new LinearPredictiveEncoder(data, shift, depth, order, fdp);
		return new SizeEstimate<SubframeEncoder>(enc.getEncodedBitLength(), enc);
	}
	
	
	
	private final int order;
	private final int[] coefficients;
	private final int coefDepth;
	private final int coefShift;
	
	
	public LinearPredictiveEncoder(long[] data, int shift, int depth, int order, FastDotProduct fdp) {
		super(shift, depth);
		int numSamples = data.length;
		if (order < 1 || order > 32 || numSamples < order)
			throw new IllegalArgumentException();
		this.order = order;
		
		data = data.clone();
		for (int i = 0; i < data.length; i++)
			data[i] >>= shift;
		
		// Set up matrix to solve linear least squares problem
		double[][] matrix = new double[order][order + 1];
		for (int r = 0; r < matrix.length; r++) {
			for (int c = 0; c < matrix[r].length; c++) {
				double val;
				if (c >= r)
					val = fdp.dotProduct(r, c, data.length - order);
				else
					val = matrix[c][r];
				matrix[r][c] = val;
			}
		}
		
		// Solve matrix, then examine range of coefficients
		double[] coefs = solveMatrix(matrix);
		double maxCoef = 0;
		for (double x : coefs)
			maxCoef = Math.max(Math.abs(x), maxCoef);
		int wholeBits = maxCoef >= 1 ? (int)(Math.log(maxCoef) / Math.log(2)) + 1 : 0;
		
		// Quantize and store the coefficients
		coefficients = new int[order];
		coefDepth = 15;  // The maximum possible
		coefShift = coefDepth - 1 - wholeBits;
		for (int i = 0; i < coefs.length; i++) {
			double coef = coefs[coefs.length - 1 - i];
			int val = (int)Math.round(coef * (1 << coefShift));
			coefficients[i] = Math.max(Math.min(val, (1 << (coefDepth - 1)) - 1), -(1 << (coefDepth - 1)));
		}
		
		applyLpc(data, coefficients, coefShift);
		int temp = (int)(RiceEncoder.computeBestSizeAndOrder(data, order) >>> 4);
		encodedBitLength = 1 + 6 + 1 + shift + order * depth + temp;
	}
	
	
	private double[] solveMatrix(double[][] mat) {
		// Gauss-Jordan elimination algorithm
		int rows = mat.length;
		int cols = mat[0].length;
		if (rows + 1 != cols)
			throw new IllegalArgumentException();
		
		// Forward elimination
		int numPivots = 0;
		for (int j = 0; j < rows && numPivots < rows; j++) {
			int pivotRow = numPivots;
			while (pivotRow < rows && mat[pivotRow][j] == 0)
				pivotRow++;
			if (pivotRow == rows)
				continue;
			double[] temp = mat[numPivots];
			mat[numPivots] = mat[pivotRow];
			mat[pivotRow] = temp;
			pivotRow = numPivots;
			numPivots++;
			
			double factor = mat[pivotRow][j];
			for (int k = 0; k < cols; k++)
				mat[pivotRow][k] /= factor;
			mat[pivotRow][j] = 1;
			
			for (int i = pivotRow + 1; i < rows; i++) {
				factor = mat[i][j];
				for (int k = 0; k < cols; k++)
					mat[i][k] -= mat[pivotRow][k] * factor;
				mat[i][j] = 0;
			}
		}
		
		// Back substitution
		double[] result = new double[rows];
		for (int i = numPivots - 1; i >= 0; i--) {
			int pivotCol = 0;
			while (pivotCol < cols && mat[i][pivotCol] == 0)
				pivotCol++;
			if (pivotCol == cols)
				continue;
			result[pivotCol] = mat[i][cols - 1];
			
			for (int j = i - 1; j >= 0; j--) {
				double factor = mat[j][pivotCol];
				for (int k = 0; k < cols; k++)
					mat[j][k] -= mat[i][k] * factor;
				mat[j][pivotCol] = 0;
			}
		}
		return result;
	}
	
	
	public void encode(long[] data, BitOutputStream out) throws IOException {
		Objects.requireNonNull(data);
		Objects.requireNonNull(out);
		if (data.length < order)
			throw new IllegalArgumentException();
		
		writeTypeAndShift(32 + order - 1, out);
		data = data.clone();
		for (int i = 0; i < data.length; i++)
			data[i] >>= sampleShift;
		
		for (int i = 0; i < order; i++)  // Warmup
			out.writeInt(sampleDepth, (int)data[i]);
		out.writeInt(4, coefDepth - 1);
		out.writeInt(5, coefShift);
		for (int x : coefficients)
			out.writeInt(coefDepth, x);
		applyLpc(data, coefficients, coefShift);
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
	
}
