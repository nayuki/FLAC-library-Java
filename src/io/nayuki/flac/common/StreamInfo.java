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

package io.nayuki.flac.common;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import io.nayuki.flac.decode.ByteArrayFlacInput;
import io.nayuki.flac.decode.DataFormatException;
import io.nayuki.flac.decode.FlacDecoder;
import io.nayuki.flac.decode.FlacLowLevelInput;
import io.nayuki.flac.encode.BitOutputStream;


/**
 * Represents precisely all the fields of a stream info metadata block. Mutable structure,
 * not thread-safe. Also has methods for parsing and serializing this structure to/from bytes.
 * @see FrameInfo
 * @see FlacDecoder
 */
public final class StreamInfo {
	
	/*---- Fields ----*/
	
	public int minBlockSize;  // In samples per channel, a uint16 value.
	public int maxBlockSize;  // In samples per channel, a uint16 value.
	public int minFrameSize;  // In bytes, a uint24 value. 0 signifies unknown.
	public int maxFrameSize;  // In bytes, a uint24 value. 0 signifies unknown.
	
	public int sampleRate;   // In hertz (Hz), a uint20 value. 0 is invalid.
	public int numChannels;  // An integer in the range [1, 8].
	public int sampleDepth;  // In bits per sample, in the range [4, 32].
	public long numSamples;  // Total number of samples (per channel) in the audio clip, a uint36 value. 0 signifies unknown (cannot have empty audio).
	
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
	
	
	// Constructs a stream info structure by reading the given 34-byte array of raw data.
	// This throws DataFormatException if values are invalid.
	public StreamInfo(byte[] b) {
		try {
			FlacLowLevelInput in = new ByteArrayFlacInput(b);
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
			numSamples = (long)in.readUint(18) << 18 | in.readUint(18);  // uint36
			md5Hash = new byte[16];
			in.readFully(md5Hash);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}
	
	
	
	/*---- Methods ----*/
	
	// Either returns silently or throws IllegalStateException or NullPointerException.
	public void checkValues() {
		if ((minBlockSize >>> 16) != 0)
			throw new IllegalStateException("Invalid minimum block size");
		if ((maxBlockSize >>> 16) != 0)
			throw new IllegalStateException("Invalid maximum block size");
		if ((minFrameSize >>> 24) != 0)
			throw new IllegalStateException("Invalid minimum frame size");
		if ((maxFrameSize >>> 24) != 0)
			throw new IllegalStateException("Invalid maximum frame size");
		if (sampleRate == 0 || (sampleRate >>> 20) != 0)
			throw new IllegalStateException("Invalid sample rate");
		if (numChannels < 1 || numChannels > 8)
			throw new IllegalStateException("Invalid number of channels");
		if (sampleDepth < 4 || sampleDepth > 32)
			throw new IllegalStateException("Invalid sample depth");
		if ((numSamples >>> 36) != 0)
			throw new IllegalStateException("Invalid number of samples");
		Objects.requireNonNull(md5Hash);
		if (md5Hash.length != 16)
			throw new IllegalStateException("Invalid MD5 hash length");
	}
	
	
	// Checks whether the given frame metadata is consistent with this stream info object.
	// This method either returns silently or throws an exception.
	public void checkFrame(FrameInfo meta) {
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
		// Abort if anything is wrong
		checkValues();
		
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
	
	
	
	/*---- Static functions ----*/
	
	// Returns the MD5 hash of the given raw audio sample data at the given bit depth.
	// The bit depth must be a multiple of 8 from 8 to 32. The returned array is a new object of length 16.
	public static byte[] getMd5Hash(int[][] samples, int depth) {
		// Check arguments
		Objects.requireNonNull(samples);
		if (depth < 0 || depth > 32 || depth % 8 != 0)
			throw new IllegalArgumentException();
		
		MessageDigest hasher;
		try {
			hasher = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError(e);
		}
		int numChannels = samples.length;
		int numSamples = samples[0].length;
		int numBytes = depth / 8;
		byte[] buf = new byte[numChannels * numBytes * Math.min(numSamples, 2048)];
		for (int i = 0, l = 0; i < numSamples; i++) {
			for (int j = 0; j < numChannels; j++) {
				int val = samples[j][i];
				for (int k = 0; k < numBytes; k++, l++)
					buf[l] = (byte)(val >>> (k << 3));
			}
			if (l == buf.length || i == numSamples - 1) {
				hasher.update(buf, 0, l);
				l = 0;
			}
		}
		return hasher.digest();
	}
	
}
