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
		
		// Parse header blocks
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
	
	
	// Reads some bytes, performs decoding computations, and stores new samples in all channels starting at sampleOffset.
	// Returns the number of samples in the block just processed (in the range [1, 65536]),
	// or -1 if the end of stream was encountered before any data was read.
	private int decodeFrame(int frameIndex, int sampleOffset) throws IOException, DataFormatException {
		// Handle sync bits
		int temp = in.readByte();
		if (temp == -1)
			return -1;
		if ((temp << 6 | in.readInt(6)) != 0x3FFE)  // Uint14
			throw new DataFormatException("Sync code expected");
		
		// Save and/or check various fields
		if (in.readInt(1) != 0)
			throw new DataFormatException("Reserved bit");
		int blockStrategy = in.readInt(1);
		int blockSamplesCode = in.readInt(4);
		int sampleRateCode = in.readInt(4);
		int channelAssignment = in.readInt(4);
		if (decodeSampleDepth(in.readInt(3)) != sampleDepth)
			throw new DataFormatException("Sample depth mismatch");
		if (in.readInt(1) != 0)
			throw new DataFormatException("Reserved bit");
		
		// Read and check the frame/sample position field
		long position = readUtf8Integer();
		if (blockStrategy == 0) {
			if ((position >>> 31) != 0)
				throw new DataFormatException("Frame index too large");
			if (position != frameIndex)
				throw new DataFormatException("Frame index mismatch");
		} else if (blockStrategy == 1) {
			if (position != sampleOffset)
				throw new DataFormatException("Sample offset mismatch");
		} else
			throw new AssertionError();
		
		// Read/check more fields
		int blockSamples = decodeBlockSamples(blockSamplesCode);  // May read bytes
		if (decodeSampleRate(sampleRateCode) != sampleRate)  // May read bytes
			throw new DataFormatException("Sample rate mismatch");
		int crc8 = in.readInt(8);  // End of frame header
		
		decodeSubframes(blockSamples, channelAssignment, sampleOffset);
		in.alignToByte();
		int crc16 = in.readInt(16);  // End of frame
		return blockSamples;
	}
	
	
	private void decodeSubframes(int blockSamples, int channelAssignment, int sampleOffset) throws IOException, DataFormatException {
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
	
	
	// Reads between 1 and 7 bytes of input, and returns a uint36 value.
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
	
	
	// Argument is uint4, return value is in the range [1, 65536], may read 2 bytes of input.
	private int decodeBlockSamples(int code) throws IOException, DataFormatException {
		if ((code >>> 4) != 0)
			throw new IllegalArgumentException();
		else if (code == 0)
			throw new DataFormatException("Reserved");
		else if (code == 1)
			return 192;
		else if (2 <= code && code <= 5)
			return 576 << (code - 2);
		else if (code == 6)
			return in.readInt(8) + 1;
		else if (code == 7)
			return in.readInt(16) + 1;
		else if (8 <= code && code <= 15)
			return 256 << (code - 8);
		else
			throw new AssertionError();
	}
	
	
	// Argument is uint4, may read 2 bytes of input.
	private int decodeSampleRate(int code) throws IOException, DataFormatException {
		if ((code >>> 4) != 0)
			throw new IllegalArgumentException();
		else if (code == 0)
			return sampleRate;
		else if (code < SAMPLE_RATES.length)
			return SAMPLE_RATES[code];
		else if (code == 12)
			return in.readInt(8);
		else if (code == 13)
			return in.readInt(16);
		else if (code == 14)
			return in.readInt(16) * 10;
		else if (code == 15)
			throw new DataFormatException("Invalid sample rate");
		else
			throw new AssertionError();
	}
	
	private static final int[] SAMPLE_RATES = {-1, 88200, 176400, 192000, 8000, 16000, 22050, 24000, 32000, 44100, 48000, 96000};
	
	
	// Argument is uint3, return value is in the range [1, 32], performs no I/O.
	private int decodeSampleDepth(int code) throws DataFormatException {
		if ((code >>> 3) != 0)
			throw new IllegalArgumentException();
		else if (code == 0)
			return sampleDepth;
		else if (SAMPLE_DEPTHS[code] < 0)
			throw new DataFormatException("Reserved bit depth");
		else
			return SAMPLE_DEPTHS[code];
	}
	
	private static final int[] SAMPLE_DEPTHS = {-1, 8, 12, -1, 16, 20, 24, -1};
	
	
	private static short clamp(int val) {
		if (val < Short.MIN_VALUE)
			return Short.MIN_VALUE;
		else if (val > Short.MAX_VALUE)
			return Short.MAX_VALUE;
		else
			return (short)val;
	}
	
}
