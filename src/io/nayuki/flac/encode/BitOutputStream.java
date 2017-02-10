/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.encode;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;


/* 
 * A bit-oriented output stream, with other methods tailored for FLAC usage (such as CRC calculation).
 */
public final class BitOutputStream implements AutoCloseable {
	
	/*---- Fields ----*/
	
	private OutputStream out;  // The underlying byte-based output stream to write to.
	private long bitBuffer;  // Only the bottom bitBufferLen bits are valid; the top bits are garbage.
	private int bitBufferLen;  // Always in the range [0, 64].
	
	// Current state of the CRC calculations.
	private int crc8;  // Always a uint8 value.
	private int crc16;  // Always a uint16 value.
	
	
	
	/*---- Constructors ----*/
	
	public BitOutputStream(OutputStream out) throws IOException {
		Objects.requireNonNull(out);
		this.out = out;
		bitBuffer = 0;
		bitBufferLen = 0;
		resetCrcs();
	}
	
	
	
	/*---- Methods ----*/
	
	/*-- Bit position --*/
	
	// Writes between 0 to 7 zero bits, to align the current bit position to a byte boundary.
	public void alignToByte() throws IOException {
		writeInt((64 - bitBufferLen) % 8, 0);
	}
	
	
	// Either returns silently or throws an exception.
	private void checkByteAligned() {
		if (bitBufferLen % 8 != 0)
			throw new IllegalStateException("Not at a byte boundary");
	}
	
	
	/*-- Writing bitwise integers --*/
	
	// Writes the lowest n bits of the given value to this bit output stream.
	// This doesn't care whether val represents a signed or unsigned integer.
	public void writeInt(int n, int val) throws IOException {
		if (n < 0 || n > 32)
			throw new IllegalArgumentException();
		
		if (bitBufferLen + n > 64) {
			flush();
			assert bitBufferLen + n <= 64;
		}
		bitBuffer <<= n;
		bitBuffer |= val & ((1L << n) - 1);
		bitBufferLen += n;
		assert 0 <= bitBufferLen && bitBufferLen <= 64;
	}
	
	
	// Writes out whole bytes from the bit buffer to the underlying stream. After this is done,
	// only 0 to 7 bits remain in the bit buffer. Also updates the CRCs on each byte written.
	public void flush() throws IOException {
		while (bitBufferLen >= 8) {
			bitBufferLen -= 8;
			int b = (int)(bitBuffer >>> bitBufferLen) & 0xFF;
			out.write(b);
			crc8 ^= b;
			crc16 ^= b << 8;
			for (int i = 0; i < 8; i++) {
				crc8 <<= 1;
				crc16 <<= 1;
				crc8 ^= (crc8 >>> 8) * 0x107;
				crc16 ^= (crc16 >>> 16) * 0x18005;
				assert (crc8 >>> 8) == 0;
				assert (crc16 >>> 16) == 0;
			}
		}
		assert 0 <= bitBufferLen && bitBufferLen <= 64;
	}
	
	
	/*-- CRC calculations --*/
	
	// Marks the current position (which must be byte-aligned) as the start of both CRC calculations.
	public void resetCrcs() throws IOException {
		flush();
		crc8 = 0;
		crc16 = 0;
	}
	
	
	// Returns the CRC-8 hash of all the bytes written since the last call to resetCrcs()
	// (or from the beginning of stream if reset was never called).
	public int getCrc8() throws IOException {
		checkByteAligned();
		flush();
		if ((crc8 >>> 8) != 0)
			throw new AssertionError();
		return crc8;
	}
	
	
	// Returns the CRC-16 hash of all the bytes written since the last call to resetCrcs()
	// (or from the beginning of stream if reset was never called).
	public int getCrc16() throws IOException {
		checkByteAligned();
		flush();
		if ((crc16 >>> 16) != 0)
			throw new AssertionError();
		return crc16;
	}
	
	
	/*-- Miscellaneous --*/
	
	// Writes out any internally buffered bit data, closes the underlying output stream,
	// and invalidates this bit output stream object for any future operation.
	public void close() throws IOException {
		checkByteAligned();
		flush();
		out.close();
		out = null;
	}
	
}
