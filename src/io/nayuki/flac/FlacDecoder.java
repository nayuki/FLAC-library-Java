/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.DataFormatException;


public final class FlacDecoder {
	
	/*---- Fields ----*/
	
	public int sampleRate;
	public int sampleDepth;
	public int numChannels;
	public int[][] samples;
	
	private BitInputStream in;
	private boolean steamInfoSeen;
	private long[][] subframes;  // Temporary arrays, reused on every call of decodeSubframes()
	
	
	
	/*---- Constructors ----*/
	
	// Constructs a FLAC decoder from the given input stream, and immediately
	// performs full decoding of the data until the end of stream is reached.
	public FlacDecoder(BitInputStream in) throws IOException, DataFormatException {
		// Initialize some fields
		Objects.requireNonNull(in);
		this.in = in;
		steamInfoSeen = false;
		subframes = new long[2][FRAME_MAX_SAMPLES];
		
		// Parse header blocks
		if (in.readUint(32) != 0x664C6143)  // Magic string "fLaC"
			throw new DataFormatException("Invalid magic string");
		while (handleMetadataBlock());
		
		// Decode frames until end of stream
		for (int i = 0, sampleOffset = 0; ; i++) {
			int numSamples = decodeFrame(i, sampleOffset);
			if (numSamples == -1)
				break;
			sampleOffset += numSamples;
		}
	}
	
	
	
	/*---- Methods to handle metadata blocks ----*/
	
	private boolean handleMetadataBlock() throws IOException, DataFormatException {
		boolean last = in.readUint(1) != 0;
		int type = in.readUint(7);
		int length = in.readUint(24);
		if (type == 0)
			parseStreamInfoData();
		else {
			byte[] data = new byte[length];
			in.readFully(data);
		}
		return !last;
	}
	
	
	private void parseStreamInfoData() throws IOException, DataFormatException {
		if (steamInfoSeen)
			throw new DataFormatException("Duplicate stream info block");
		steamInfoSeen = true;
		int minBlockSamples = in.readUint(16);
		int maxBlockSamples = in.readUint(16);
		int minFrameBytes = in.readUint(24);
		int maxFrameBytes = in.readUint(24);
		if (maxBlockSamples < minBlockSamples)
			throw new DataFormatException("Maximum block size less than minimum block size");
		if (minFrameBytes != 0 && maxFrameBytes != 0 && maxFrameBytes < minFrameBytes)
			throw new DataFormatException("Maximum frame size less than minimum frame size");
		sampleRate = in.readUint(20);
		if (sampleRate == 0 || sampleRate > 655350)
			throw new DataFormatException("Invalid sample rate");
		numChannels = in.readUint(3) + 1;
		sampleDepth = in.readUint(5) + 1;
		long numSamples = (long)in.readUint(18) << 18 | in.readUint(18);
		samples = new int[numChannels][(int)numSamples];
		byte[] hash = new byte[16];
		in.readFully(hash);
	}
	
	
	// Reads between 1 and 7 bytes of input, and returns a uint36 value.
	// See: https://hydrogenaud.io/index.php/topic,112831.msg929128.html#msg929128
	private long readUtf8Integer() throws IOException, DataFormatException {
		int temp = in.readUint(8);
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
				temp = in.readUint(8);
				if ((temp & 0xC0) != 0x80)
					throw new DataFormatException("Invalid UTF-8 coded number");
				result = (result << 6) | (temp & 0x3F);
			}
			if ((result >>> 36) != 0)
				throw new AssertionError();
			return result;
		}
	}
	
	
	
	/*---- Methods to handle audio frames ----*/
	
	// Reads some bytes, performs decoding computations, and stores new samples in all channels starting at sampleOffset.
	// Returns the number of samples in the block just processed (in the range [1, FRAME_MAX_SAMPLES]),
	// or -1 if the end of stream was encountered before any data was read.
	private int decodeFrame(int frameIndex, int sampleOffset) throws IOException, DataFormatException {
		if (frameIndex < 0 || sampleOffset < 0)
			throw new IllegalArgumentException();
		
		// Handle sync bits
		int temp = in.readByte();
		if (temp == -1)
			return -1;
		int sync = temp << 6 | in.readUint(6);  // Uint14
		if (sync != 0x3FFE)
			throw new DataFormatException("Sync code expected");
		
		// Save and/or check various fields
		if (in.readUint(1) != 0)
			throw new DataFormatException("Reserved bit");
		int blockStrategy = in.readUint(1);
		int blockSamplesCode = in.readUint(4);
		int sampleRateCode = in.readUint(4);
		int channelAssignment = in.readUint(4);
		if (decodeSampleDepth(in.readUint(3)) != sampleDepth)
			throw new DataFormatException("Sample depth mismatch");
		if (in.readUint(1) != 0)
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
		@SuppressWarnings("unused")
		int crc8 = in.readUint(8);  // End of frame header
		
		decodeSubframes(blockSamples, channelAssignment, sampleOffset);
		in.alignToByte();
		@SuppressWarnings("unused")
		int crc16 = in.readUint(16);  // End of frame
		return blockSamples;
	}
	
	
	// Argument is uint4, return value is in the range [1, FRAME_MAX_SAMPLES], may read 2 bytes of input.
	private int decodeBlockSamples(int code) throws IOException, DataFormatException {
		if ((code >>> 4) != 0)
			throw new IllegalArgumentException();
		else if (code == 0)
			throw new DataFormatException("Reserved block size");
		else if (code == 1)
			return 192;
		else if (2 <= code && code <= 5)
			return 576 << (code - 2);
		else if (code == 6)
			return in.readUint(8) + 1;
		else if (code == 7)
			return in.readUint(16) + 1;
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
			return in.readUint(8);
		else if (code == 13)
			return in.readUint(16);
		else if (code == 14)
			return in.readUint(16) * 10;
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
	
	
	private void decodeSubframes(int blockSamples, int channelAssignment, int sampleOffset) throws IOException, DataFormatException {
		if (blockSamples < 1 || blockSamples > FRAME_MAX_SAMPLES || (channelAssignment >>> 4) != 0)
			throw new IllegalArgumentException();
		long[] temp0 = subframes[0];
		long[] temp1 = subframes[1];
		
		if (0 <= channelAssignment && channelAssignment <= 7) {  // Independent channels
			if (channelAssignment + 1 != numChannels)
				throw new DataFormatException("Channel count mismatch");
			for (int ch = 0; ch < numChannels; ch++) {
				decodeSubframe(blockSamples, sampleDepth, temp0);
				int[] outChan = samples[ch];
				for (int i = 0; i < blockSamples; i++)
					outChan[sampleOffset + i] = (int)temp0[i];
			}
			
		} else if (8 <= channelAssignment && channelAssignment <= 10) {  // Side-coded stereo methods
			if (numChannels != 2)
				throw new DataFormatException("Channel count mismatch");
			decodeSubframe(blockSamples, sampleDepth + (channelAssignment == 9 ? 1 : 0), temp0);
			decodeSubframe(blockSamples, sampleDepth + (channelAssignment == 9 ? 0 : 1), temp1);
			
			if (channelAssignment == 8) {  // Left-side stereo
				for (int i = 0; i < blockSamples; i++)
					temp1[i] = temp0[i] - temp1[i];
			} else if (channelAssignment == 9) {  // Side-right stereo
				for (int i = 0; i < blockSamples; i++)
					temp0[i] += temp1[i];
			} else if (channelAssignment == 10) {  // Mid-side stereo
				for (int i = 0; i < blockSamples; i++) {
					long s = temp1[i];
					long m = (temp0[i] << 1) | (s & 1);
					temp0[i] = (m + s) >> 1;
					temp1[i] = (m - s) >> 1;
				}
			}
			
			int[] outLeft  = samples[0];
			int[] outRight = samples[1];
			for (int i = 0; i < blockSamples; i++) {
				outLeft [sampleOffset + i] = (int)temp0[i];
				outRight[sampleOffset + i] = (int)temp1[i];
			}
		} else  // 11 <= channelAssignment <= 15
			throw new DataFormatException("Reserved channel assignment");
	}
	
	
	private void decodeSubframe(int numSamples, int sampleDepth, long[] result) throws IOException, DataFormatException {
		if (in.readUint(1) != 0)
			throw new DataFormatException("Invalid padding bit");
		int type = in.readUint(6);
		int shift = in.readUint(1);  // Also known as "wasted bits-per-sample"
		if (shift == 1) {
			while (in.readUint(1) == 0)  // Unary coding
				shift++;
		}
		
		if (type == 0) {
			Arrays.fill(result, 0, numSamples, in.readSignedInt(sampleDepth));
		} else if (type == 1) {
			for (int i = 0; i < numSamples; i++)
				result[i] = in.readSignedInt(sampleDepth);
		} else if (type < 8)
			throw new DataFormatException("Reserved subframe type");
		else if (type <= 12)
			decodeFixedPrediction(numSamples, type - 8, sampleDepth, result);
		else if (type < 32)
			throw new DataFormatException("Reserved subframe type");
		else if (type < 64)
			decodeLinearPredictiveCoding(numSamples, type - 31, sampleDepth, result);
		else
			throw new AssertionError();
		
		for (int i = 0; i < numSamples; i++)
			result[i] <<= shift;
	}
	
	
	private void decodeFixedPrediction(int numSamples, int order, int sampleDepth, long[] result)
			throws IOException, DataFormatException {
		if (order < 0 || order > 4)
			throw new IllegalArgumentException();
		
		for (int i = 0; i < order; i++)
			result[i] = in.readSignedInt(sampleDepth);
		
		readResiduals(numSamples, order, result);
		restoreLpc(numSamples, result, FIXED_PREDICTION_COEFFICIENTS[order], 0);
	}
	
	private static final int[][] FIXED_PREDICTION_COEFFICIENTS = {
		{},
		{1},
		{2, -1},
		{3, -3, 1},
		{4, -6, 4, -1},
	};
	
	
	private void decodeLinearPredictiveCoding(int numSamples, int order, int sampleDepth, long[] result)
			throws IOException, DataFormatException {
		if (order < 1 || order > 32)
			throw new IllegalArgumentException();
		
		for (int i = 0; i < order; i++)
			result[i] = in.readSignedInt(sampleDepth);
		
		int precision = in.readUint(4) + 1;
		if (precision == 16)
			throw new DataFormatException("Invalid LPC precision");
		int shift = in.readSignedInt(5);
		if (shift < 0)
			throw new DataFormatException("Invalid LPC shift");
		
		int[] coefs = new int[order];
		for (int i = 0; i < coefs.length; i++)
			coefs[i] = in.readSignedInt(precision);
		
		readResiduals(numSamples, order, result);
		restoreLpc(numSamples, result, coefs, shift);
	}
	
	
	// Updates the values of block[coefs.length : numSamples] according to linear predictive coding.
	private void restoreLpc(int numSamples, long[] result, int[] coefs, int shift) {
		if (numSamples < 0 || numSamples > result.length)
			throw new IllegalArgumentException();
		for (int i = coefs.length; i < numSamples; i++) {
			long val = 0;
			for (int j = 0; j < coefs.length; j++)
				val += result[i - 1 - j] * coefs[j];
			result[i] += val >> shift;
		}
	}
	
	
	// Reads metadata and Rice-coded numbers from the input stream, storing them in result[warmup : numSamples].
	private void readResiduals(int numSamples, int warmup, long[] result) throws IOException, DataFormatException {
		int method = in.readUint(2);
		if (method == 0 || method == 1) {
			int partitionOrder = in.readUint(4);
			int numPartitions = 1 << partitionOrder;
			if (numSamples % numPartitions != 0)
				throw new DataFormatException("Block size not divisible by number of Rice partitions");
			int paramBits = method == 0 ? 4 : 5;
			int escapeParam = method == 0 ? 0xF : 0x1F;
			
			for (int partIndex = 0, resultIndex = warmup; partIndex < numPartitions; partIndex++) {
				int subcount = numSamples >>> partitionOrder;
				if (partIndex == 0)
					subcount -= warmup;
				
				int param = in.readUint(paramBits);
				if (param == escapeParam) {
					int numBits = in.readUint(5);
					for (int i = 0; i < subcount; i++, resultIndex++)
						result[resultIndex] = in.readSignedInt(numBits);
				} else {
					for (int i = 0; i < subcount; i++, resultIndex++)
						result[resultIndex] = readRiceSignedInt(param);
				}
			}
		} else  // method == 2, 3
			throw new DataFormatException("Reserved residual coding method");
	}
	
	
	private int readRiceSignedInt(int param) throws IOException {
		int result = 0;
		while (in.readUint(1) == 0)
			result++;
		result = (result << param) | in.readUint(param);
		return (result >>> 1) ^ (-(result & 1));
	}
	
	
	private static final int FRAME_MAX_SAMPLES = 1 << 16;
	
}
