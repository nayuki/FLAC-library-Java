/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.common;

import java.io.IOException;
import io.nayuki.flac.decode.BitInputStream;
import io.nayuki.flac.decode.DataFormatException;
import io.nayuki.flac.decode.FrameMetadata;
import io.nayuki.flac.encode.BitOutputStream;


// A mutable structure holding all fields of the stream info metadata block.
public final class StreamInfo {
	
	/*---- Fields ----*/
	
	public int minBlockSize;  // In samples per channel, a uint16 value.
	public int maxBlockSize;  // In samples per channel, a uint16 value.
	public int minFrameSize;  // In bytes, a uint24 value. 0 signifies unknown.
	public int maxFrameSize;  // In bytes, a uint24 value. 0 signifies unknown.
	
	public int sampleRate;   // In hertz (Hz), a uint20 value. 0 is invalid.
	public int numChannels;  // An integer in the range [1, 8].
	public int sampleDepth;  // In bits per sample, in the range [4, 32].
	public long numSamples;  // Total number of samples (per channel) in the audio clip. 0 signifies unknown.
	
	// Always 16 bytes long. Can be all zeros to signify that the encoder did not
	// compute the MD5 hash. It is okay to replace this array with a different object.
	public byte[] md5Hash;
	
	
	
	/*---- Constructors ----*/
	
	// Constructs a blank stream info structure with certain default values.
	public StreamInfo() {
		// Set these fields to legal unknown values
		minFrameSize = 0;
		maxFrameSize = 0;
		numSamples = 0;
		md5Hash = new byte[16];
		
		// Set these fields to invalid (not reserved) values
		minBlockSize = 0;
		maxBlockSize = 0;
		sampleRate = 0;
	}
	
	
	// Constructs a stream info structure by reading 34 bytes from the given input
	// stream of a FLAC file. This throws DataFormatException if values are invalid.
	public StreamInfo(BitInputStream in) throws IOException {
		this();
		minBlockSize = in.readUint(16);
		maxBlockSize = in.readUint(16);
		minFrameSize = in.readUint(24);
		maxFrameSize = in.readUint(24);
		if (minBlockSize < 16)
			throw new DataFormatException("Minimum block size less than 16");
		if (maxBlockSize > 65535)
			throw new DataFormatException("Maximum block size greater than 65535");
		if (maxBlockSize < minBlockSize)
			throw new DataFormatException("Maximum block size less than minimum block size");
		if (minFrameSize != 0 && maxFrameSize != 0 && maxFrameSize < minFrameSize)
			throw new DataFormatException("Maximum frame size less than minimum frame size");
		sampleRate = in.readUint(20);
		if (sampleRate == 0 || sampleRate > 655350)
			throw new DataFormatException("Invalid sample rate");
		numChannels = in.readUint(3) + 1;
		sampleDepth = in.readUint(5) + 1;
		numSamples = (long)in.readUint(18) << 18 | in.readUint(18);
		in.readFully(md5Hash);
	}
	
	
	
	/*---- Methods ----*/
	
	// Checks whether the given frame metadata is consistent with this stream info object.
	// This method either returns silently or throws an exception.
	public void checkFrame(FrameMetadata meta) {
		if (meta.numChannels != numChannels)
			throw new DataFormatException("Channel count mismatch");
		if (meta.sampleRate != -1 && meta.sampleRate != sampleRate)
			throw new DataFormatException("Sample rate mismatch");
		if (meta.sampleDepth != -1 && meta.sampleDepth != sampleDepth)
			throw new DataFormatException("Sample depth mismatch");
		if (numSamples != 0 && meta.blockSize > numSamples)
			throw new DataFormatException("Block size exceeds total number of samples");
		
		if (meta.blockSize > maxBlockSize)
			throw new DataFormatException("Block size exceeds maximum");
		// Note: If minBlockSize == maxBlockSize, then the final block
		// in the stream is allowed to be smaller than minBlockSize
		
		if (minFrameSize != 0 && meta.frameSize < minFrameSize)
			throw new DataFormatException("Frame size less than minimum");
		if (maxFrameSize != 0 && meta.frameSize > maxFrameSize)
			throw new DataFormatException("Frame size exceeds maximum");
	}
	
	
	// Writes this stream info metadata block to the given output stream, including the
	// metadata block header. This writes exactly 38 bytes. The output stream should
	// initially be aligned to a byte boundary, and will finish at a byte boundary.
	public void write(boolean last, BitOutputStream out) throws IOException {
		// Metadata block header
		out.writeInt(1, last ? 1 : 0);
		out.writeInt(7, 0);  // Type
		out.writeInt(24, 34);  // Length
		
		// Stream info block fields
		out.writeInt(16, minBlockSize);
		out.writeInt(16, maxBlockSize);
		out.writeInt(24, minFrameSize);
		out.writeInt(24, maxFrameSize);
		out.writeInt(20, sampleRate);
		out.writeInt(3, numChannels - 1);
		out.writeInt(5, sampleDepth - 1);
		out.writeInt(18, (int)(numSamples >>> 18));
		out.writeInt(18, (int)(numSamples >>>  0));
		for (byte b : md5Hash)
			out.writeInt(8, b);
	}
	
}
