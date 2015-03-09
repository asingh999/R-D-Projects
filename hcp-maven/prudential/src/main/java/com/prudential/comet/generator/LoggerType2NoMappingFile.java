package com.prudential.comet.generator;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class LoggerType2NoMappingFile extends PrudentialBaseFiles {

	private static Logger logger = LogManager.getLogger();
	
	private static String FILE_PATTERN = "\\d{8}-\\d{6}_.*\\.wav";

	public void initialize() { 
		if ( ! isInitialized ) {			

			mFileType = "wav"; 
			mPatternMatch = FILE_PATTERN;
			
			// Setup the date formatter for this recording type.  The timezone information
			//   will be set in the "super" initialization routine.
			mInputDateFormat = new SimpleDateFormat("MMddyy");
			
			super.initialize();
		}
	}
	
	protected String getCustomMetadata(File inSourceFile) {
		
		String retVal = null;
		
		try {
			/*
			 * Verify the file is of the format we expect for this module
			 */
			
			String fileName = inSourceFile.getName();
	
			// Do pattern match. Not using String.matches() because we don't want to compile the
			//   regular expression every invocation.
			if ( ! mFilePattern.matcher(fileName).matches()) {
				// Did not match, so don't go any further
				logger.debug("Did not match file pattern for filename: \"" + fileName + "\"");
				return null;
			}

			// Split apart the fields. NOTE, there 2 or 3 fields.  Inconsistent
			String fileNameParts[] = fileName.split("\\.");
			if (fileNameParts.length != 2 && fileNameParts.length != 3) {
				// Bad.  Not simple name.ext format.
				logger.warn("File name not well formatted (unexpected number of \".\" in name): \"" + fileName + "\"");
				return null;			

			}

			/*
			 * Split up the file parts to mine for the metadata.
			 * 
			 */
			String fileNameCore = fileNameParts[0];
			
			try {
				String StartDate, EndDate, CLSCallID, Session, Logger, Channel, Server, AgentID;
				
				// Now dataFields has a position parse-able fields.
				Logger = fileNameCore.substring(0, 8).trim();
				
				// TODO:
				// TODO:  Is there something useful with the last two numbers.  One is a hex number that is different between
				// TODO:   days, and the other is a sequence number within the folder.
				// TODO:
				StartDate = mOutputDateFormat.format(mInputDateFormat.parse(fileNameCore.substring(9, 22).trim()));
				EndDate = StartDate;
				CLSCallID = null;
				Session = null;
				Channel = null;
				Server = null;
				AgentID = null;
				
				// Now let's put the information into the XML
				retVal = writeXML(logger, StartDate, EndDate, CLSCallID, Session, Logger, Channel, Server, AgentID);

			} catch (ParseException e) {
				logger.fatal("Unexpected date string format encountered for file (" 
			                  + fileName + ")", e);
				
				retVal = null;
			}
			
		} catch (Exception e) {
			logger.fatal("Unexpected Exception", e);
			
			retVal = null;
		}

		return retVal;
	}
}
