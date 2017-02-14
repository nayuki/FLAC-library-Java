/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.encode;

import java.util.Objects;


/* 
 * A mutable structure that pairs an integer with an arbitrary object.
 */
final class SizeEstimate<E> {
	
	/*---- Fields ----*/
	
	public long sizeEstimate;  // Should be non-negative
	public E encoder;  // Should be not null
	
	
	
	/*---- Constructors ----*/
	
	public SizeEstimate(long size, E enc) {
		if (size < 0)
			throw new IllegalArgumentException();
		Objects.requireNonNull(enc);
		sizeEstimate = size;
		encoder = enc;
	}
	
	
	
	/*---- Methods ----*/
	
	// Returns this object if the size is less than or equal to the other object, otherwise returns other.
	public SizeEstimate<E> minimum(SizeEstimate<E> other) {
		Objects.requireNonNull(other);
		if (sizeEstimate <= other.sizeEstimate)
			return this;
		else
			return other;
	}
	
}
