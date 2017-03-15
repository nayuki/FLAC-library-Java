
package io.nayuki.flac.decode;

import java.io.IOException;


public interface FlacLowLevelInput extends AutoCloseable {
	
	/*---- Bit position ----*/
	
	// Returns the number of bits in the current byte that have been consumed.
	// This starts at 0, increments for each bit consumed, topping out at 7, then wraps around and repeats.
	public int getBitPosition();
	
	
	
	/*---- Reading bitwise integers ----*/
	
	// Reads the next given number of bits as an unsigned integer (i.e. zero-extended to int32).
	// Note that when n = 32, the result will be always a signed integer.
	public int readUint(int n) throws IOException;
	
	
	// Reads the next given number of bits as an signed integer (i.e. sign-extended to int32).
	public int readSignedInt(int n) throws IOException;
	
	
	// Reads and decodes the next batch of Rice-coded signed integers. Note that any Rice-coded integer might read a large
	// number of bits from the underlying stream (but not in practice because it would be a very inefficient encoding).
	// Every new value stored into the array is guaranteed to fit into a signed int53 - see FrameDecoder.restoreLpc()
	// for an explanation of why this is a necessary (but not sufficient) bound on the range of decoded values.
	public void readRiceSignedInts(int param, long[] result, int start, int end) throws IOException;
	
	
	
	/*---- Reading bytes ----*/
	
	// Discards any partial bits, then either returns an unsigned byte value or -1 for EOF.
	public int readByte() throws IOException;
	
	
	// Discards any partial bits, then reads the given array fully or throws EOFEOxception.
	public void readFully(byte[] b) throws IOException;
	
	
	
	/*---- CRC calculations ----*/
	
	// Marks the current position (which must be byte-aligned) as the start of both CRC calculations.
	public void resetCrcs();
	
	
	// Returns the CRC-8 hash of all the bytes read since the last call to resetCrcs()
	// (or from the beginning of stream if reset was never called).
	public int getCrc8();
	
	
	// Returns the CRC-16 hash of all the bytes read since the last call to resetCrcs()
	// (or from the beginning of stream if reset was never called).
	public int getCrc16();
	
	
	
	/*---- Miscellaneous ----*/
	
	// Returns the number of bytes consumed since the start of the stream.
	public long getByteCount();
	
	
	// Discards all buffered data so that the next read loads new data from the underlying stream.
	// This method only makes sense if the underlying stream is seekable or resettable.
	// This also resets the CRC calculation and number of bytes read.
	public void flush();
	
	
	// Discards all buffers and closes the underlying input stream. This bit input stream becomes invalid
	// for any future operation. Note that a ByteBitInputStream only uses memory but does not have native resources.
	// It is okay to simply let a ByteBitInputStream be garbage collected without calling close(), but the parent is still responsible
	// for calling close() on the underlying input stream if it uses native resources (such as FileInputStream or SocketInputStream).
	// For example if the underlying stream supports seeking, then it is okay to discard an existing ByteBitInputStream,
	// call seek on the underlying stream, and wrap a new ByteBitInputStream over the underlying stream after seeking.
	public void close() throws IOException;
	
}
