/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac;

import java.io.IOException;
import java.util.Arrays;
import java.util.zip.DataFormatException;


public class FlacDecoder {
	
	/*---- Fields ----*/
	
	public int sampleRate;
	public int sampleDepth;
	public int numChannels;
	public short[][] samples;
	
	private BitInputStream in;
	
	
	
	/*---- Constructors ----*/
	
	// Constructs a FLAC decoder from the given input stream, and immediately
	// performs full decoding of the data until the end of stream is reached.
	public FlacDecoder(BitInputStream in) throws IOException, DataFormatException {
		// Initialize some fields
		this.in = in;
		
		// Parse data chunks
		if (in.readInt(32) != 0x664C6143)  // Magic string "fLaC"
			throw new DataFormatException();
		while (handleMetadataBlock());
		
		// Decode frames until end of stream
		for (int i = 0, sampleOffset = 0; ; i++) {
			int numSamples = decodeFrame(i, sampleOffset);
			if (numSamples == -1)
				break;
			sampleOffset += numSamples;
		}
	}
	
	
	
	/*---- Methods ----*/
	
	private boolean handleMetadataBlock() throws IOException, DataFormatException {
		boolean last = in.readInt(1) != 0;
		int type = in.readInt(7);
		int length = in.readInt(24);
		if (type == 0)
			parseStreamInfoData();
		else {
			byte[] data = new byte[length];
			in.readFully(data);
		}
		return !last;
	}
	
	
	private void parseStreamInfoData() throws IOException, DataFormatException {
		int minBlockSamples = in.readInt(16);
		int maxBlockSamples = in.readInt(16);
		int minFrameBytes = in.readInt(24);
		int maxFrameBytes = in.readInt(24);
		sampleRate = in.readInt(20);
		if (sampleRate == 0 || sampleRate > 655350)
			throw new DataFormatException("Invalid sample rate");
		numChannels = in.readInt(3) + 1;
		sampleDepth = in.readInt(5) + 1;
		long numSamples = (long)in.readInt(18) << 18 | in.readInt(18);
		samples = new short[numChannels][(int)numSamples];
		byte[] hash = new byte[16];
		in.readFully(hash);
	}
	
	
	private int decodeFrame(int frameIndex, int sampleOffset) throws IOException, DataFormatException {
		int temp = in.readByte();
		if (temp == -1)
			return -1;
		if ((temp << 6 | in.readInt(6)) != 0x3FFE)  // Uint14
			throw new DataFormatException("Sync code expected");
		if (in.readInt(1) != 0)
			throw new DataFormatException("Reserved bit");
		int blockStrategy = in.readInt(1);
		
		int blockSamplesCode = in.readInt(4);
		int sampleRateCode = in.readInt(4);
		
		int channelAssignment = in.readInt(4);
		int sampleDepthCode = in.readInt(3);
		if (in.readInt(1) != 0)
			throw new DataFormatException("Reserved bit");
		
		int sampleDepth;
		switch (sampleDepthCode) {
			case 0:  sampleDepth = this.sampleDepth;  break;
			case 1:  sampleDepth =  8;  break;
			case 2:  sampleDepth = 12;  break;
			case 4:  sampleDepth = 16;  break;
			case 5:  sampleDepth = 20;  break;
			case 6:  sampleDepth = 24;  break;
			case 3:
			case 7:
				throw new DataFormatException("Reserved bit depth");
			default:  throw new AssertionError();
		}
		if (sampleDepth != this.sampleDepth)
			throw new DataFormatException("Sample depth mismatch");
		
		long position = readUtf8Integer();
		if (blockStrategy == 0 && position != frameIndex)
			throw new DataFormatException("Frame index mismatch");
		
		int blockSamples;
		switch (blockSamplesCode) {
			case 0:
				throw new DataFormatException("Reserved");
			case 1:
				blockSamples = 192;
				break;
			case 2:
			case 3:
			case 4:
			case 5:
				blockSamples = 576 << (blockSamplesCode - 2);
				break;
			case 6:
				blockSamples = in.readInt(8) + 1;
				break;
			case 7:
				blockSamples = in.readInt(16) + 1;
				break;
			case 8:
			case 9:
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				blockSamples = 256 << (blockSamplesCode - 8);
				break;
			default:
				throw new AssertionError();
		}
		
		int sampleRate;
		switch (sampleRateCode) {
			case  0:  sampleRate = this.sampleRate;  break;
			case  1:  sampleRate =  88200;  break;
			case  2:  sampleRate = 176400;  break;
			case  3:  sampleRate = 192000;  break;
			case  4:  sampleRate =   8000;  break;
			case  5:  sampleRate =  16000;  break;
			case  6:  sampleRate =  22050;  break;
			case  7:  sampleRate =  24000;  break;
			case  8:  sampleRate =  32000;  break;
			case  9:  sampleRate =  44100;  break;
			case 10:  sampleRate =  48000;  break;
			case 11:  sampleRate =  96000;  break;
			case 12:  sampleRate = in.readInt(8);  break;
			case 13:  sampleRate = in.readInt(16);  break;
			case 14:  sampleRate = in.readInt(16) * 10;  break;
			case 15:  throw new DataFormatException("Invalid sample rate");
			default:
				throw new AssertionError();
		}
		if (sampleRate != this.sampleRate)
			throw new DataFormatException("Sample rate mismatch");
		
		int crc8 = in.readInt(8);
		if (channelAssignment < 8) {  // Independent channels
			if (channelAssignment + 1 != numChannels)
				throw new DataFormatException("Channel count mismatch");
			for (int j = 0; j < numChannels; j++) {
				int[] block = decodeSubframe(blockSamples, j, sampleDepth);
				for (int i = 0; i < block.length; i++)
					samples[j][sampleOffset + i] = clamp(block[i]);
			}
			
		} else if (channelAssignment == 8) {  // Left-side stereo
			if (numChannels != 2)
				throw new DataFormatException("Channel count mismatch");
			int[] left = decodeSubframe(blockSamples, 0, sampleDepth);
			int[] side = decodeSubframe(blockSamples, 1, sampleDepth + 1);
			for (int i = 0; i < blockSamples; i++) {
				samples[0][sampleOffset + i] = clamp(left[i]);
				samples[1][sampleOffset + i] = clamp(left[i] - side[i]);
			}
			
		} else if (channelAssignment == 9) {  // Side-right stereo
			if (numChannels != 2)
				throw new DataFormatException("Channel count mismatch");
			int[] side  = decodeSubframe(blockSamples, 0, sampleDepth + 1);
			int[] right = decodeSubframe(blockSamples, 1, sampleDepth);
			for (int i = 0; i < blockSamples; i++) {
				samples[0][sampleOffset + i] = clamp(right[i] + side[i]);
				samples[1][sampleOffset + i] = clamp(right[i]);
			}
			
		} else if (channelAssignment == 10) {  // Mid-side stereo
			if (numChannels != 2)
				throw new DataFormatException("Channel count mismatch");
			int[] mid  = decodeSubframe(blockSamples, 0, sampleDepth);
			int[] side = decodeSubframe(blockSamples, 1, sampleDepth + 1);
			for (int i = 0; i < blockSamples; i++) {
				int s = side[i];
				int m = (mid[i] << 1) | (s & 1);
				samples[0][sampleOffset + i] = clamp((m + s) >> 1);
				samples[1][sampleOffset + i] = clamp((m - s) >> 1);
			}
			
		} else
			throw new DataFormatException("Reserved channel assignment");
		
		in.alignToByte();
		int crc16 = in.readInt(16);
		return blockSamples;
	}
	
	
	private int[] decodeSubframe(int numSamples, int channelIndex, int sampleDepth) throws IOException, DataFormatException {
		if (in.readInt(1) != 0)
			throw new DataFormatException("Invalid padding bit");
		int type = in.readInt(6);
		int shift = in.readInt(1);  // Also known as "wasted bits-per-sample"
		if (shift == 1) {
			while (in.readInt(1) == 0)  // Unary coding
				shift++;
		}
		
		int[] block = new int[numSamples];
		if (type == 0) {
			Arrays.fill(block, in.readInt(sampleDepth));
		} else if (type == 1) {
			for (int i = 0; i < block.length; i++)
				block[i] = in.readSignedInt(sampleDepth);
		} else if (type < 8)
			throw new DataFormatException("Reserved subframe type");
		else if (type <= 12)
			decodeFixedPrediction(type - 8, block, sampleDepth);
		else if (type < 32)
			throw new DataFormatException("Reserved subframe type");
		else if (type < 64)
			decodeLinearPredictiveCoding(type - 31, block, sampleDepth);
		else
			throw new AssertionError();
		
		for (int i = 0; i < block.length; i++)
			block[i] <<= shift;
		return block;
	}
	
	
	private void decodeFixedPrediction(int order, int[] block, int sampleDepth)
			throws IOException, DataFormatException {
		if (order < 0 || order > 4)
			throw new IllegalArgumentException();
		
		for (int i = 0; i < order; i++)
			block[i] = in.readSignedInt(sampleDepth);
		
		int[] residuals = readResiduals(block.length, order);
		System.arraycopy(residuals, 0, block, order, residuals.length);
		switch (order) {
			case 0:  break;
			case 1:  restoreLpc(block, new int[]{1}, 0);  break;
			case 2:  restoreLpc(block, new int[]{2, -1}, 0);  break;
			case 3:  restoreLpc(block, new int[]{3, -3, 1}, 0);  break;
			case 4:  restoreLpc(block, new int[]{4, -6, 4, -1}, 0);  break;
			default:  throw new AssertionError();
		}
	}
	
	
	private void decodeLinearPredictiveCoding(int order, int[] block, int sampleDepth)
			throws IOException, DataFormatException {
		if (order < 1 || order > 32)
			throw new IllegalArgumentException();
		
		for (int i = 0; i < order; i++)
			block[i] = in.readSignedInt(sampleDepth);
		
		int precision = in.readInt(4) + 1;
		if (precision == 16)
			throw new DataFormatException("Invalid LPC precision");
		int shift = in.readSignedInt(5);
		if (shift < 0)
			throw new DataFormatException("Invalid LPC shift");
		
		int[] coefs = new int[order];
		for (int i = 0; i < coefs.length; i++)
			coefs[i] = in.readSignedInt(precision);
		
		int[] residuals = readResiduals(block.length, order);
		System.arraycopy(residuals, 0, block, order, residuals.length);
		restoreLpc(block, coefs, shift);
	}
	
	
	// Updates the values of block[coefs.length : block.length] according to linear predictive coding.
	private void restoreLpc(int[] block, int[] coefs, int shift) {
		for (int i = coefs.length; i < block.length; i++) {
			long val = 0;
			for (int j = 0; j < coefs.length; j++)
				val += (long)block[i - 1 - j] * coefs[j];
			block[i] += (int)(val >> shift);
		}
	}
	
	
	private int[] readResiduals(int count, int warmup) throws IOException, DataFormatException {
		int[] result = new int[count - warmup];
		int method = in.readInt(2);
		if (method == 0 || method == 1) {
			int numPartitions = 1 << in.readInt(4);
			int paramBits = method == 0 ? 4 : 5;
			int escape = method == 0 ? 0xF : 0x1F;
			for (int partitionIndex = 0, resultIndex = 0; partitionIndex < numPartitions; partitionIndex++) {
				int subcount = count / numPartitions;
				if (partitionIndex == 0)
					subcount -= warmup;
				int param = in.readInt(paramBits);
				if (param == escape) {
					int numBits = in.readInt(5);
					for (int i = 0; i < subcount; i++, resultIndex++)
						result[resultIndex] = in.readSignedInt(numBits);
				} else {
					for (int i = 0; i < subcount; i++, resultIndex++)
						result[resultIndex] = readRiceSignedInt(param);
				}
			}
		} else
			throw new DataFormatException("Reserved residual coding method");
		return result;
	}
	
	
	private int readRiceSignedInt(int param) throws IOException {
		int result = 0;
		while (in.readInt(1) == 0)
			result++;
		result = (result << param) | in.readInt(param);
		return (result >>> 1) ^ (-(result & 1));
	}
	
	
	// Read between 1 and 7 bytes of input, and returns a uint36 value.
	// See: https://hydrogenaud.io/index.php/topic,112831.msg929128.html#msg929128
	private long readUtf8Integer() throws IOException, DataFormatException {
		int temp = in.readInt(8);
		int n = Integer.numberOfLeadingZeros(~(temp << 24));  // Number of leading 1s in the byte
		if (n < 0 || n > 8)
			throw new AssertionError();
		else if (n == 0)
			return temp;
		else if (n == 1 || n == 8)
			throw new DataFormatException("Invalid UTF-8 coded number");
		else {
			long result = temp & ((1 << (7 - n)) - 1);
			for (int i = 0; i < n - 1; i++) {
				temp = in.readInt(8);
				if ((temp & 0xC0) != 0x80)
					throw new DataFormatException("Invalid UTF-8 coded number");
				result = (result << 6) | (temp & 0x3F);
			}
			return result;
		}
	}
	
	
	private static short clamp(int val) {
		if (val < Short.MIN_VALUE)
			return Short.MIN_VALUE;
		else if (val > Short.MAX_VALUE)
			return Short.MAX_VALUE;
		else
			return (short)val;
	}
	
}
