/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.encode;

import java.util.Objects;


// A mutable structure.
final class SizeEstimate<E> {
	
	public long sizeEstimate;
	public E encoder;
	
	
	public SizeEstimate(long size, E enc) {
		if (size < 0)
			throw new IllegalArgumentException();
		Objects.requireNonNull(enc);
		sizeEstimate = size;
		encoder = enc;
	}
	
}
