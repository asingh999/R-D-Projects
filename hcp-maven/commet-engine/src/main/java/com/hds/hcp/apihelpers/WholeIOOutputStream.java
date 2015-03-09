/*
 *   Copyright (c) 2011 Hitachi Data Systems, Inc.
 *
 *   Permission is hereby granted to  this software and associated
 *   documentation files (the "Software"), subject to the terms and
 *   conditions of the Sample Source Code License (SSCL) delivered
 *   with this Software. If you do not agree to the terms and
 *   conditions of the SSCL,
 *
 *     (i)  you must close this file and delete all copies of the
 *          Software, and
 *     (ii) any permission to use the Software is expressly denied.
 *
 */

package com.hds.hcp.apihelpers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This class defines an OututStream that will create both the object data file
 * and the custom metadata file. The copy() method is used to read an InputStream
 * and create the two output files based on the indicated size of the data file
 * portion of the stream.
 * 
 * It is used to split and create content retrieved over HTTP as a single stream for
 * Whole I/O GET.
 */
public class WholeIOOutputStream extends OutputStream {
	
	// Constructor.  Passed output streams for the data file and the custom metadata.
	//   Allows specification as to whether custom metadata comes before data file.
	public WholeIOOutputStream(OutputStream inDataFile, 
			OutputStream inCustomMetadataFile, 
			Boolean inCustomMetadataFirst) {

		bCustomMetadataFirst = inCustomMetadataFirst;
		
		// Setup first and second file Output Streams based on whether custom
		//  metadata is first in the stream.
		if (bCustomMetadataFirst) {
			mFirstFile = inCustomMetadataFile;
			mSecondFile = inDataFile;
		} else {
			mFirstFile = inDataFile;
			mSecondFile = inCustomMetadataFile;
		}
		
		bFinishedFirstPart = false;
	}
	
	// Member variables.
	private Boolean bFinishedFirstPart;
	private Boolean bCustomMetadataFirst;
	private OutputStream mFirstFile, mSecondFile;
	
	/**
	 * This routine copies content in an InputStream and to this Output Stream. 
	 * The first inDataSize number of bytes are written to the data file output
	 * stream.
	 * 
	 * @param inStream - InputStream to copy content from.
	 * @param inFirstPartSize - number of bytes of inStream that should be written to
	 *      the first output stream.
	 * @throws IOException
	 */
	public void copy(InputStream inStream, Integer inFirstPartSize) throws IOException {
		int streamPos = 0;
		byte buffer[] = new byte[2048];
		
		int readLength = 0;
		
		// Keep reading bytes until EOF has been reached.
		while (-1 != (readLength = inStream.read(buffer, 0, Math.min(buffer.length, (bFinishedFirstPart ? buffer.length : inFirstPartSize - streamPos ) )))) {

			// Update where we are at in the stream.
			streamPos += readLength;
			
			// Write out what we have.
			write(buffer, 0, readLength);

			// Did we just write all the content for the first file?
			if ( streamPos == inFirstPartSize)
			{
				// Yes.  So flag that the next write should be to the second file.
				bFinishedFirstPart = true;
			}
		}
	}
	
	/**
	 * This is the core buffer write function for the OutputStream implementation.
	 * It either writes to the first or second file stream depending on where in the
	 * stream it is.
	 */
	public void write(byte[] b, int offset, int length) throws IOException {
		// Write to first or second file depending on where we are in the
		//  stream.
		if (! bFinishedFirstPart ) {
			mFirstFile.write(b, offset, length);
		} else {
			mSecondFile.write(b, offset, length);
		}
	}
	
	/**
	 * This is the core write function for the InputStream implementation. It
	 * either writes to the first or second file stream depending on where in
	 * the whole stream it is.
	 */
	public void write(int b) throws IOException {
		// Write to first or second file depending on where we are in the
		//  stream.
		if (! bFinishedFirstPart ) {
			mFirstFile.write(b);
		} else {
			mSecondFile.write(b);
		}
	}

	/**
	 * flush() method to flush all files involved.
	 */
	public void flush() throws IOException {
		mFirstFile.flush();
		mSecondFile.flush();
		super.flush();
	}
	
	/**
	 * close() method to first close the data file and custom metadata files.
	 * Then close itself.
	 */
	public void close() throws IOException {
		mFirstFile.close();
		mSecondFile.close();
		super.close();
	}
}

