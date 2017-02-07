/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac;

import java.util.Objects;


final class Md5Hasher {
	
	/*---- Static functions ----*/
	
	public static byte[] getHash(int[][] samples, int depth) {
		Objects.requireNonNull(samples);
		if (depth < 0 || depth > 32 || depth % 8 != 0)
			throw new IllegalArgumentException();
		
		Md5Hasher hasher = new Md5Hasher();
		int numBytes = depth / 8;
		for (int i = 0; i < samples[0].length; i++) {
			for (int j = 0; j < samples.length; j++) {
				int val = samples[j][i];
				for (int k = 0; k < numBytes; k++)
					hasher.update((byte)(val >>> (k << 3)));
			}
		}
		return hasher.getHash();
	}
	
	
	
	/*---- Fields ----*/
	
	private int[] state;
	private byte[] block;
	private int blockFilled;
	private long length;
	
	
	
	/*---- Constructors ----*/
	
	public Md5Hasher() {
		state = new int[]{0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476};
		length = 0;
		block = new byte[64];
		blockFilled = 0;
	}
	
	
	
	/*---- Methods ----*/
	
	public void update(byte b) {
		block[blockFilled] = b;
		blockFilled++;
		length++;
		if (blockFilled == block.length) {
			compressBlock();
			blockFilled = 0;
		}
	}
	
	
	public byte[] getHash() {
		long bitLen = length * 8;
		update((byte)0x80);
		while (blockFilled + 8 != block.length)
			update((byte)0);
		for (int i = 0; i < 8; i++)
			update((byte)(bitLen >>> (i * 8)));
		byte[] result = new byte[state.length * 4];
		for (int i = 0; i < result.length; i++)
			result[i] = (byte)(state[i / 4] >>> (i % 4 * 8));
		state = null;
		block = null;
		blockFilled = 0;
		length = 0;
		return result;
	}
	
	
	private void compressBlock() {
		int[] schedule = new int[16];
		for (int i = 0; i < block.length; i++)
			schedule[i / 4] |= (block[i] & 0xFF) << (i % 4 * 8);
		
		int a = state[0];
		int b = state[1];
		int c = state[2];
		int d = state[3];
		for (int j = 0; j < 64; j++) {
			int f, k;
			if      ( 0 <= j && j < 16) { f = (b & c) | (~b & d);  k = j;                }
			else if (16 <= j && j < 32) { f = (d & b) | (~d & c);  k = (5 * j + 1) % 16; }
			else if (32 <= j && j < 48) { f = b ^ c ^ d;           k = (3 * j + 5) % 16; }
			else if (48 <= j && j < 64) { f = c ^ (b | (~d));      k = 7 * j % 16;       }
			else throw new AssertionError();
			
			int temp = a + f + schedule[k] + RCON[j];
			a = d;
			d = c;
			c = b;
			b += Integer.rotateLeft(temp, ROT[j / 16 * 4 + j % 4]);
		}
		state[0] += a;
		state[1] += b;
		state[2] += c;
		state[3] += d;
	}
	
	
	/*---- Tables of constants ----*/
	
	private static final int[] RCON = {
		0xD76AA478, 0xE8C7B756, 0x242070DB, 0xC1BDCEEE,
		0xF57C0FAF, 0x4787C62A, 0xA8304613, 0xFD469501,
		0x698098D8, 0x8B44F7AF, 0xFFFF5BB1, 0x895CD7BE,
		0x6B901122, 0xFD987193, 0xA679438E, 0x49B40821,
		0xF61E2562, 0xC040B340, 0x265E5A51, 0xE9B6C7AA,
		0xD62F105D, 0x02441453, 0xD8A1E681, 0xE7D3FBC8,
		0x21E1CDE6, 0xC33707D6, 0xF4D50D87, 0x455A14ED,
		0xA9E3E905, 0xFCEFA3F8, 0x676F02D9, 0x8D2A4C8A,
		0xFFFA3942, 0x8771F681, 0x6D9D6122, 0xFDE5380C,
		0xA4BEEA44, 0x4BDECFA9, 0xF6BB4B60, 0xBEBFBC70,
		0x289B7EC6, 0xEAA127FA, 0xD4EF3085, 0x04881D05,
		0xD9D4D039, 0xE6DB99E5, 0x1FA27CF8, 0xC4AC5665,
		0xF4292244, 0x432AFF97, 0xAB9423A7, 0xFC93A039,
		0x655B59C3, 0x8F0CCC92, 0xFFEFF47D, 0x85845DD1,
		0x6FA87E4F, 0xFE2CE6E0, 0xA3014314, 0x4E0811A1,
		0xF7537E82, 0xBD3AF235, 0x2AD7D2BB, 0xEB86D391,
	};
	
	
	private static final int[] ROT = {
		 7, 12, 17, 22,
		 5,  9, 14, 20,
		 4, 11, 16, 23,
		 6, 10, 15, 21,
	};
	
}
