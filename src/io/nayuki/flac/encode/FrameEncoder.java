/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.encode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


final class FrameEncoder {
	
	/*---- Static functions ----*/
	
	public static SizeEstimate<FrameEncoder> computeBest(int sampleOffset, long[][] data, int sampleDepth, int sampleRate) {
		FrameEncoder enc = new FrameEncoder(sampleOffset, data, sampleDepth, sampleRate);
		int numChannels = data.length;
		@SuppressWarnings("unchecked")
		SizeEstimate<SubframeEncoder>[] encoderInfo = new SizeEstimate[numChannels];
		if (numChannels != 2) {
			enc.channelAssignment = numChannels - 1;
			for (int i = 0; i < encoderInfo.length; i++)
				encoderInfo[i] = SubframeEncoder.computeBest(data[i], sampleDepth);
		} else {  // Explore the 4 stereo encoding modes
			long[] left  = data[0];
			long[] right = data[1];
			long[] mid  = new long[data[0].length];
			long[] side = new long[mid.length];
			for (int i = 0; i < mid.length; i++) {
				mid[i] = (left[i] + right[i]) >> 1;
				side[i] = left[i] - right[i];
			}
			SizeEstimate<SubframeEncoder> leftInfo  = SubframeEncoder.computeBest(left , sampleDepth);
			SizeEstimate<SubframeEncoder> rightInfo = SubframeEncoder.computeBest(right, sampleDepth);
			SizeEstimate<SubframeEncoder> midInfo   = SubframeEncoder.computeBest(mid  , sampleDepth);
			SizeEstimate<SubframeEncoder> sideInfo  = SubframeEncoder.computeBest(side , sampleDepth + 1);
			long mode1Size = leftInfo.sizeEstimate + rightInfo.sizeEstimate;
			long mode8Size = leftInfo.sizeEstimate + sideInfo.sizeEstimate;
			long mode9Size = rightInfo.sizeEstimate + sideInfo.sizeEstimate;
			long mode10Size = midInfo.sizeEstimate + sideInfo.sizeEstimate;
			long minimum = Math.min(Math.min(mode1Size, mode8Size), Math.min(mode9Size, mode10Size));
			if (mode1Size == minimum) {
				enc.channelAssignment = 1;
				encoderInfo[0] = leftInfo;
				encoderInfo[1] = rightInfo;
			} else if (mode8Size == minimum) {
				enc.channelAssignment = 8;
				encoderInfo[0] = leftInfo;
				encoderInfo[1] = sideInfo;
			} else if (mode9Size == minimum) {
				enc.channelAssignment = 9;
				encoderInfo[0] = sideInfo;
				encoderInfo[1] = rightInfo;
			} else if (mode10Size == minimum) {
				enc.channelAssignment = 10;
				encoderInfo[0] = midInfo;
				encoderInfo[1] = sideInfo;
			} else
				throw new AssertionError();
		}
		
		// Add up subframe sizes
		long size = 0;
		enc.subEncoders = new SubframeEncoder[encoderInfo.length];
		for (int i = 0; i < enc.subEncoders.length; i++) {
			size += encoderInfo[i].sizeEstimate;
			enc.subEncoders[i] = encoderInfo[i].encoder;
		}
		
		// Count length of header (always in whole bytes)
		try {
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			try (BitOutputStream bitout = new BitOutputStream(bout)) {
				enc.encodeHeader(data, bitout);
			}
			bout.close();
			size += bout.toByteArray().length * 8;
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		
		// Count padding and footer
		size = (size + 7) / 8 * 8;  // Round up to nearest byte
		size += 16;  // CRC-16
		return new SizeEstimate<>(size, enc);
	}
	
	
	
	/*---- Fields ----*/
	
	private final int sampleOffset;
	private final int sampleDepth;
	private final int sampleRate;
	public final int blockSize;
	private int channelAssignment;
	private SubframeEncoder[] subEncoders;
	
	
	
	/*---- Constructors ----*/
	
	public FrameEncoder(int sampleOffset, long[][] data, int sampleDepth, int sampleRate) {
		// Set fields
		this.sampleOffset = sampleOffset;
		this.sampleDepth = sampleDepth;
		this.sampleRate = sampleRate;
		this.blockSize = data[0].length;
		channelAssignment = data.length - 1;
	}
	
	
	
	/*---- Public methods ----*/
	
	public void encode(long[][] data, BitOutputStream out) throws IOException {
		// Check arguments
		Objects.requireNonNull(data);
		Objects.requireNonNull(out);
		if (data[0].length != blockSize)
			throw new IllegalArgumentException();
		
		encodeHeader(data, out);
		if (0 <= channelAssignment && channelAssignment <= 7) {
			for (int i = 0; i < data.length; i++)
				subEncoders[i].encode(data[i], out);
		} else if (8 <= channelAssignment || channelAssignment <= 10) {
			long[] left  = data[0];
			long[] right = data[1];
			long[] mid  = new long[blockSize];
			long[] side = new long[blockSize];
			for (int i = 0; i < blockSize; i++) {
				mid[i] = (left[i] + right[i]) >> 1;
				side[i] = left[i] - right[i];
			}
			if (channelAssignment == 8) {
				subEncoders[0].encode(left, out);
				subEncoders[1].encode(side, out);
			} else if (channelAssignment == 9) {
				subEncoders[0].encode(side, out);
				subEncoders[1].encode(right, out);
			} else if (channelAssignment == 10) {
				subEncoders[0].encode(mid, out);
				subEncoders[1].encode(side, out);
			} else
				throw new AssertionError();
		} else
			throw new AssertionError();
		out.alignToByte();
		out.writeInt(16, out.getCrc16());
	}
	
	
	
	/*---- Private I/O methods ----*/
	
	private void encodeHeader(long[][] data, BitOutputStream out) throws IOException {
		out.resetCrcs();
		out.writeInt(14, 0x3FFE);  // Sync
		out.writeInt(1, 0);  // Reserved
		out.writeInt(1, 1);  // Blocking strategy
		
		int blockSizeCode = getBlockSizeCode(blockSize);
		out.writeInt(4, blockSizeCode);
		int sampleRateCode = getSampleRateCode(sampleRate);
		out.writeInt(4, sampleRateCode);
		
		out.writeInt(4, channelAssignment);
		out.writeInt(3, SAMPLE_DEPTH_CODES.get(sampleDepth));
		out.writeInt(1, 0);  // Reserved
		
		// Variable-length: 1 to 7 bytes
		writeUtf8Integer(sampleOffset, out);  // Sample position
		
		// Variable-length: 0/8/16 bits
		if (blockSizeCode == 6)
			out.writeInt(8, blockSize - 1);
		else if (blockSizeCode == 7)
			out.writeInt(16, blockSize - 1);
		
		// Variable-length: 0/8/16 bits
		if (sampleRateCode == 12)
			out.writeInt(8, sampleRate);
		else if (sampleRateCode == 13)
			out.writeInt(16, sampleRate);
		else if (sampleRateCode == 14)
			out.writeInt(16, sampleRate / 10);
		
		out.writeInt(8, out.getCrc8());
	}
	
	
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
	
	
	
	/*---- Private helper integer pure functions ----*/
	
	private static int getBlockSizeCode(int blockSize) {
		int result;  // Uint4
		if (BLOCK_SIZE_CODES.containsKey(blockSize))
			result = BLOCK_SIZE_CODES.get(blockSize);
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
	
	
	private static int getSampleRateCode(int sampleRate) {
		int result;  // Uint4
		if (SAMPLE_RATE_CODES.containsKey(sampleRate))
			result = SAMPLE_RATE_CODES.get(sampleRate);
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
	
	
	
	/*---- Tables of constants ----*/
	
	private static final Map<Integer,Integer> BLOCK_SIZE_CODES = new HashMap<>();
	static {
		BLOCK_SIZE_CODES.put(  192,  1);
		BLOCK_SIZE_CODES.put(  576,  2);
		BLOCK_SIZE_CODES.put( 1152,  3);
		BLOCK_SIZE_CODES.put( 2304,  4);
		BLOCK_SIZE_CODES.put( 4608,  5);
		BLOCK_SIZE_CODES.put(  256,  8);
		BLOCK_SIZE_CODES.put(  512,  9);
		BLOCK_SIZE_CODES.put( 1024, 10);
		BLOCK_SIZE_CODES.put( 2048, 11);
		BLOCK_SIZE_CODES.put( 4096, 12);
		BLOCK_SIZE_CODES.put( 8192, 13);
		BLOCK_SIZE_CODES.put(16384, 14);
		BLOCK_SIZE_CODES.put(32768, 15);
	}
	
	
	private static final Map<Integer,Integer> SAMPLE_DEPTH_CODES = new HashMap<>();
	static {
		for (int i = 1; i <= 32; i++)
			SAMPLE_DEPTH_CODES.put(i, 0);
		SAMPLE_DEPTH_CODES.put( 8, 1);
		SAMPLE_DEPTH_CODES.put(12, 2);
		SAMPLE_DEPTH_CODES.put(16, 4);
		SAMPLE_DEPTH_CODES.put(20, 5);
		SAMPLE_DEPTH_CODES.put(24, 6);
	}
	
	
	private static final Map<Integer,Integer> SAMPLE_RATE_CODES = new HashMap<>();
	static {
		SAMPLE_RATE_CODES.put( 88200,  1);
		SAMPLE_RATE_CODES.put(176400,  2);
		SAMPLE_RATE_CODES.put(192000,  3);
		SAMPLE_RATE_CODES.put(  8000,  4);
		SAMPLE_RATE_CODES.put( 16000,  5);
		SAMPLE_RATE_CODES.put( 22050,  6);
		SAMPLE_RATE_CODES.put( 24000,  7);
		SAMPLE_RATE_CODES.put( 32000,  8);
		SAMPLE_RATE_CODES.put( 44100,  9);
		SAMPLE_RATE_CODES.put( 48000, 10);
		SAMPLE_RATE_CODES.put( 96000, 11);
	}
	
}
