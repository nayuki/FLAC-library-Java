/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;


final class BitInputStream implements AutoCloseable {
	
	/*---- Fields ----*/
	
	private InputStream in;
	private long bitBuffer;
	private int bitBufferLen;
	
	
	
	/*---- Constructors ----*/
	
	public BitInputStream(InputStream in) {
		Objects.requireNonNull(in);
		this.in = in;
		bitBuffer = 0;
		bitBufferLen = 0;
	}
	
	
	
	/*---- Methods ----*/
	
	public int readInt(int n) throws IOException {
		if (n < 0 || n > 32)
			throw new IllegalArgumentException();
		while (bitBufferLen < n) {
			int b = in.read();
			if (b == -1)
				throw new EOFException();
			bitBuffer = (bitBuffer << 8) | b;
			bitBufferLen += 8;
		}
		int result = (int)(bitBuffer >>> (bitBufferLen - n));
		if (n != 32)
			result &= (1 << n) - 1;
		bitBufferLen -= n;
		return result;
	}
	
	
	public int readSignedInt(int n) throws IOException {
		int shift = 32 - n;
		return (readInt(n) << shift) >> shift;
	}
	
	
	public void readFully(byte[] b) throws IOException {
		Objects.requireNonNull(b);
		alignToByte();
		int i = 0;
		for (; bitBufferLen >= 8 && i < b.length; i++) {
			b[i] = (byte)(bitBuffer >>> (bitBufferLen - 8));
			bitBufferLen -= 8;
		}
		while (i < b.length) {
			int n = in.read(b, i, b.length - i);
			if (n == -1)
				throw new EOFException();
			i += n;
		}
	}
	
	
	public int readByte() throws IOException {
		alignToByte();
		if (bitBufferLen >= 8) {
			int result = (int)(bitBuffer >>> (bitBufferLen - 8)) & 0xFF;
			bitBufferLen -= 8;
			return result;
		} else {
			bitBufferLen = 0;
			return in.read();
		}
	}
	
	
	public void alignToByte() {
		bitBufferLen &= ~7;
	}
	
	
	public void close() throws IOException {
		in.close();
	}
	
}
