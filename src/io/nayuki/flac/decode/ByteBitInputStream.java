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
import java.util.Objects;


/* 
 * A bit-oriented input stream, with many methods tailored for FLAC usage (such as Rice decoding and CRC calculation).
 */
public final class ByteBitInputStream extends AbstractFlacLowLevelInput {
	
	/*---- Fields ----*/
	
	// The underlying byte-based input stream to read from.
	private InputStream in;
	
	
	
	/*---- Constructors ----*/
	
	// Constructs a FLAC-oriented bit input stream from the given byte-based input stream.
	public ByteBitInputStream(InputStream in) {
		super();
		Objects.requireNonNull(in);
		this.in = in;
	}
	
	
	
	/*---- Methods ----*/
	
	protected int readUnderlying(byte[] buf, int off, int len) throws IOException {
		return in.read(buf, off, len);
	}
	
	
	public void close() throws IOException {
		if (in != null) {
			in.close();
			in = null;
			super.close();
		}
	}
	
}
