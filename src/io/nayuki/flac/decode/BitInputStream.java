/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.decode;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;


public final class BitInputStream implements AutoCloseable {
	
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
	
	// Constructs a FLAC-oriented bit inputstream from the given byte-based input stream.
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
	
	
	// Reads and decodes the next Rice-coded signed integer. This might read a large number of bits
	// from the underlying stream (but not in practice because it would be a very inefficient encoding).
	public int readRiceSignedInt(int param) throws IOException {
		while (true) {  // Simulate goto
			// Check or fill enough bits in the buffer
			if (bitBufferLen < RICE_DECODING_TABLE_BITS) {
				if (((byteBufferLen - byteBufferIndex) << 3) >= RICE_DECODING_TABLE_BITS) {
					fillBitBuffer();
					assert bitBufferLen >= RICE_DECODING_TABLE_BITS;
				} else
					break;
			}
			
			// Fast decoder
			int extractedBits = (int)(bitBuffer >>> (bitBufferLen - RICE_DECODING_TABLE_BITS)) & RICE_DECODING_TABLE_MASK;
			int consumed = RICE_DECODING_CONSUMED_TABLES[param][extractedBits];
			if (consumed == 0)
				break;
			bitBufferLen -= consumed;
			return RICE_DECODING_VALUE_TABLES[param][extractedBits];
		}
		
		// Slow decoder
		int result = 0;
		while (readUint(1) == 0)
			result++;
		result = (result << param) | readUint(param);
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
		if (bitBufferLen >= 8)
			return readUint(8);
		else {
			bitBufferLen = 0;
			return readUnderlying();
		}
	}
	
	
	public int getBitPosition() {
		return (-bitBufferLen) & 7;
	}
	
	
	// Returns the CRC-8 hash of all the data seen since the last call to resetCrcs()
	// (or from the beginning of stream if reset was never called).
	public int getCrc8() {
		if (bitBufferLen % 8 != 0)
			throw new IllegalStateException("Not at a byte boundary");
		updateCrcs(bitBufferLen / 8);
		if ((crc8 >>> 8) != 0)
			throw new AssertionError();
		return crc8;
	}
	
	
	// Returns the CRC-16 hash of all the data seen since the last call to resetCrcs()
	// (or from the beginning of stream if reset was never called).
	public int getCrc16() {
		if (bitBufferLen % 8 != 0)
			throw new IllegalStateException("Not at a byte boundary");
		updateCrcs(bitBufferLen / 8);
		if ((crc16 >>> 16) != 0)
			throw new AssertionError();
		return crc16;
	}
	
	
	private void updateCrcs(int unusedTrailingBytes) {
		int end = byteBufferIndex - unusedTrailingBytes;
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
	
	
	// Discards all buffers and closes the underlying input stream.
	public void close() throws IOException {
		in.close();
		in = null;
		byteBuffer = null;
		byteBufferLen = 0;
		byteBufferIndex = 0;
		bitBuffer = 0;
		bitBufferLen = 0;
	}
	
	
	// Appends at least 8 bits to the bit buffer, or throws EOFException.
	private void fillBitBuffer() throws IOException {
		int i = byteBufferIndex;
		int n = Math.min((64 - bitBufferLen) >>> 3, byteBufferLen - i);
		byte[] b = byteBuffer;
		if (n == 6) {
			bitBuffer <<= 48;
			bitBuffer |= ((long)((b[i] & 0xFF) << 8 | (b[i + 1] & 0xFF)) << 32)
				| ((b[i + 2] << 24 | (b[i + 3] & 0xFF) << 16 | (b[i + 4] & 0xFF) << 8 | (b[i + 5] & 0xFF)) & 0xFFFFFFFFL);
			bitBufferLen += 48;
		} else if (n == 7) {
			bitBuffer <<= 56;
			bitBuffer |= ((long)((b[i] & 0xFF) << 16 | (b[i + 1] & 0xFF) << 8 | (b[i + 2] & 0xFF)) << 32)
				| ((b[i + 3] << 24 | (b[i + 4] & 0xFF) << 16 | (b[i + 5] & 0xFF) << 8 | (b[i + 6] & 0xFF)) & 0xFFFFFFFFL);
			bitBufferLen += 56;
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
	
	private static final int RICE_DECODING_TABLE_BITS = 13;  // Must be positive
	private static final int RICE_DECODING_TABLE_MASK = (1 << RICE_DECODING_TABLE_BITS) - 1;
	private static final byte[][] RICE_DECODING_CONSUMED_TABLES = new byte[31][1 << RICE_DECODING_TABLE_BITS];
	private static final int[][] RICE_DECODING_VALUE_TABLES = new int[31][1 << RICE_DECODING_TABLE_BITS];
	
	static {
		for (int param = 0; param < RICE_DECODING_CONSUMED_TABLES.length; param++) {
			byte[] consumed = RICE_DECODING_CONSUMED_TABLES[param];
			int[] values = RICE_DECODING_VALUE_TABLES[param];
			for (int i = 0; ; i++) {
				int numBits = (i >>> param) + 1 + param;
				if (numBits > RICE_DECODING_TABLE_BITS)
					break;
				int bits = ((1 << param) | (i & ((1 << param) - 1)));
				int shift = RICE_DECODING_TABLE_BITS - numBits;
				for (int j = 0; j < (1 << shift); j++) {
					consumed[(bits << shift) | j] = (byte)numBits;
					values[(bits << shift) | j] = (i >>> 1) ^ (-(i & 1));
				}
			}
			if (consumed[0] != 0)
				throw new AssertionError();
		}
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
