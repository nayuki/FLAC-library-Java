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
 * An adapter from RandomAccessFile to InputStream. Objects of this class have no direct
 * native resources - so it is safe to discard a RandomAccessFileInputStream object without
 * closing it, as long as other code will close() the underlying RandomAccessFile object.
 */
public final class RandomAccessFileInputStream extends InputStream {
	
	/*---- Fields ----*/
	
	private RandomAccessFile in;
	
	
	
	/*---- Constructors ----*/
	
	public RandomAccessFileInputStream(RandomAccessFile raf) {
		this.in = raf;
	}
	
	
	
	/*---- Methods ----*/
	
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
