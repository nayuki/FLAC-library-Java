/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.encode;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;


/* 
 * An adapter from RandomAccessFile to OutputStream.
 */
public final class RandomAccessFileOutputStream extends OutputStream {
	
	private RandomAccessFile out;
	
	
	public RandomAccessFileOutputStream(RandomAccessFile raf) {
		this.out = raf;
	}
	
	
	public void write(int b) throws IOException {
		out.write(b);
	}
	
	
	public void write(byte[] b, int off, int len) throws IOException {
		out.write(b, off, len);
	}
	
	
	public void close() throws IOException {
		out.close();
	}
	
}
