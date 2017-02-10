/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.decode;


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
	
	// Always 16 bytes long. Can be all zeros to signify
	// that the encoder did not compute the MD5 hash.
	public byte[] md5Hash = new byte[16];
	
}
