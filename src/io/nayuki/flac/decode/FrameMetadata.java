/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.decode;

import java.io.IOException;
import io.nayuki.flac.encode.BitOutputStream;


// A mutable structure holding key pieces of information from decoding a frame header.
// A frame size field is also included, although it is computed long after the header ends.
public final class FrameMetadata {
	
	/*---- Fields ----*/
	
	// Exactly one of these following two fields equals -1.
	public int frameIndex;     // Either -1 or a uint31.
	public long sampleOffset;  // Either -1 or a uint36.
	
	public int numChannels;  // In the range [1, 8]. Determined by channelAssignment.
	public int channelAssignment;  // In the range [0, 15].
	public int blockSize;    // Number of samples per channel, in the range [1, 65536].
	public int sampleRate;   // Either -1 if not encoded in the frame, or in the range [1, 655350].
	public int sampleDepth;  // Either -1 if not encoded in the frame, or in the range [8, 24].
	public int frameSize;    // Number of bytes, at least 10.
	
	
	
	/*---- Constructors ----*/
	
	// Constructs a blank frame metadata structure.
	public FrameMetadata() {}
	
	
	
	/*---- Static functions ----*/
	
	// Tries to read the next FLAC frame header from the given bit input stream.
	// The stream must be aligned to a byte boundary, and should start at a sync code.
	// If EOF is encountered before any bytes were read, then this returns null.
	// Otherwise this reads 6 to 16 bytes from the given input stream and tries
	// to parse it as a FLAC frame header - starting from the sync code, and ending
	// after the CRC-8 value is read (but before reading any subframes).
	// If any field is found to be invalid then a DataFormatException is thrown.
	// After the frame header is successfully decoded, a new FrameMetadata with
	// all fields (except frameSize) set to appropriate values is returned.
	// (This doesn't read to the end of the frame, so the frameSize field is set to -1.)
	public static FrameMetadata readFrame(BitInputStream in) throws IOException {
		// Preliminaries
		in.resetCrcs();
		int temp = in.readByte();
		if (temp == -1)
			return null;
		FrameMetadata result = new FrameMetadata();
		result.frameSize = -1;
		
		// Read sync bits
		int sync = temp << 6 | in.readUint(6);  // Uint14
		if (sync != 0x3FFE)
			throw new DataFormatException("Sync code expected");
		
		// Read various simple fields
		if (in.readUint(1) != 0)
			throw new DataFormatException("Reserved bit");
		int blockStrategy  = in.readUint(1);
		int blockSizeCode  = in.readUint(4);
		int sampleRateCode = in.readUint(4);
		int chanAsgn       = in.readUint(4);
		result.channelAssignment = chanAsgn;
		if (chanAsgn < 8)
			result.numChannels = chanAsgn + 1;
		else if (8 <= chanAsgn && chanAsgn <= 10)
			result.numChannels = 2;
		else
			throw new DataFormatException("Reserved channel assignment");
		result.sampleDepth = decodeSampleDepth(in.readUint(3));
		if (in.readUint(1) != 0)
			throw new DataFormatException("Reserved bit");
		
		// Read and check the frame/sample position field
		long position = readUtf8Integer(in);  // Reads 1 to 7 bytes
		if (blockStrategy == 0) {
			if ((position >>> 31) != 0)
				throw new DataFormatException("Frame index too large");
			result.frameIndex = (int)position;
			result.sampleOffset = -1;
		} else if (blockStrategy == 1) {
			result.sampleOffset = position;
			result.frameIndex = -1;
		} else
			throw new AssertionError();
		
		// Read variable-length data for some fields
		result.blockSize = decodeBlockSize(blockSizeCode, in);  // Reads 0 to 2 bytes
		result.sampleRate = decodeSampleRate(sampleRateCode, in);  // Reads 0 to 2 bytes
		int computedCrc8 = in.getCrc8();
		if (in.readUint(8) != computedCrc8)
			throw new DataFormatException("CRC-8 mismatch");
		return result;
	}
	
	
	// Reads 1 to 7 bytes from the input stream. Return value is a uint36.
	// See: https://hydrogenaud.io/index.php/topic,112831.msg929128.html#msg929128
	private static long readUtf8Integer(BitInputStream in) throws IOException {
		int head = in.readUint(8);
		int n = Integer.numberOfLeadingZeros(~(head << 24));  // Number of leading 1s in the byte
		assert 0 <= n && n <= 8;
		if (n == 0)
			return head;
		else if (n == 1 || n == 8)
			throw new DataFormatException("Invalid UTF-8 coded number");
		else {
			long result = head & (0x7F >>> n);
			for (int i = 0; i < n - 1; i++) {
				int temp = in.readUint(8);
				if ((temp & 0xC0) != 0x80)
					throw new DataFormatException("Invalid UTF-8 coded number");
				result = (result << 6) | (temp & 0x3F);
			}
			if ((result >>> 36) != 0)
				throw new AssertionError();
			return result;
		}
	}
	
	
	// Argument is a uint4 value. Reads 0 to 2 bytes from the input stream.
	// Return value is in the range [1, 65536].
	private static int decodeBlockSize(int code, BitInputStream in) throws IOException {
		if ((code >>> 4) != 0)
			throw new IllegalArgumentException();
		switch (code) {
			case 0:  throw new DataFormatException("Reserved block size");
			case 6:  return in.readUint(8) + 1;
			case 7:  return in.readUint(16) + 1;
			default:
				int result = searchSecond(BLOCK_SIZE_CODES, code);
				if (result < 1 || result > 65536)
					throw new AssertionError();
				return result;
		}
	}
	
	
	// Argument is a uint4 value. Reads 0 to 2 bytes from the input stream.
	// Return value is in the range [-1, 655350].
	private static int decodeSampleRate(int code, BitInputStream in) throws IOException {
		if ((code >>> 4) != 0)
			throw new IllegalArgumentException();
		switch (code) {
			case  0:  return -1;  // Caller should obtain value from stream info metadata block
			case 12:  return in.readUint(8);
			case 13:  return in.readUint(16);
			case 14:  return in.readUint(16) * 10;
			case 15:  throw new DataFormatException("Invalid sample rate");
			default:
				int result = searchSecond(SAMPLE_RATE_CODES, code);
				if (result < 1 || result > 655350)
					throw new AssertionError();
				return result;
		}
	}
	
	
	// Argument is a uint3 value. Pure function and performs no I/O. Return value is in the range [-1, 24].
	private static int decodeSampleDepth(int code) {
		if ((code >>> 3) != 0)
			throw new IllegalArgumentException();
		else if (code == 0)
			return -1;  // Caller should obtain value from stream info metadata block
		else {
			int result = searchSecond(SAMPLE_DEPTH_CODES, code);
			if (result == -1)
				throw new DataFormatException("Reserved bit depth");
			if (result < 1 || result > 32)
				throw new AssertionError();
			return result;
		}
	}
	
	
	
	public void writeHeader(BitOutputStream out) throws IOException {
		out.resetCrcs();
		out.writeInt(14, 0x3FFE);  // Sync
		out.writeInt(1, 0);  // Reserved
		out.writeInt(1, 1);  // Blocking strategy
		
		int blockSizeCode = getBlockSizeCode(blockSize);
		out.writeInt(4, blockSizeCode);
		int sampleRateCode = getSampleRateCode(sampleRate);
		out.writeInt(4, sampleRateCode);
		
		out.writeInt(4, channelAssignment);
		out.writeInt(3, getSampleDepthCode(sampleDepth));
		out.writeInt(1, 0);  // Reserved
		
		// Variable-length: 1 to 7 bytes
		if (frameIndex != -1 && sampleOffset == -1)
			writeUtf8Integer(sampleOffset, out);
		else if (sampleOffset != -1 && frameIndex == -1)
			writeUtf8Integer(sampleOffset, out);
		else
			throw new IllegalStateException();
		
		// Variable-length: 0 to 2 bytes
		if (blockSizeCode == 6)
			out.writeInt(8, blockSize - 1);
		else if (blockSizeCode == 7)
			out.writeInt(16, blockSize - 1);
		
		// Variable-length: 0 to 2 bytes
		if (sampleRateCode == 12)
			out.writeInt(8, sampleRate);
		else if (sampleRateCode == 13)
			out.writeInt(16, sampleRate);
		else if (sampleRateCode == 14)
			out.writeInt(16, sampleRate / 10);
		
		out.writeInt(8, out.getCrc8());
	}
	
	
	// Given a uint36 value, this writes 1 to 7 whole bytes to the given output stream.
	private static void writeUtf8Integer(long val, BitOutputStream out) throws IOException {
		if (val < 0 || val >= (1L << 36))
			throw new IllegalArgumentException();
		int bitLen = 64 - Long.numberOfLeadingZeros(val);
		if (bitLen <= 7)
			out.writeInt(8, (int)val);
		else {
			int n = (bitLen - 2) / 5;
			out.writeInt(8, (0xFF80 >>> n) | (int)(val >>> (n * 6)));
			for (int i = n - 1; i >= 0; i--)
				out.writeInt(8, 0x80 | ((int)(val >>> (i * 6)) & 0x3F));
		}
	}
	
	
	// Returns a uint4 value representing the given block size. Pure function.
	private static int getBlockSizeCode(int blockSize) {
		int result = searchFirst(BLOCK_SIZE_CODES, blockSize);
		if (result != -1);  // Already done
		else if (1 <= blockSize && blockSize <= 256)
			result = 6;
		else if (1 <= blockSize && blockSize <= 65536)
			result = 7;
		else  // blockSize < 1 || blockSize > 65536
			throw new IllegalArgumentException();
		
		if ((result >>> 4) != 0)
			throw new AssertionError();
		return result;
	}
	
	
	// Returns a uint4 value representing the given sample rate. Pure function.
	private static int getSampleRateCode(int sampleRate) {
		if (sampleRate <= 0)
			throw new IllegalArgumentException();
		int result = searchFirst(SAMPLE_RATE_CODES, sampleRate);
		if (result != -1);  // Already done
		else if (0 <= sampleRate && sampleRate < 256)
			result = 12;
		else if (0 <= sampleRate && sampleRate < 65536)
			result = 13;
		else if (0 <= sampleRate && sampleRate < 655360 && sampleRate % 10 == 0)
			result = 14;
		else
			result = 0;
		
		if ((result >>> 4) != 0)
			throw new AssertionError();
		return result;
	}
	
	
	// Returns a uint3 value representing the given sample depth. Pure function.
	private static int getSampleDepthCode(int sampleDepth) {
		int result = searchFirst(SAMPLE_DEPTH_CODES, sampleDepth);
		if (result == -1)
			result = 0;
		if ((result >>> 3) != 0)
			throw new AssertionError();
		return result;
	}
	
	
	
	/*---- Tables of constants and search functions ----*/
	
	private static final int searchFirst(int[][] table, int key) {
		for (int[] pair : table) {
			if (pair[0] == key)
				return pair[1];
		}
		return -1;
	}
	
	
	private static final int searchSecond(int[][] table, int key) {
		for (int[] pair : table) {
			if (pair[1] == key)
				return pair[0];
		}
		return -1;
	}
	
	
	private static final int[][] BLOCK_SIZE_CODES = {
		{  192,  1},
		{  576,  2},
		{ 1152,  3},
		{ 2304,  4},
		{ 4608,  5},
		{  256,  8},
		{  512,  9},
		{ 1024, 10},
		{ 2048, 11},
		{ 4096, 12},
		{ 8192, 13},
		{16384, 14},
		{32768, 15},
	};
	
	
	private static final int[][] SAMPLE_DEPTH_CODES = {
		{ 8, 1},
		{12, 2},
		{16, 4},
		{20, 5},
		{24, 6},
	};
	
	
	private static final int[][] SAMPLE_RATE_CODES = {
		{ 88200,  1},
		{176400,  2},
		{192000,  3},
		{  8000,  4},
		{ 16000,  5},
		{ 22050,  6},
		{ 24000,  7},
		{ 32000,  8},
		{ 44100,  9},
		{ 48000, 10},
		{ 96000, 11},
	};
	
}
