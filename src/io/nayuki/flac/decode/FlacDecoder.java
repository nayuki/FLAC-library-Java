/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.decode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;
import io.nayuki.flac.common.Md5Hasher;


public final class FlacDecoder {
	
	/*---- Fields ----*/
	
	public int[][] samples;
	public int hashCheck;  // 0 = skipped because hash in file was all zeros, 1 = hash check passed, 2 = hash mismatch
	
	private BitInputStream in;
	
	public StreamInfo streamInfo;
	
	
	
	/*---- Constructors ----*/
	
	// Constructs a FLAC decoder from the given input stream, and immediately
	// performs full decoding of the data until the end of stream is reached.
	public FlacDecoder(InputStream in) throws IOException {
		// Initialize some fields
		Objects.requireNonNull(in);
		this.in = new BitInputStream(in);
		streamInfo = null;
		
		// Parse header blocks
		if (this.in.readUint(32) != 0x664C6143)  // Magic string "fLaC"
			throw new DataFormatException("Invalid magic string");
		while (handleMetadataBlock());
		
		// Decode frames until end of stream
		FrameDecoder dec = new FrameDecoder(this.in);
		for (int i = 0, sampleOffset = 0; ; i++) {
			FrameMetadata meta = dec.readFrame(samples, sampleOffset);
			if (meta == null)
				break;
			if (streamInfo.minFrameSize != 0 && meta.frameSize < streamInfo.minFrameSize)
				throw new DataFormatException("Frame size smaller than indicated minimum");
			if (streamInfo.maxFrameSize != 0 && meta.frameSize > streamInfo.maxFrameSize)
				throw new DataFormatException("Frame size smaller than indicated maximum");
			checkFrame(meta, i, sampleOffset);
			sampleOffset += meta.blockSize;
		}
		
		// Check audio data against hash
		if (Arrays.equals(streamInfo.md5Hash, new byte[16]))
			hashCheck = 0;
		else if (Arrays.equals(Md5Hasher.getHash(samples, streamInfo.sampleDepth), streamInfo.md5Hash))
			hashCheck = 1;
		else
			hashCheck = 2;  // Hash check failed!
	}
	
	
	
	/*---- Private methods ----*/
	
	private boolean handleMetadataBlock() throws IOException {
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
	
	
	private void parseStreamInfoData() throws IOException {
		if (streamInfo != null)
			throw new DataFormatException("Duplicate stream info block");
		streamInfo = new StreamInfo();
		streamInfo.minBlockSize = in.readUint(16);
		streamInfo.maxBlockSize = in.readUint(16);
		streamInfo.minFrameSize = in.readUint(24);
		streamInfo.maxFrameSize = in.readUint(24);
		if (streamInfo.minBlockSize < 16)
			throw new DataFormatException("Minimum block size less than 16");
		if (streamInfo.maxBlockSize > 65535)
			throw new DataFormatException("Maximum block size greater than 65535");
		if (streamInfo.maxBlockSize < streamInfo.minBlockSize)
			throw new DataFormatException("Maximum block size less than minimum block size");
		if (streamInfo.minFrameSize != 0 && streamInfo.maxFrameSize != 0 && streamInfo.maxFrameSize < streamInfo.minFrameSize)
			throw new DataFormatException("Maximum frame size less than minimum frame size");
		streamInfo.sampleRate = in.readUint(20);
		if (streamInfo.sampleRate == 0 || streamInfo.sampleRate > 655350)
			throw new DataFormatException("Invalid sample rate");
		streamInfo.numChannels = in.readUint(3) + 1;
		streamInfo.sampleDepth = in.readUint(5) + 1;
		streamInfo.numSamples = (long)in.readUint(18) << 18 | in.readUint(18);
		samples = new int[streamInfo.numChannels][(int)streamInfo.numSamples];
		in.readFully(streamInfo.md5Hash);
	}
	
	
	// Examines the values in the given frame metadata to check if they match the other arguments
	// and the current object state, either returning silently or throwing an exception.
	private void checkFrame(FrameMetadata meta, int frameIndex, long sampleOffset) {
		if (meta.numChannels != streamInfo.numChannels)
			throw new DataFormatException("Channel count mismatch");
		if (meta.sampleRate != -1 && meta.sampleRate != streamInfo.sampleRate)
			throw new DataFormatException("Sample rate mismatch");
		if (meta.sampleDepth != -1 && meta.sampleDepth != streamInfo.sampleDepth)
			throw new DataFormatException("Sample depth mismatch");
		if (meta.frameIndex != -1 && meta.frameIndex != frameIndex)
			throw new DataFormatException("Frame index mismatch");
		if (meta.sampleOffset != -1 && meta.sampleOffset != sampleOffset)
			throw new DataFormatException("Sample offset mismatch");
		if (meta.blockSize > streamInfo.maxBlockSize)
			throw new DataFormatException("Block size exceeds maximum");
		// Note: If minBlockSize == maxBlockSize, then the final block
		// in the stream is allowed to be smaller than minBlockSize
	}
	
}
