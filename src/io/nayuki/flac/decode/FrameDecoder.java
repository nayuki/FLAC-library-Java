/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.decode;

import java.io.IOException;
import java.util.Arrays;
import java.util.zip.DataFormatException;


// Note: Objects are stateful and not thread-safe, because of the bit input stream field and private temporary arrays.
public final class FrameDecoder {
	
	/*---- Fields ----*/
	
	public BitInputStream in;
	
	// Temporary arrays to hold two decoded audio channels. The maximum possible block size is either
	// 65536 from the frame header logic, or 65535 from a strict reading of the FLAC specification.
	// Two buffers are needed due to stereo techniques like mid-side processing, but not more than
	// two buffers because all other multi-channel audio is processed independently per channel.
	private long[] temp0;
	private long[] temp1;
	
	// The number of samples (per channel) in the current block/frame being processed.
	// This value is only valid while the method readFrame() is on the call stack.
	private int currentBlockSize;
	
	
	
	/*---- Constructors ----*/
	
	public FrameDecoder(BitInputStream in) {
		this.in = in;
		temp0 = new long[65536];
		temp1 = new long[65536];
	}
	
	
	
	/*---- Frame header decoding methods ----*/
	
	// Reads and decodes the next FLAC frame, storing output samples and returning metadata.
	// The bit input stream must be initially aligned at a byte boundary. If EOF is encountered before
	// any actual bytes were read, then this returns null. Otherwise this function either successfully
	// decodes a frame and returns a new metadata object, or throws an appropriate exception. A frame
	// may have up to 8 channels and 65536 samples, so the output arrays need to be sized appropriately.
	public FrameMetadata readFrame(int[][] outSamples, int outOffset)
			throws IOException, DataFormatException {
		
		// Preliminaries
		in.resetCrcs();
		int temp = in.readByte();
		if (temp == -1)
			return null;
		FrameMetadata result = new FrameMetadata();
		
		// Read sync bits
		int sync = temp << 6 | in.readUint(6);  // Uint14
		if (sync != 0x3FFE)
			throw new DataFormatException("Sync code expected");
		
		// Read various simple fields
		if (in.readUint(1) != 0)
			throw new DataFormatException("Reserved bit");
		int blockStrategy = in.readUint(1);
		int blockSamplesCode = in.readUint(4);
		int sampleRateCode = in.readUint(4);
		int channelAssignment = in.readUint(4);
		if (channelAssignment < 8)
			result.numChannels = channelAssignment + 1;
		else if (8 <= channelAssignment && channelAssignment <= 10)
			result.numChannels = 2;
		else
			throw new DataFormatException("Reserved channel assignment");
		result.sampleDepth = decodeSampleDepth(in.readUint(3));
		if (in.readUint(1) != 0)
			throw new DataFormatException("Reserved bit");
		
		// Read and check the frame/sample position field
		long position = readUtf8Integer();
		if (blockStrategy == 0) {
			if ((position >>> 31) != 0)
				throw new DataFormatException("Frame index too large");
			result.frameIndex = (int)position;
			result.sampleOffset = -1;
		} else if (blockStrategy == 1) {
			if ((position >>> 36) != 0)
				throw new AssertionError();
			result.sampleOffset = position;
			result.frameIndex = -1;
		} else
			throw new AssertionError();
		
		// Read variable-length data for some fields
		currentBlockSize = decodeBlockSamples(blockSamplesCode);  // Reads 0 to 2 bytes
		result.numSamples = currentBlockSize;
		result.sampleRate = decodeSampleRate(sampleRateCode);  // Reads 0 to 2 bytes
		int computedCrc8 = in.getCrc8();
		if (in.readUint(8) != computedCrc8)
			throw new DataFormatException("CRC-8 mismatch");
		
		// Do the hard work
		decodeSubframes(result.sampleDepth, channelAssignment, outSamples, outOffset);
		
		// Read padding and footer
		while (in.getBitPosition() != 0) {
			if (in.readUint(1) != 0)
				throw new DataFormatException("Invalid padding bit");
		}
		int computedCrc16 = in.getCrc16();
		if (in.readUint(16) != computedCrc16)
			throw new DataFormatException("CRC-16 mismatch");
		return result;
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
			return -1;
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
	
	
	// Argument is uint3, return value is in the range [-1, 32], performs no I/O.
	private static int decodeSampleDepth(int code) throws DataFormatException {
		if ((code >>> 3) != 0)
			throw new IllegalArgumentException();
		else if (code == 0)
			return -1;
		else if (SAMPLE_DEPTHS[code] < 0)
			throw new DataFormatException("Reserved bit depth");
		else
			return SAMPLE_DEPTHS[code];
	}
	
	private static final int[] SAMPLE_DEPTHS = {-1, 8, 12, -1, 16, 20, 24, -1};
	
	
	
	/*---- Sub-frame audio data decoding methods ----*/
	
	private void decodeSubframes(int sampleDepth, int chanAsgn, int[][] outSamples, int outOffset)
			throws IOException, DataFormatException {
		if ((chanAsgn >>> 4) != 0)
			throw new IllegalArgumentException();
		
		if (0 <= chanAsgn && chanAsgn <= 7) {  // Independent channels
			int numChannels = chanAsgn + 1;
			for (int ch = 0; ch < numChannels; ch++) {
				decodeSubframe(sampleDepth, temp0);
				int[] outChan = outSamples[ch];
				for (int i = 0; i < currentBlockSize; i++)
					outChan[outOffset + i] = (int)temp0[i];
			}
			
		} else if (8 <= chanAsgn && chanAsgn <= 10) {  // Side-coded stereo methods
			decodeSubframe(sampleDepth + (chanAsgn == 9 ? 1 : 0), temp0);
			decodeSubframe(sampleDepth + (chanAsgn == 9 ? 0 : 1), temp1);
			
			if (chanAsgn == 8) {  // Left-side stereo
				for (int i = 0; i < currentBlockSize; i++)
					temp1[i] = temp0[i] - temp1[i];
			} else if (chanAsgn == 9) {  // Side-right stereo
				for (int i = 0; i < currentBlockSize; i++)
					temp0[i] += temp1[i];
			} else if (chanAsgn == 10) {  // Mid-side stereo
				for (int i = 0; i < currentBlockSize; i++) {
					long s = temp1[i];
					long m = (temp0[i] << 1) | (s & 1);
					temp0[i] = (m + s) >> 1;
					temp1[i] = (m - s) >> 1;
				}
			}
			
			int[] outLeft  = outSamples[0];
			int[] outRight = outSamples[1];
			for (int i = 0; i < currentBlockSize; i++) {
				outLeft [outOffset + i] = (int)temp0[i];
				outRight[outOffset + i] = (int)temp1[i];
			}
		} else  // 11 <= channelAssignment <= 15
			throw new DataFormatException("Reserved channel assignment");
	}
	
	
	private void decodeSubframe(int sampleDepth, long[] result) throws IOException, DataFormatException {
		if (in.readUint(1) != 0)
			throw new DataFormatException("Invalid padding bit");
		int type = in.readUint(6);
		int shift = in.readUint(1);  // Also known as "wasted bits-per-sample"
		if (shift == 1) {
			while (in.readUint(1) == 0)  // Unary coding
				shift++;
		}
		sampleDepth -= shift;
		
		if (type == 0) {
			Arrays.fill(result, 0, currentBlockSize, in.readSignedInt(sampleDepth));
		} else if (type == 1) {
			for (int i = 0; i < currentBlockSize; i++)
				result[i] = in.readSignedInt(sampleDepth);
		} else if (type < 8)
			throw new DataFormatException("Reserved subframe type");
		else if (type <= 12)
			decodeFixedPrediction(type - 8, sampleDepth, result);
		else if (type < 32)
			throw new DataFormatException("Reserved subframe type");
		else if (type < 64)
			decodeLinearPredictiveCoding(type - 31, sampleDepth, result);
		else
			throw new AssertionError();
		
		for (int i = 0; i < currentBlockSize; i++)
			result[i] <<= shift;
	}
	
	
	private void decodeFixedPrediction(int order, int sampleDepth, long[] result)
			throws IOException, DataFormatException {
		if (order < 0 || order > 4)
			throw new IllegalArgumentException();
		
		for (int i = 0; i < order; i++)
			result[i] = in.readSignedInt(sampleDepth);
		
		readResiduals(order, result);
		restoreLpc(result, FIXED_PREDICTION_COEFFICIENTS[order], 0);
	}
	
	private static final int[][] FIXED_PREDICTION_COEFFICIENTS = {
		{},
		{1},
		{2, -1},
		{3, -3, 1},
		{4, -6, 4, -1},
	};
	
	
	private void decodeLinearPredictiveCoding(int order, int sampleDepth, long[] result)
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
		
		readResiduals(order, result);
		restoreLpc(result, coefs, shift);
	}
	
	
	// Updates the values of block[coefs.length : currentBlockSize] according to linear predictive coding.
	private void restoreLpc(long[] result, int[] coefs, int shift) {
		for (int i = coefs.length; i < currentBlockSize; i++) {
			long sum = 0;
			for (int j = 0; j < coefs.length; j++)
				sum += result[i - 1 - j] * coefs[j];
			result[i] += sum >> shift;
		}
	}
	
	
	// Reads metadata and Rice-coded numbers from the input stream, storing them in result[warmup : currentBlockSize].
	private void readResiduals(int warmup, long[] result) throws IOException, DataFormatException {
		int method = in.readUint(2);
		if (method == 0 || method == 1) {
			int partitionOrder = in.readUint(4);
			int numPartitions = 1 << partitionOrder;
			if (currentBlockSize % numPartitions != 0)
				throw new DataFormatException("Block size not divisible by number of Rice partitions");
			int paramBits = method == 0 ? 4 : 5;
			int escapeParam = method == 0 ? 0xF : 0x1F;
			for (int inc = currentBlockSize >>> partitionOrder, partEnd = inc, resultIndex = warmup;
					partEnd <= currentBlockSize; partEnd += inc) {
				
				int param = in.readUint(paramBits);
				if (param == escapeParam) {
					int numBits = in.readUint(5);
					for (; resultIndex < partEnd; resultIndex++)
						result[resultIndex] = in.readSignedInt(numBits);
				} else {
					for (; resultIndex < partEnd; resultIndex++)
						result[resultIndex] = in.readRiceSignedInt(param);
				}
			}
		} else  // method == 2, 3
			throw new DataFormatException("Reserved residual coding method");
	}
	
}
