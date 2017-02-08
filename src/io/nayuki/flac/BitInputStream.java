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
	private byte[] byteBuffer;
	private int byteBufferLen;
	private int byteBufferIndex;
	private int crcStartIndex;
	private long bitBuffer;
	private int bitBufferLen;
	private int crc8;
	private int crc16;
	
	
	
	/*---- Constructors ----*/
	
	public BitInputStream(InputStream in) {
		Objects.requireNonNull(in);
		this.in = in;
		byteBuffer = new byte[4096];
		byteBufferLen = 0;
		byteBufferIndex = 0;
		crcStartIndex = 0;
		bitBuffer = 0;
		bitBufferLen = 0;
		crc8  = 0;
		crc16 = 0;
	}
	
	
	
	/*---- Methods ----*/
	
	// Reads the next given number of bits as an unsigned integer (i.e. zero-extended to int32).
	// Note that when n = 32, the result will be always a signed integer.
	public int readUint(int n) throws IOException {
		if (n < 0 || n > 32)
			throw new IllegalArgumentException();
		while (bitBufferLen < n) {
			int b = readUnderlying();
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
	
	
	// Reads the next given number of bits as an signed integer (i.e. sign-extended to int32).
	public int readSignedInt(int n) throws IOException {
		int shift = 32 - n;
		return (readUint(n) << shift) >> shift;
	}
	
	
	public int readRiceSignedInt(int param) throws IOException {
		if (bitBufferLen < RICE_DECODING_TABLE_BITS)
			fillBitBuffer();
		int result = RICE_DECODING_TABLE[(int)(bitBuffer >>> (bitBufferLen - RICE_DECODING_TABLE_BITS)) & RICE_DECODING_TABLE_MASK];
		bitBufferLen -= result;
		if (result == RICE_DECODING_TABLE_BITS) {
			do {
				if (bitBufferLen == 0)
					fillBitBuffer();
				int temp = Long.numberOfLeadingZeros(~(~bitBuffer << (64 - bitBufferLen)));
				result += temp;
				bitBufferLen -= temp;
			} while (bitBufferLen == 0);
		}
		assert (bitBuffer >>> (bitBufferLen - 1)) == 1;
		bitBufferLen--;
		if (param > 0) {
			result <<= param;
			while (bitBufferLen < param) {
				int b = readUnderlying();
				if (b == -1)
					throw new EOFException();
				bitBuffer = (bitBuffer << 8) | b;
				bitBufferLen += 8;
			}
			result |= (bitBuffer << (64 - bitBufferLen)) >>> (64 - param);
			bitBufferLen -= param;
		}
		return (result >>> 1) ^ (-(result & 1));
	}
	
	
	// Discards any partial bits, then reads the given array fully or throws EOFEOxception.
	public void readFully(byte[] b) throws IOException {
		Objects.requireNonNull(b);
		bitBufferLen &= ~7;  // Align to byte (discards between 0 to 7 bits)
		for (int i = 0; i < b.length; i++)
			b[i] = (byte)readUint(8);
	}
	
	
	// Discards any partial bits, then either returns an unsigned byte value or -1 for EOF.
	public int readByte() throws IOException {
		bitBufferLen &= ~7;  // Align to byte (discards between 0 to 7 bits)
		if (bitBufferLen >= 8) {
			int result = (int)(bitBuffer >>> (bitBufferLen - 8)) & 0xFF;
			bitBufferLen -= 8;
			return result;
		} else {
			bitBufferLen = 0;
			return readUnderlying();
		}
	}
	
	
	public int getBitPosition() {
		return (-bitBufferLen) & 7;
	}
	
	
	public int getCrc8() {
		if (bitBufferLen % 8 != 0)
			throw new IllegalStateException();
		updateCrcs(bitBufferLen / 8);
		if ((crc8 >>> 8) != 0)
			throw new AssertionError();
		return crc8;
	}
	
	
	public int getCrc16() {
		if (bitBufferLen % 8 != 0)
			throw new IllegalStateException();
		updateCrcs(bitBufferLen / 8);
		if ((crc16 >>> 16) != 0)
			throw new AssertionError();
		return crc16;
	}
	
	
	private void updateCrcs(int delta) {
		int end = byteBufferIndex - delta;
		for (int i = crcStartIndex; i < end; i++) {
			int b = byteBuffer[i] & 0xFF;
			crc8 = CRC8_TABLE[crc8 ^ b] & 0xFF;
			crc16 = CRC16_TABLE[crc16 >>> 8 ^ b] ^ ((crc16 & 0xFF) << 8);
			assert (crc8 >>> 8) == 0;
			assert (crc16 >>> 16) == 0;
		}
		crcStartIndex = end;
	}
	
	
	public void resetCrcs() {
		if (bitBufferLen % 8 != 0)
			throw new IllegalStateException();
		crcStartIndex = byteBufferIndex - bitBufferLen / 8;
		crc8 = 0;
		crc16 = 0;
	}
	
	
	public void close() throws IOException {
		in.close();
		in = null;
		bitBuffer = 0;
		bitBufferLen = 0;
	}
	
	
	private void fillBitBuffer() throws IOException {
		int i = byteBufferIndex;
		int n = Math.min((64 - bitBufferLen) >>> 3, byteBufferLen - i);
		byte[] b = byteBuffer;
		if (n == 8) {
			bitBuffer = ((long)(b[i] << 24 | (b[i + 1] & 0xFF) << 16 | (b[i + 2] & 0xFF) << 8 | (b[i + 3] & 0xFF)) << 32)
				| ((b[i + 4] << 24 | (b[i + 5] & 0xFF) << 16 | (b[i + 6] & 0xFF) << 8 | (b[i + 7] & 0xFF)) & 0xFFFFFFFFL);
			bitBufferLen = 64;
		} else if (n > 0) {
			for (int j = 0; j < n; j++, i++)
				bitBuffer = (bitBuffer << 8) | (b[i] & 0xFF);
			bitBufferLen += n << 3;
		} else if (bitBufferLen <= 56) {
			int temp = readUnderlying();
			if (temp == -1)
				throw new EOFException();
			bitBuffer = (bitBuffer << 8) | temp;
			bitBufferLen += 8;
		}
		assert bitBufferLen >= 8;
		byteBufferIndex += n;
	}
	
	
	private int readUnderlying() throws IOException {
		if (byteBufferIndex >= byteBufferLen) {
			if (byteBufferLen == -1)
				return -1;
			updateCrcs(0);
			byteBufferLen = in.read(byteBuffer);
			crcStartIndex = 0;
			if (byteBufferLen <= 0)
				return -1;
			byteBufferIndex = 0;
		}
		assert byteBufferIndex < byteBufferLen;
		int temp = byteBuffer[byteBufferIndex] & 0xFF;
		byteBufferIndex++;
		return temp;
	}
	
	
	
	/*---- Tables of constants ----*/
	
	private static final int RICE_DECODING_TABLE_BITS = 8;  // Must be between 1 to 8 (inclusive)
	private static final int RICE_DECODING_TABLE_MASK = (1 << RICE_DECODING_TABLE_BITS) - 1;
	private static final byte[] RICE_DECODING_TABLE = new byte[1 << RICE_DECODING_TABLE_BITS];  // Number of leading 0 bits in the byte value
	
	static {
		for (int i = 0; i < RICE_DECODING_TABLE.length; i++)
			RICE_DECODING_TABLE[i] = (byte)(Integer.numberOfLeadingZeros(i) - (32 - RICE_DECODING_TABLE_BITS));
	}
	
	
	private static byte[] CRC8_TABLE  = new byte[256];
	private static char[] CRC16_TABLE = new char[256];
	
	static {
		for (int i = 0; i < CRC8_TABLE.length; i++) {
			int temp8 = i;
			int temp16 = i << 8;
			for (int j = 0; j < 8; j++) {
				temp8 = (temp8 << 1) ^ ((temp8 >>> 7) * 0x107);
				temp16 = (temp16 << 1) ^ ((temp16 >>> 15) * 0x18005);
			}
			CRC8_TABLE[i] = (byte)temp8;
			CRC16_TABLE[i] = (char)temp16;
		}
	}
	
}
