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
	
	// The underlying byte-based input stream to read from.
	private InputStream in;
	
	// Data from the underlying stream is first stored into this byte buffer before further processing.
	private byte[] byteBuffer;
	private int byteBufferLen;
	private int byteBufferIndex;
	
	// The buffer of next bits to return to a reader. Note that byteBufferIndex is incremented when byte
	// values are put into the bit buffer, but they might not have been consumed by the ultimate reader yet.
	private long bitBuffer;  // Only the bottom bitBufferLen bits are valid; the top bits are garbage.
	private int bitBufferLen;  // Always in the range [0, 64].
	
	// Current state of the CRC calculations.
	private int crc8;  // Always a uint8 value.
	private int crc16;  // Always a uint16 value.
	private int crcStartIndex;  // In the range [0, byteBufferLen], unless byteBufferLen = -1.
	
	
	
	/*---- Constructors ----*/
	
	// Constructs a FLAC-oriented bit input stream from the given byte-based input stream.
	public BitInputStream(InputStream in) {
		Objects.requireNonNull(in);
		this.in = in;
		
		byteBuffer = new byte[4096];
		byteBufferLen = 0;
		byteBufferIndex = 0;
		
		bitBuffer = 0;
		bitBufferLen = 0;
		
		crc8  = 0;
		crc16 = 0;
		crcStartIndex = 0;
	}
	
	
	
	/*---- Methods ----*/
	
	/*-- Bit position --*/
	
	// Returns the number of bits in the current byte that have been consumed.
	// This starts at 0, increments for each bit consumed, topping out at 7, then wraps around and repeats.
	public int getBitPosition() {
		return (-bitBufferLen) & 7;
	}
	
	
	// Either returns silently or throws an exception.
	private void checkByteAligned() {
		if (bitBufferLen % 8 != 0)
			throw new IllegalStateException("Not at a byte boundary");
	}
	
	
	/*-- Reading bitwise integers --*/
	
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
			assert 0 <= bitBufferLen && bitBufferLen <= 64;
		}
		int result = (int)(bitBuffer >>> (bitBufferLen - n));
		if (n != 32) {
			result &= (1 << n) - 1;
			assert (result >>> n) == 0;
		}
		bitBufferLen -= n;
		assert 0 <= bitBufferLen && bitBufferLen <= 64;
		return result;
	}
	
	
	// Reads the next given number of bits as an signed integer (i.e. sign-extended to int32).
	public int readSignedInt(int n) throws IOException {
		int shift = 32 - n;
		return (readUint(n) << shift) >> shift;
	}
	
	
	// Reads and decodes the next batch of Rice-coded signed integers. Note that any Rice-coded integer might read a large
	// number of bits from the underlying stream (but not in practice because it would be a very inefficient encoding).
	public void readRiceSignedInts(int param, long[] result, int start, int end) throws IOException {
		byte[] consumeTable = RICE_DECODING_CONSUMED_TABLES[param];
		int[] valueTable = RICE_DECODING_VALUE_TABLES[param];
		while (true) {
			middle:
			while (start <= end - RICE_DECODING_CHUNK) {
				if (bitBufferLen < RICE_DECODING_CHUNK * RICE_DECODING_TABLE_BITS) {
					if (byteBufferIndex <= byteBufferLen - 8) {
						fillBitBuffer();
					} else
						break;
				}
				for (int i = 0; i < RICE_DECODING_CHUNK; i++, start++) {
					// Fast decoder
					int extractedBits = (int)(bitBuffer >>> (bitBufferLen - RICE_DECODING_TABLE_BITS)) & RICE_DECODING_TABLE_MASK;
					int consumed = consumeTable[extractedBits];
					if (consumed == 0)
						break middle;
					bitBufferLen -= consumed;
					result[start] = valueTable[extractedBits];
				}
			}
			
			// Slow decoder
			if (start >= end)
				break;
			int val = 0;
			while (readUint(1) == 0)
				val++;
			val = (val << param) | readUint(param);
			result[start] = (val >>> 1) ^ (-(val & 1));
			start++;
		}
	}
	
	
	// Appends at least 8 bits to the bit buffer, or throws EOFException.
	private void fillBitBuffer() throws IOException {
		int i = byteBufferIndex;
		int n = Math.min((64 - bitBufferLen) >>> 3, byteBufferLen - i);
		byte[] b = byteBuffer;
		if (n > 0) {
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
		assert 8 <= bitBufferLen && bitBufferLen <= 64;
		byteBufferIndex += n;
	}
	
	
	/*-- Reading bytes --*/
	
	// Discards any partial bits, then either returns an unsigned byte value or -1 for EOF.
	public int readByte() throws IOException {
		checkByteAligned();
		if (bitBufferLen >= 8)
			return readUint(8);
		else {
			assert bitBufferLen == 0;
			return readUnderlying();
		}
	}
	
	
	// Discards any partial bits, then reads the given array fully or throws EOFEOxception.
	public void readFully(byte[] b) throws IOException {
		Objects.requireNonNull(b);
		checkByteAligned();
		for (int i = 0; i < b.length; i++)
			b[i] = (byte)readUint(8);
	}
	
	
	// Reads a byte from the byte buffer (if available) or from the underlying stream, returning either a uint8 or -1.
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
	
	
	/*-- CRC calculations --*/
	
	// Marks the current position (which must be byte-aligned) as the start of both CRC calculations.
	public void resetCrcs() {
		checkByteAligned();
		crcStartIndex = byteBufferIndex - bitBufferLen / 8;
		crc8 = 0;
		crc16 = 0;
	}
	
	
	// Returns the CRC-8 hash of all the data seen since the last call to resetCrcs()
	// (or from the beginning of stream if reset was never called).
	public int getCrc8() {
		checkByteAligned();
		updateCrcs(bitBufferLen / 8);
		if ((crc8 >>> 8) != 0)
			throw new AssertionError();
		return crc8;
	}
	
	
	// Returns the CRC-16 hash of all the data seen since the last call to resetCrcs()
	// (or from the beginning of stream if reset was never called).
	public int getCrc16() {
		checkByteAligned();
		updateCrcs(bitBufferLen / 8);
		if ((crc16 >>> 16) != 0)
			throw new AssertionError();
		return crc16;
	}
	
	
	// Updates the two CRC values with data in byteBuffer[crcStartIndex : byteBufferIndex - unusedTrailingBytes].
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
	
	
	/*-- Miscellaneous --*/
	
	// Discards all buffers and closes the underlying input stream.
	public void close() throws IOException {
		in.close();
		in = null;
		byteBuffer = null;
		byteBufferLen = -1;
		byteBufferIndex = -1;
		bitBuffer = 0;
		bitBufferLen = -1;
		crc8 = -1;
		crc16 = -1;
		crcStartIndex = -1;
	}
	
	
	
	/*---- Tables of constants ----*/
	
	// For rice decoding
	
	private static final int RICE_DECODING_TABLE_BITS = 13;  // Configurable, must be positive
	private static final int RICE_DECODING_TABLE_MASK = (1 << RICE_DECODING_TABLE_BITS) - 1;
	private static final byte[][] RICE_DECODING_CONSUMED_TABLES = new byte[31][1 << RICE_DECODING_TABLE_BITS];
	private static final int[][] RICE_DECODING_VALUE_TABLES = new int[31][1 << RICE_DECODING_TABLE_BITS];
	private static final int RICE_DECODING_CHUNK = 4;  // Configurable, must be positive, and RICE_DECODING_CHUNK * RICE_DECODING_TABLE_BITS <= 64
	
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
	
	
	// For CRC calculations
	
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
