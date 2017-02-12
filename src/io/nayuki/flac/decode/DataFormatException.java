/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.decode;


// Thrown when data being read violates the FLAC file format.
@SuppressWarnings("serial")
public class DataFormatException extends RuntimeException {
	
	/*---- Constructors ----*/
	
	public DataFormatException() {
		super();
	}
	
	
	public DataFormatException(String msg) {
		super(msg);
	}
	
	
	public DataFormatException(String msg, Throwable cause) {
		super(msg, cause);
	}
	
}
