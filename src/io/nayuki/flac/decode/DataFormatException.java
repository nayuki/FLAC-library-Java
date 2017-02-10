/* 
 * FLAC library (Java)
 * Copyright (c) Project Nayuki. All rights reserved.
 * https://www.nayuki.io/
 */

package io.nayuki.flac.decode;


@SuppressWarnings("serial")
public class DataFormatException extends RuntimeException {
	
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
