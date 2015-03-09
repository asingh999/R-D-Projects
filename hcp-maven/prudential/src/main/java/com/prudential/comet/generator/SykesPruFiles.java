/*
 *   Copyright (c) 2012 Hitachi Data Systems, Inc.
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
 * Disclaimer: This code is only a sample and is provided for educational purposes.
 * The consumer of this sample assumes full responsibility for any effects due to
 * coding errors.
 * 
 */
package com.prudential.comet.generator;

import java.io.File;
import java.text.ParseException;
import java.util.NoSuchElementException;
import java.util.Scanner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hds.hcp.tools.comet.generator.SystemMetadataContainer;

public class SykesPruFiles extends PrudentialBaseFiles {

	private static Logger logger = LogManager.getLogger();

	private static String FILE_PATTERN = "sykespru_.*\\.wav";
			
	private static String mListingFile = "sykespru_audfiles.txt";
	
	public void initialize() {
		if ( ! isInitialized ) {
			mFileType = "wav";  /// NOTE: This is default.
			mPatternMatch = FILE_PATTERN;
			
			super.initialize();
		}
	}
	
	protected String getCustomMetadata(File inSourceFile) {
		
		String retVal = "";
		
		try {
			/*
			 * Verify the file is of the format we expect for this module
			 */
			
			// Should be of the form sykespru_<logger>_<seqnum>.wav OR sykespru_WAV_<seqnum>.wav
			
			String fileName = inSourceFile.getName();
	
			// Do pattern match. Not using String.matches() because we don't want to compile the
			//   regular expression every invocation.
			if ( ! mFilePattern.matcher(fileName).matches()) {
				// Did not match, so don't go any further
				logger.debug("Did not match file pattern for filename: \"" + fileName + "\"");
				return null;
			}

			// Split apart the fields.
			String fileNameParts[] = fileName.split("\\.");
			if (fileNameParts.length != 2) {
				// Bad.  Not simple name.ext format.
				logger.warn("File name not well formatted (too many \".\" in name): \"" + fileName + "\"");
				return null;
			}
			
			File ListingFile = new File(inSourceFile.getParentFile().getAbsolutePath() + File.separator + mListingFile);
			
			if ( ! ListingFile.exists() ) {
				// Bad.  Can't find file
				logger.fatal("Unable to locate listing file: \"" + ListingFile.getCanonicalPath() + "\"");
				return null;
			}
			
			if ( ! ListingFile.isFile() || ! ListingFile.canRead() ) {
				logger.fatal("Listing file is not a regular readable file: \"" + ListingFile.getCanonicalPath() + "\"");
				return null;
			}
	
			String currentLine = null;
			Scanner fileScanner = new Scanner(ListingFile);
	
			try {
				// Keep searching through the file looking for the filename.
				//  Note: It will throw an exception when the EOF is reached.
				//    The null test below it to keep Java happy.
				while( (currentLine = fileScanner.nextLine()) != null )
				{
					if (-1 != currentLine.indexOf(fileNameParts[0]))
					{
				    	break;  // Found the string
				    }
				}
			} catch (NoSuchElementException e) {
				// Oops!!  Didn't find it.  
				logger.fatal("Unable to find file \"" + fileName + "\" in listing file.");
				
				fileScanner.close();
				
				return null;
			}
			
			fileScanner.close();
			
			/*
			 * Well... If we got here, that means we found a line in the file that matches our
			 *   file name.  So time to go through the fun and parse it.
			 */
			try {
				retVal = processDownloaderMetadata(logger, fileNameParts[0], currentLine);
			} catch (ParseException e) {
				logger.fatal("Unexpected date string format encountered for file (" 
			                  + fileName 
			                  + ") in listing file \"" + ListingFile.getAbsolutePath() + "\"", e);
				retVal = null;
			}
		} catch (Exception e) {
			logger.fatal("Unexpected Exception", e);
			
			retVal = null;
		}

		return retVal;
	}

	// 
	//  Set specific metadata for the objects recognized by this module.
	//
	public SystemMetadataContainer getSystemMetadata(File inSourceFile, SystemMetadataContainer inBaseMetadata)
	{
		SystemMetadataContainer retval = null;
		
		// NOTE:  If this module were conditionally processing files, there should be the same conditional
		//    in both this method and the getCustomMetadata method.
		
		// As an example, only put shredding on files that are wav files.
		if (inSourceFile.getName().matches(FILE_PATTERN)) {
			inBaseMetadata.setShredding(Boolean.TRUE);

			retval = inBaseMetadata;
		}

		return retval;
	};

}
