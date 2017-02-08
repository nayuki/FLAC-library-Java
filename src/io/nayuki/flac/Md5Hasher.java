/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac;

import static java.lang.Integer.rotateLeft;
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
		int sch00 = (block[ 0] & 0xFF) | (block[ 1] & 0xFF) << 8 | (block[ 2] & 0xFF) << 16 | block[ 3] << 24;
		int sch01 = (block[ 4] & 0xFF) | (block[ 5] & 0xFF) << 8 | (block[ 6] & 0xFF) << 16 | block[ 7] << 24;
		int sch02 = (block[ 8] & 0xFF) | (block[ 9] & 0xFF) << 8 | (block[10] & 0xFF) << 16 | block[11] << 24;
		int sch03 = (block[12] & 0xFF) | (block[13] & 0xFF) << 8 | (block[14] & 0xFF) << 16 | block[15] << 24;
		int sch04 = (block[16] & 0xFF) | (block[17] & 0xFF) << 8 | (block[18] & 0xFF) << 16 | block[19] << 24;
		int sch05 = (block[20] & 0xFF) | (block[21] & 0xFF) << 8 | (block[22] & 0xFF) << 16 | block[23] << 24;
		int sch06 = (block[24] & 0xFF) | (block[25] & 0xFF) << 8 | (block[26] & 0xFF) << 16 | block[27] << 24;
		int sch07 = (block[28] & 0xFF) | (block[29] & 0xFF) << 8 | (block[30] & 0xFF) << 16 | block[31] << 24;
		int sch08 = (block[32] & 0xFF) | (block[33] & 0xFF) << 8 | (block[34] & 0xFF) << 16 | block[35] << 24;
		int sch09 = (block[36] & 0xFF) | (block[37] & 0xFF) << 8 | (block[38] & 0xFF) << 16 | block[39] << 24;
		int sch10 = (block[40] & 0xFF) | (block[41] & 0xFF) << 8 | (block[42] & 0xFF) << 16 | block[43] << 24;
		int sch11 = (block[44] & 0xFF) | (block[45] & 0xFF) << 8 | (block[46] & 0xFF) << 16 | block[47] << 24;
		int sch12 = (block[48] & 0xFF) | (block[49] & 0xFF) << 8 | (block[50] & 0xFF) << 16 | block[51] << 24;
		int sch13 = (block[52] & 0xFF) | (block[53] & 0xFF) << 8 | (block[54] & 0xFF) << 16 | block[55] << 24;
		int sch14 = (block[56] & 0xFF) | (block[57] & 0xFF) << 8 | (block[58] & 0xFF) << 16 | block[59] << 24;
		int sch15 = (block[60] & 0xFF) | (block[61] & 0xFF) << 8 | (block[62] & 0xFF) << 16 | block[63] << 24;
		int a = state[0];
		int b = state[1];
		int c = state[2];
		int d = state[3];
		a += (d ^ (b & (c ^ d))) + 0xD76AA478 + sch00;  a = b + rotateLeft(a,  7);
		d += (c ^ (a & (b ^ c))) + 0xE8C7B756 + sch01;  d = a + rotateLeft(d, 12);
		c += (b ^ (d & (a ^ b))) + 0x242070DB + sch02;  c = d + rotateLeft(c, 17);
		b += (a ^ (c & (d ^ a))) + 0xC1BDCEEE + sch03;  b = c + rotateLeft(b, 22);
		a += (d ^ (b & (c ^ d))) + 0xF57C0FAF + sch04;  a = b + rotateLeft(a,  7);
		d += (c ^ (a & (b ^ c))) + 0x4787C62A + sch05;  d = a + rotateLeft(d, 12);
		c += (b ^ (d & (a ^ b))) + 0xA8304613 + sch06;  c = d + rotateLeft(c, 17);
		b += (a ^ (c & (d ^ a))) + 0xFD469501 + sch07;  b = c + rotateLeft(b, 22);
		a += (d ^ (b & (c ^ d))) + 0x698098D8 + sch08;  a = b + rotateLeft(a,  7);
		d += (c ^ (a & (b ^ c))) + 0x8B44F7AF + sch09;  d = a + rotateLeft(d, 12);
		c += (b ^ (d & (a ^ b))) + 0xFFFF5BB1 + sch10;  c = d + rotateLeft(c, 17);
		b += (a ^ (c & (d ^ a))) + 0x895CD7BE + sch11;  b = c + rotateLeft(b, 22);
		a += (d ^ (b & (c ^ d))) + 0x6B901122 + sch12;  a = b + rotateLeft(a,  7);
		d += (c ^ (a & (b ^ c))) + 0xFD987193 + sch13;  d = a + rotateLeft(d, 12);
		c += (b ^ (d & (a ^ b))) + 0xA679438E + sch14;  c = d + rotateLeft(c, 17);
		b += (a ^ (c & (d ^ a))) + 0x49B40821 + sch15;  b = c + rotateLeft(b, 22);
		a += (c ^ (d & (b ^ c))) + 0xF61E2562 + sch01;  a = b + rotateLeft(a,  5);
		d += (b ^ (c & (a ^ b))) + 0xC040B340 + sch06;  d = a + rotateLeft(d,  9);
		c += (a ^ (b & (d ^ a))) + 0x265E5A51 + sch11;  c = d + rotateLeft(c, 14);
		b += (d ^ (a & (c ^ d))) + 0xE9B6C7AA + sch00;  b = c + rotateLeft(b, 20);
		a += (c ^ (d & (b ^ c))) + 0xD62F105D + sch05;  a = b + rotateLeft(a,  5);
		d += (b ^ (c & (a ^ b))) + 0x02441453 + sch10;  d = a + rotateLeft(d,  9);
		c += (a ^ (b & (d ^ a))) + 0xD8A1E681 + sch15;  c = d + rotateLeft(c, 14);
		b += (d ^ (a & (c ^ d))) + 0xE7D3FBC8 + sch04;  b = c + rotateLeft(b, 20);
		a += (c ^ (d & (b ^ c))) + 0x21E1CDE6 + sch09;  a = b + rotateLeft(a,  5);
		d += (b ^ (c & (a ^ b))) + 0xC33707D6 + sch14;  d = a + rotateLeft(d,  9);
		c += (a ^ (b & (d ^ a))) + 0xF4D50D87 + sch03;  c = d + rotateLeft(c, 14);
		b += (d ^ (a & (c ^ d))) + 0x455A14ED + sch08;  b = c + rotateLeft(b, 20);
		a += (c ^ (d & (b ^ c))) + 0xA9E3E905 + sch13;  a = b + rotateLeft(a,  5);
		d += (b ^ (c & (a ^ b))) + 0xFCEFA3F8 + sch02;  d = a + rotateLeft(d,  9);
		c += (a ^ (b & (d ^ a))) + 0x676F02D9 + sch07;  c = d + rotateLeft(c, 14);
		b += (d ^ (a & (c ^ d))) + 0x8D2A4C8A + sch12;  b = c + rotateLeft(b, 20);
		a += (b ^ c ^ d) + 0xFFFA3942 + sch05;  a = b + rotateLeft(a,  4);
		d += (a ^ b ^ c) + 0x8771F681 + sch08;  d = a + rotateLeft(d, 11);
		c += (d ^ a ^ b) + 0x6D9D6122 + sch11;  c = d + rotateLeft(c, 16);
		b += (c ^ d ^ a) + 0xFDE5380C + sch14;  b = c + rotateLeft(b, 23);
		a += (b ^ c ^ d) + 0xA4BEEA44 + sch01;  a = b + rotateLeft(a,  4);
		d += (a ^ b ^ c) + 0x4BDECFA9 + sch04;  d = a + rotateLeft(d, 11);
		c += (d ^ a ^ b) + 0xF6BB4B60 + sch07;  c = d + rotateLeft(c, 16);
		b += (c ^ d ^ a) + 0xBEBFBC70 + sch10;  b = c + rotateLeft(b, 23);
		a += (b ^ c ^ d) + 0x289B7EC6 + sch13;  a = b + rotateLeft(a,  4);
		d += (a ^ b ^ c) + 0xEAA127FA + sch00;  d = a + rotateLeft(d, 11);
		c += (d ^ a ^ b) + 0xD4EF3085 + sch03;  c = d + rotateLeft(c, 16);
		b += (c ^ d ^ a) + 0x04881D05 + sch06;  b = c + rotateLeft(b, 23);
		a += (b ^ c ^ d) + 0xD9D4D039 + sch09;  a = b + rotateLeft(a,  4);
		d += (a ^ b ^ c) + 0xE6DB99E5 + sch12;  d = a + rotateLeft(d, 11);
		c += (d ^ a ^ b) + 0x1FA27CF8 + sch15;  c = d + rotateLeft(c, 16);
		b += (c ^ d ^ a) + 0xC4AC5665 + sch02;  b = c + rotateLeft(b, 23);
		a += (c ^ (b | ~d)) + 0xF4292244 + sch00;  a = b + rotateLeft(a,  6);
		d += (b ^ (a | ~c)) + 0x432AFF97 + sch07;  d = a + rotateLeft(d, 10);
		c += (a ^ (d | ~b)) + 0xAB9423A7 + sch14;  c = d + rotateLeft(c, 15);
		b += (d ^ (c | ~a)) + 0xFC93A039 + sch05;  b = c + rotateLeft(b, 21);
		a += (c ^ (b | ~d)) + 0x655B59C3 + sch12;  a = b + rotateLeft(a,  6);
		d += (b ^ (a | ~c)) + 0x8F0CCC92 + sch03;  d = a + rotateLeft(d, 10);
		c += (a ^ (d | ~b)) + 0xFFEFF47D + sch10;  c = d + rotateLeft(c, 15);
		b += (d ^ (c | ~a)) + 0x85845DD1 + sch01;  b = c + rotateLeft(b, 21);
		a += (c ^ (b | ~d)) + 0x6FA87E4F + sch08;  a = b + rotateLeft(a,  6);
		d += (b ^ (a | ~c)) + 0xFE2CE6E0 + sch15;  d = a + rotateLeft(d, 10);
		c += (a ^ (d | ~b)) + 0xA3014314 + sch06;  c = d + rotateLeft(c, 15);
		b += (d ^ (c | ~a)) + 0x4E0811A1 + sch13;  b = c + rotateLeft(b, 21);
		a += (c ^ (b | ~d)) + 0xF7537E82 + sch04;  a = b + rotateLeft(a,  6);
		d += (b ^ (a | ~c)) + 0xBD3AF235 + sch11;  d = a + rotateLeft(d, 10);
		c += (a ^ (d | ~b)) + 0x2AD7D2BB + sch02;  c = d + rotateLeft(c, 15);
		b += (d ^ (c | ~a)) + 0xEB86D391 + sch09;  b = c + rotateLeft(b, 21);
		state[0] += a;
		state[1] += b;
		state[2] += c;
		state[3] += d;
	}
	
}
