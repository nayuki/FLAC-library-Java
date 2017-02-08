/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac;


// A mutable structure holding key pieces of information from decoding a frame header.
public final class FrameMetadata {
	
	/*---- Fields ----*/
	
	// Exactly one of these following two fields equals -1.
	public int frameIndex;     // Either -1 or a uint31.
	public long sampleOffset;  // Either -1 or a uint36.
	
	public int numChannels;  // In the range [1, 8].
	public int numSamples;   // For this frame/block (not the whole stream), in the range [1, 65536].
	public int sampleRate;   // Either -1 if not encoded in the frame, or in the range [1, 655350].
	public int sampleDepth;  // Either -1 if not encoded in the frame, or in the range [8, 24].
	
}
