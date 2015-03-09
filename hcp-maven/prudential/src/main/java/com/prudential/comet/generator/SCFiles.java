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
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hds.hcp.tools.comet.generator.SystemMetadataContainer;

public class SCFiles extends PrudentialBaseFiles {

	private static Logger logger = LogManager.getLogger();

	private static String FILE_PATTERN = "SC_\\d{1,8}_\\d{8}_\\d{6}_.*\\.Aud";

	private String mFields[] = null;

	// Should be of the form SC_<logger>_<start-mmddyyyy>_<start-HHmmss>_<end-mmddyyyy>_<end-HHmmss>_<channel>_<CLSCallID>_<AgentID>.Aud
	// And the trailing _<AgentID> can be optional.
	// NOTE: Index 0 is just the file preface.
	private static final int LOGGER = 1;
	private static final int START_DATE = 2;
	private static final int START_TIME = 3;
	private static final int END_DATE = 4;
	private static final int END_TIME = 5;
	private static final int CHANNEL = 6;
	private static final int CLS_CALL_ID = 7;
	private static final int AGENT_ID = 8;
	
	public void initialize() {
		if ( ! isInitialized ) {
			mFileType = "Aud"; 
			mPatternMatch = FILE_PATTERN;
			
			// Setup the date formatter for this recording type.  The timezone information
			//   will be set in the "super" initialization routine.
			mInputDateFormat = new SimpleDateFormat("ddMMyyyy HHmmss");

			super.initialize();
		}
	}

	private void parseFields(String inFileName) {

		mFields = null;
		
		// Split apart the fields.
		String fileNameParts[] = inFileName.split("\\.");
		if (fileNameParts.length != 2) {
			// Bad.  Not simple name.ext format.
			logger.warn("File name not well formatted (too many \".\" in name): \"" + inFileName + "\"");
			return;
		}

		mFields = fileNameParts[0].split("_");
		
		if (mFields.length != 9 && mFields.length != 8) {
			// Bad. Not enough fields
			logger.warn("File name does not have 8 or 9 fields: \"" + inFileName + "\"");
			
			mFields = null;
		}
	}
	
	protected String getCustomMetadata(File inSourceFile) {
		String retVal = "";

		try {
			/*
			 * Verify the file is of the format we expect for this module
			 */
			
			// Should be of the form SC_<logger>_<start-mmddyyyy>_<start-HHmmss>_<end-mmddyyyy>_<end-HHmmss>_<channel>_<CLSCallID>_<AgentID>.Aud
			// Note "_<AgentID>" is optional.
			
			String fileName = inSourceFile.getName();

			// Do pattern match. Not using String.matches() because we don't want to compile the
			//   regular expression every invocation.
			if ( ! mFilePattern.matcher(fileName).matches()) {
				// Did not match, so don't go any further
				logger.debug("Did not match file pattern for filename: \"" + fileName + "\"");
				return null;
			}

			// Break up the file name into all the needed fields into mFields
			parseFields(fileName);

			// If we failed to parse the filename, don't return metadata.
			if (null == mFields) {
				return null;
			}
			
			String StartDate, EndDate, CLSCallID, Session, Logger, Channel, Server, AgentID;
			try {
				StartDate = mOutputDateFormat.format(mInputDateFormat.parse(mFields[START_DATE] + " " + mFields[START_TIME]));
				EndDate = mOutputDateFormat.format(mInputDateFormat.parse(mFields[END_DATE] + " " + mFields[END_TIME]));
				
				CLSCallID = mFields[CLS_CALL_ID];
				Session = null;
				Logger = mFields[LOGGER];
				Channel = mFields[CHANNEL];
				Server = null;
				if (mFields.length == 9) {
					AgentID = mFields[AGENT_ID];
				} else {
					AgentID = null;
				}

			} catch (ParseException e) {
				logger.debug("Unexpected date string format encountered in file name (" + fileName + ")");
				return null;
			}

			// Now let's put the information into the XML
			retVal = writeXML(logger, StartDate, EndDate, CLSCallID, Session, Logger, Channel, Server, AgentID);
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
		/*
		 * Verify the file is of the format we expect for this module
		 */
		
		// Should be of the form SC_<logger>_<start-mmddyyyy>_<start-HHmmss>_<end-mmddyyyy>_<end-HHmmss>_<channel>_<CLSCallID>_<AgentID>.Aud
		
		String fileName = inSourceFile.getName();

		// Do pattern match. Not using String.matches() because we don't want to compile the
		//   regular expression every invocation.
		if ( ! mFilePattern.matcher(fileName).matches()) {
			// Did not match, so don't go any further
			logger.debug("Did not match file pattern for filename: \"" + fileName + "\"");
			return null;
		}

		// Break up the file name into all the needed fields into mFields
		parseFields(fileName);

		try {
			// Set up retention on this file to be 'RETENTION_PERIOD_YEARS' years past the start time.
			
			Calendar c = Calendar.getInstance();
			c.setTime(mInputDateFormat.parse(mFields[START_DATE] + " " + mFields[START_TIME]));
			c.add(Calendar.YEAR, RETENTION_PERIOD_YEARS);  // Add a retention for 7 years from call start date.
			
			inBaseMetadata.setRetention(mHCPRetentionDateFormat.format(c.getTime()));
		} catch (ParseException e) {
			logger.fatal("Unexpected date string format encountered in file name (" + fileName + ")", e);
			return null;
		}

		return inBaseMetadata;
	};

}
