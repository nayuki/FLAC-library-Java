/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.encode;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Objects;


/* 
 * An adapter from RandomAccessFile to OutputStream. These objects have no buffer, so seek()
 * and write() can be safely interleaved. Also, objects of this class have no direct
 * native resources - so it is safe to discard a RandomAccessFileOutputStream object without
 * closing it, as long as other code will close() the underlying RandomAccessFile object.
 */
public final class RandomAccessFileOutputStream extends OutputStream {
	
	/*---- Fields ----*/
	
	private RandomAccessFile out;
	
	
	
	/*---- Constructors ----*/
	
	public RandomAccessFileOutputStream(RandomAccessFile raf) {
		Objects.requireNonNull(raf);
		this.out = raf;
	}
	
	
	
	/*---- Methods ----*/
	
	public long getPosition() throws IOException {
		return out.getFilePointer();
	}
	
	
	public void seek(long pos) throws IOException {
		out.seek(pos);
	}
	
	
	public void write(int b) throws IOException {
		out.write(b);
	}
	
	
	public void write(byte[] b, int off, int len) throws IOException {
		out.write(b, off, len);
	}
	
	
	public void close() throws IOException {
		out.close();
		out = null;
	}
	
}
