/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.encode;

import java.util.Objects;


/* 
 * Pairs an integer with an arbitrary object. Immutable structure.
 */
final class SizeEstimate<E> {
	
	/*---- Fields ----*/
	
	public final long sizeEstimate;  // Non-negative
	public final E encoder;  // Not null
	
	
	
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
