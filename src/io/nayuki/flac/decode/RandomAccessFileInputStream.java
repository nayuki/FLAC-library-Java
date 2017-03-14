/* 
 * FLAC library (Java)
 * 
 * Copyright (c) Project Nayuki
 * https://www.nayuki.io/page/flac-library-java
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program (see COPYING.txt and COPYING.LESSER.txt).
 * If not, see <http://www.gnu.org/licenses/>.
 */

package io.nayuki.flac.decode;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Objects;


/* 
 * A bare adapter from RandomAccessFile to InputStream. These objects have no buffer, so any seek()
 * on the underlying RAF is visible on the next read(). Also, objects of this class have no direct
 * native resources - so it is safe to discard a RandomAccessFileInputStream object without closing it,
 * as long as other code will close() the underlying RandomAccessFile object.
 */
public final class RandomAccessFileInputStream extends InputStream {
	
	/*---- Fields ----*/
	
	private RandomAccessFile in;
	
	
	
	/*---- Constructors ----*/
	
	public RandomAccessFileInputStream(RandomAccessFile raf) {
		Objects.requireNonNull(raf);
		this.in = raf;
	}
	
	
	
	/*---- Methods ----*/
	
	public long getPosition() throws IOException {
		return in.getFilePointer();
	}
	
	
	public void seek(long pos) throws IOException {
		in.seek(pos);
	}
	
	
	public int read() throws IOException {
		return in.read();
	}
	
	
	public int read(byte[] b, int off, int len) throws IOException {
		return in.read(b, off, len);
	}
	
	
	public void close() throws IOException {
		if (in != null) {
			in.close();
			in = null;
		}
	}
	
}
