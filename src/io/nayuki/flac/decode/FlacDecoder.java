/* 
 * FLAC library (Java)
 * 
 * Copyright (c) Project Nayuki
 * https://www.nayuki.io/page/flac-library-java
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program (see COPYING.txt and COPYING.LESSER.txt).
 * If not, see <http://www.gnu.org/licenses/>.
 */

package io.nayuki.flac.decode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;
import io.nayuki.flac.common.FrameMetadata;
import io.nayuki.flac.common.StreamInfo;


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
			streamInfo.checkFrame(meta);
			if (meta.frameIndex != -1 && meta.frameIndex != i)
				throw new DataFormatException("Frame index mismatch");
			if (meta.sampleOffset != -1 && meta.sampleOffset != sampleOffset)
				throw new DataFormatException("Sample offset mismatch");
			sampleOffset += meta.blockSize;
		}
		
		// Check audio data against hash
		if (Arrays.equals(streamInfo.md5Hash, new byte[16]))
			hashCheck = 0;
		else if (Arrays.equals(StreamInfo.getMd5Hash(samples, streamInfo.sampleDepth), streamInfo.md5Hash))
			hashCheck = 1;
		else
			hashCheck = 2;  // Hash check failed!
	}
	
	
	
	/*---- Private methods ----*/
	
	private boolean handleMetadataBlock() throws IOException {
		boolean last = in.readUint(1) != 0;
		int type = in.readUint(7);
		int length = in.readUint(24);
		byte[] data = new byte[length];
		in.readFully(data);
		if (type == 0) {  // Stream info block
			if (streamInfo != null)
				throw new DataFormatException("Duplicate stream info block");
			streamInfo = new StreamInfo(data);
			samples = new int[streamInfo.numChannels][(int)streamInfo.numSamples];
		}
		return !last;
	}
	
}
