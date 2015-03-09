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

/**
 * This class defines an InputStream that is comprised of both the data file and
 * custom metadata file as on stream.
 * 
 * It is used to provide a single stream of object data and custom metadata that
 * will be transmitted over HTTP for Whole I/O PUT operations.
 */
public class WholeIOInputStream extends InputStream {

	/*
	 * Constructor.  Passed in an InputStream for the data file and the custom
	 *   metadata file.
	 */
	public WholeIOInputStream(InputStream inDataFile, InputStream inCustomMetadataFile) {
		mDataFile = inDataFile;
		mCustomMetadataFile = inCustomMetadataFile;
		
		bFinishedDataFile = false;
	}
	
	// Private member variables.
	private Boolean bFinishedDataFile;  // Indicates when all data file content has been read.
	private InputStream mDataFile, mCustomMetadataFile;
	
	public int read(byte b[], int off, int len) throws IOException {
		int retval = 0;  // Assume nothing read.
		
		// Do we still need to read from the data file?
		if (! bFinishedDataFile ) {
			// Read from the data file.
			retval = mDataFile.read(b, off, len);

			// If reached the end of the stream, indicate it is time
			//  to read from the Custom Metadata file.
			if (-1 == retval) {
				bFinishedDataFile = true;
			}
		}

		// This should not be coded as an "else" because it may need to be
		//  run after data file has reached EOF.
		if ( bFinishedDataFile ) {
			// Read from the custom metadata file.
			retval = mCustomMetadataFile.read(b, off, len);
		}
		
		return retval;
	}

	/*
	 * Base InputStream read function that reads from either the data file or custom metadata
	 * depending on how much has been read so far.
	 */
	public int read() throws IOException {
		int retval = 0;  // Assume nothing read.
		
		// Do we still need to read from the data file?
		if (! bFinishedDataFile ) {
			// Read from the data file.
			retval = mDataFile.read();

			// If reached the end of the stream, indicate it is time
			//  to read from the Custom Metadata file.
			if (-1 == retval) {
				bFinishedDataFile = true;
			}
		}

		// This should not be coded as an "else" because it may need to be
		//  run after data file has reached EOF.
		if ( bFinishedDataFile ) {
			// Read from the custom metadata file.
			retval = mCustomMetadataFile.read();
		}
		
		return retval;
	}
}

