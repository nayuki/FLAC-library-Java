/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.encode;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;


public final class BitOutputStream implements AutoCloseable {
	
	/*---- Fields ----*/
	
	private OutputStream out;
	private long bitBuffer;
	private int bitBufferLen;
	private int crc8;
	private int crc16;
	
	
	
	/*---- Constructors ----*/
	
	public BitOutputStream(OutputStream out) throws IOException {
		Objects.requireNonNull(out);
		this.out = out;
		bitBuffer = 0;
		bitBufferLen = 0;
		resetCrcs();
	}
	
	
	
	/*---- Methods ----*/
	
	// Writes the lowest n bits of the given value to this bit output stream.
	public void writeInt(int n, int val) throws IOException {
		if (n < 0 || n > 32)
			throw new IllegalArgumentException();
		
		if (bitBufferLen + n > 64)
			flushBuffer();
		bitBuffer <<= n;
		bitBuffer |= val & ((1L << n) - 1);
		bitBufferLen += n;
	}
	
	
	// Writes between 0 to 7 zero bits to align the current bit position to a byte boundary.
	public void alignToByte() throws IOException {
		writeInt((64 - bitBufferLen) % 8, 0);
	}
	
	
	public int getCrc8() throws IOException {
		if (bitBufferLen % 8 != 0)
			throw new IllegalStateException();
		flushBuffer();
		if ((crc8 >>> 8) != 0)
			throw new AssertionError();
		return crc8;
	}
	
	
	public int getCrc16() throws IOException {
		if (bitBufferLen % 8 != 0)
			throw new IllegalStateException();
		flushBuffer();
		if ((crc16 >>> 16) != 0)
			throw new AssertionError();
		return crc16;
	}
	
	
	public void resetCrcs() throws IOException {
		flushBuffer();
		crc8 = 0;
		crc16 = 0;
	}
	
	
	public void close() throws IOException {
		alignToByte();
		flushBuffer();
		out.close();
		out = null;
	}
	
	
	private void flushBuffer() throws IOException {
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
			}
		}
	}
	
}
