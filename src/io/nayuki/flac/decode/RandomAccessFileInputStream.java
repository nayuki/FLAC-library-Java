/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.decode;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;


/* 
 * An adapter from RandomAccessFile to InputStream.
 */
public final class RandomAccessFileInputStream extends InputStream {
	
	private RandomAccessFile in;
	
	
	public RandomAccessFileInputStream(RandomAccessFile raf) {
		this.in = raf;
	}
	
	
	public int read() throws IOException {
		return in.read();
	}
	
	
	public int read(byte[] b, int off, int len) throws IOException {
		return in.read(b, off, len);
	}
	
	
	public void close() throws IOException {
		in.close();
	}
	
}
