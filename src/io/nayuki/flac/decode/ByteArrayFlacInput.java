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
import java.util.Objects;


public final class ByteArrayFlacInput extends AbstractFlacLowLevelInput {
	
	/*---- Fields ----*/
	
	private byte[] data;
	private int offset;
	
	
	
	/*---- Constructors ----*/
	
	public ByteArrayFlacInput(byte[] b) {
		super();
		Objects.requireNonNull(b);
		data = b;
		offset = 0;
	}
	
	
	
	/*---- Methods ----*/
	
	protected int readUnderlying(byte[] buf, int off, int len) {
		int n = Math.min(data.length - offset, len);
		if (n == 0)
			return -1;
		System.arraycopy(data, offset, buf, off, n);
		offset += n;
		return n;
	}
	
	
	public void close() throws IOException {
		if (data != null) {
			data = null;
			super.close();
		}
	}
	
}
