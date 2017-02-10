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
	
	public int sampleRate;
	public int sampleDepth;
	public int numChannels;
	public int[][] samples;
	public int hashCheck;  // 0 = skipped because hash in file was all zeros, 1 = hash check passed, 2 = hash mismatch
	
	private BitInputStream in;
	private boolean steamInfoSeen;
	private byte[] md5Hash;
	
	
	
	/*---- Constructors ----*/
	
	// Constructs a FLAC decoder from the given input stream, and immediately
	// performs full decoding of the data until the end of stream is reached.
	public FlacDecoder(InputStream in) throws IOException {
		// Initialize some fields
		Objects.requireNonNull(in);
		this.in = new BitInputStream(in);
		steamInfoSeen = false;
		md5Hash = new byte[16];
		
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
			checkFrame(meta, i, sampleOffset);
			sampleOffset += meta.numSamples;
		}
		
		// Check audio data against hash
		if (Arrays.equals(md5Hash, new byte[16]))
			hashCheck = 0;
		else if (Arrays.equals(Md5Hasher.getHash(samples, sampleDepth), md5Hash))
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
		if (steamInfoSeen)
			throw new DataFormatException("Duplicate stream info block");
		steamInfoSeen = true;
		int minBlockSamples = in.readUint(16);
		int maxBlockSamples = in.readUint(16);
		int minFrameBytes = in.readUint(24);
		int maxFrameBytes = in.readUint(24);
		if (minBlockSamples < 16)
			throw new DataFormatException("Minimum block size less than 16");
		if (maxBlockSamples > 65535)
			throw new DataFormatException("Maximum block size greater than 65535");
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
		in.readFully(md5Hash);
	}
	
	
	// Examines the values in the given frame metadata to check if they match the other arguments
	// and the current object state, either returning silently or throwing an exception.
	private void checkFrame(FrameMetadata meta, int frameIndex, long sampleOffset) {
		if (meta.numChannels != this.numChannels)
			throw new DataFormatException("Channel count mismatch");
		if (meta.sampleRate != -1 && meta.sampleRate != this.sampleRate)
			throw new DataFormatException("Sample rate mismatch");
		if (meta.sampleDepth != -1 && meta.sampleDepth != this.sampleDepth)
			throw new DataFormatException("Sample depth mismatch");
		if (meta.frameIndex != -1 && meta.frameIndex != frameIndex)
			throw new DataFormatException("Frame index mismatch");
		if (meta.sampleOffset != -1 && meta.sampleOffset != sampleOffset)
			throw new DataFormatException("Sample offset mismatch");
	}
	
}
