/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.decode;

import java.io.IOException;


// A mutable structure holding key pieces of information from decoding a frame header.
public final class FrameMetadata {
	
	/*---- Fields ----*/
	
	// Exactly one of these following two fields equals -1.
	public int frameIndex;     // Either -1 or a uint31.
	public long sampleOffset;  // Either -1 or a uint36.
	
	public int numChannels;  // In the range [1, 8].
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
			default:  return SAMPLE_RATES[code];  // 1 <= code <= 11
		}
	}
	
	private static final int[] SAMPLE_RATES = {-1, 88200, 176400, 192000, 8000, 16000, 22050, 24000, 32000, 44100, 48000, 96000};
	
	
	// Argument is a uint3 value. Pure function and performs no I/O. Return value is in the range [-1, 24].
	private static int decodeSampleDepth(int code) {
		if ((code >>> 3) != 0)
			throw new IllegalArgumentException();
		else if (code == 0)
			return -1;  // Caller should obtain value from stream info metadata block
		else if (SAMPLE_DEPTHS[code] < 0)
			throw new DataFormatException("Reserved bit depth");
		else
			return SAMPLE_DEPTHS[code];
	}
	
	private static final int[] SAMPLE_DEPTHS = {-1, 8, 12, -2, 16, 20, 24, -2};
	
}
