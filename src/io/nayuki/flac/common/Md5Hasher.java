/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;


public final class Md5Hasher {
	
	public static byte[] getHash(int[][] samples, int depth) {
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
