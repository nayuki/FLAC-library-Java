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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Objects;
import io.nayuki.flac.common.FrameMetadata;
import io.nayuki.flac.common.SeekTable;
import io.nayuki.flac.common.StreamInfo;


public final class FlacDecoder implements AutoCloseable {
	
	/*---- Fields ----*/
	
	public StreamInfo streamInfo;
	public SeekTable seekTable;
	
	private RandomAccessFileInputStream fileInput;
	private BitInputStream bitInput;
	
	private long metadataEndPos;
	
	private FrameDecoder frameDec;
	
	
	
	/*---- Constructors ----*/
	
	// Constructs a new FLAC decoder to read the given file.
	// This immediately reads the basic header but not metadata blocks.
	public FlacDecoder(File file) throws IOException {
		// Initialize streams
		Objects.requireNonNull(file);
		fileInput = new RandomAccessFileInputStream(new RandomAccessFile(file, "r"));
		bitInput = new BitInputStream(fileInput);
		
		// Read basic header
		if (bitInput.readUint(32) != 0x664C6143)  // Magic string "fLaC"
			throw new DataFormatException("Invalid magic string");
		metadataEndPos = -1;
	}
	
	
	
	/*---- Methods ----*/
	
	// Reads, handles, and returns the next metadata block. Returns a pair (int type, byte[] data) if the
	// next metadata block exists, otherwise returns null if the final metadata block was previously read.
	// In addition to reading and returning data, this method also updates the internal state
	// of this object to reflect the new data seen, and throws exceptions for situations such as
	// not starting with a stream info metadata block or encountering duplicates of certain blocks.
	public Object[] readAndHandleMetadataBlock() throws IOException {
		if (metadataEndPos != -1)
			return null;  // All metadata already consumed
		
		// Read entire block
		boolean last = bitInput.readUint(1) != 0;
		int type = bitInput.readUint(7);
		int length = bitInput.readUint(24);
		byte[] data = new byte[length];
		bitInput.readFully(data);
		
		// Handle recognized block
		if (type == 0) {
			if (streamInfo != null)
				throw new DataFormatException("Duplicate stream info metadata block");
			streamInfo = new StreamInfo(data);
		} else {
			if (streamInfo == null)
				throw new DataFormatException("Expected stream info metadata block");
			if (type == 3) {
				if (seekTable != null)
					throw new DataFormatException("Duplicate seek table metadata block");
				seekTable = new SeekTable(data);
			}
		}
		
		if (last) {
			metadataEndPos = bitInput.getByteCount();
			frameDec = new FrameDecoder(bitInput);
		}
		return new Object[]{type, data};
	}
	
	
	// Reads and decodes the next block of audio samples into the given buffer,
	// returning the number of samples in the block. The return value is 0 if the read
	// started at the end of stream, or a number in the range [1, 65536] for a valid block.
	// All metadata blocks must be read before starting to read audio blocks.
	public int readAudioBlock(int[][] samples, int off) throws IOException {
		if (frameDec == null)
			throw new IllegalStateException("Metadata blocks not fully consumed yet");
		FrameMetadata frame = frameDec.readFrame(samples, off);
		if (frame == null)
			return 0;
		else
			return frame.blockSize;  // In the range [1, 65536]
	}
	
	
	public int seekAndReadAudioBlock(long pos, int[][] samples, int off) throws IOException {
		if (frameDec == null)
			throw new IllegalStateException("Metadata blocks not fully consumed yet");
		
		long[] sampleAndFilePos = getBestSeekPoint(pos);
		fileInput.seek(sampleAndFilePos[1] + metadataEndPos);
		bitInput = new BitInputStream(fileInput);
		frameDec = new FrameDecoder(bitInput);
		
		long curPos = sampleAndFilePos[0];
		int[][] smpl = new int[streamInfo.numChannels][65536];
		while (true) {
			FrameMetadata frame = frameDec.readFrame(smpl, 0);
			if (frame == null)
				return 0;
			long nextPos = curPos + frame.blockSize;
			if (nextPos > pos) {
				for (int ch = 0; ch < smpl.length; ch++)
					System.arraycopy(smpl[ch], (int)(pos - curPos), samples[ch], off, (int)(nextPos - pos));
				return (int)(nextPos - pos);
			}
			curPos = nextPos;
		}
	}
	
	
	private long[] getBestSeekPoint(long pos) {
		long samplePos = 0;
		long filePos = 0;
		if (seekTable != null) {
			for (SeekTable.SeekPoint p : seekTable.points) {
				if (p.sampleOffset <= pos) {
					samplePos = p.sampleOffset;
					filePos = p.fileOffset;
				} else
					break;
			}
		}
		return new long[]{samplePos, filePos};
	}
	
	
	// Closes the underlying input streams and discards object data.
	// This decoder object becomes invalid for any method calls or field usages.
	public void close() throws IOException {
		if (bitInput != null) {
			streamInfo = null;
			seekTable = null;
			frameDec = null;
			bitInput.close();
			fileInput.close();
			bitInput = null;
			fileInput = null;
		}
	}
	
}
