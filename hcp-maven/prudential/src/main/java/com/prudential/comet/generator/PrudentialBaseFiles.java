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
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.TimeZone;
import java.util.regex.Pattern;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hds.hcp.apihelpers.HCPUtils;
import com.hds.hcp.tools.comet.BaseWorkItem;
import com.hds.hcp.tools.comet.generator.BaseGeneratorProperties;
import com.hds.hcp.tools.comet.generator.BaseMetadataGenerator;
import com.hds.hcp.tools.comet.generator.ObjectContainer;
import com.hds.hcp.tools.comet.generator.SystemMetadataContainer;
import com.hds.hcp.tools.comet.utils.StaticUtils;
import com.hds.hcp.tools.comet.utils.URIWrapper;

abstract class PrudentialBaseFiles extends BaseMetadataGenerator {

	private static Logger logger = LogManager.getLogger();
	
	static final String BASE_FILENAME_PROPERTY = "com.prudential.properties.file";
	
	protected String mFileType = "";
	protected String mPatternMatch = "";
	protected boolean isInitialized;
	protected SimpleDateFormat mInputDateFormat;
	protected SimpleDateFormat mOutputDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
	
	protected static final int RETENTION_PERIOD_YEARS = 7;

	// Used for efficient pattern matching by compiling the pattern once.
	protected Pattern mFilePattern;
	
	/*
	 * Construct a private properties class to construct module specific information.
	 */
	protected class BaseProperties extends BaseGeneratorProperties {
		public BaseProperties() {
			super(BASE_FILENAME_PROPERTY);
		}


		// Define private property file values.
		public String getDestUserName(String inName) {
			return StaticUtils.resolveEnvVars(mProps.getProperty(inName + ".user"));
		}

		public String getDestEncodedUserName(String inName) {
			return HCPUtils.toBase64Encoding(getDestUserName(inName));
		}

		public String getDestPassword(String inName) {
			return StaticUtils.resolveEnvVars(mProps.getProperty(inName + ".password"));
		}
		
		public String getDestEncodedPassword(String inName) {
			if (isDestPasswordEncoded(inName)) {
				return getDestPassword(inName);
			} else {
				try {
					return HCPUtils.toMD5Digest(getDestPassword(inName));
				} catch (Exception e) {
					logger.warn("WARNING: Failed to encode destination password.  Will be followed by failure to authenticate.");
					return "";
				}
			}
		}
		
		public Boolean isDestPasswordEncoded(String inName) {
			return new Boolean(mProps.getProperty(inName + ".passwordEncoded", "false"));
		}

		public String getDestRootPath(String inName) {
			return StaticUtils.resolveEnvVars(mProps.getProperty(inName + ".rootPath"));
		}

	}

	/*
	 * Construct a private properties class to construct module specific information.
	 */
	class PrudentialProperties extends BaseProperties {
		public PrudentialProperties() {
		}

		// Define private property file values.
		public String getRetentionFilePattern() {
			return StaticUtils.convertFilePatternToRegExpr(mProps.getProperty("metadata.retentionFilePattern", ""));
		}
		
		public String getRetentionValue() {
			return mProps.getProperty("metadata.retentionValue", null);
		}
		
		public String getMetadataTimeZone() {
			return mProps.getProperty("metadata.timeZone");
		}
	}

	protected PrudentialProperties mProps = new PrudentialProperties();

	
	public void initialize() {
		mFilePattern = Pattern.compile(mPatternMatch);

		isInitialized = true;
		
		if (null == mInputDateFormat) {
			mInputDateFormat = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a");
		}

		// Setup input date/time timezone based on configuration, if any.
		TimeZone tz = TimeZone.getDefault();
		String timeZoneName = mProps.getMetadataTimeZone();
		if (null != timeZoneName) {
			tz = TimeZone.getTimeZone(timeZoneName);
			if ( ! timeZoneName.equals("GMT") && tz.getID().equals("GMT")) {
				logger.warn("Metadata timezone setting might be invalid. Defaulting to time zone of executing system.");
			}
		}
		logger.debug("Using time zone ({}) for input date/time values.", tz.getID());
		mInputDateFormat.setTimeZone(tz);

		// Output will be in GMT (Greenwich)
		mOutputDateFormat.setTimeZone(TimeZone.getTimeZone("Greenwich"));
	}
		
	protected abstract String getCustomMetadata(File inSourceFile);
	
	@Override
	public LinkedList<ObjectContainer> getMetadataList(BaseWorkItem inItem) {
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		LinkedList<ObjectContainer> retval = null;
		
		/**
		 *  Build the base object metadata
		 */

		if ( ! (inItem.getBaseSpecification() instanceof File) ) {
			logger.fatal("BaseWorkItem.getBaseSpecification() is not a java.io.File");

			// TODO Probably should see about throwing an exception.
			StaticUtils.TRACE_METHOD_EXIT(logger);
			return null;
		}
		
		if ( ! (inItem.getHandle() instanceof File) ) {
			logger.fatal("BaseWorkItem.getHandle() is not a java.io.File");

			// TODO Probably should see about throwing an exception.
			StaticUtils.TRACE_METHOD_EXIT(logger);
			return null;
		}
		
		File inSrcFile = (File)(inItem.getHandle());
		File inBaseFolder = (File)(inItem.getBaseSpecification());
			
		/*
		 * First see if this file will construct custom metadata
		 */
		String customMetadata = getCustomMetadata(inSrcFile);
		if (null == customMetadata || 0 == customMetadata.length()) {
			// Nope. Probably not the correct file pattern for this module.
			return null;
		}

		/**
		 *  Build the base object metadata
		 */

		//
		// First formulate the destination object path based on source file path, base folder path,
		//   and the settings in the properties file.
		//
		StringBuffer destFilePath = new StringBuffer(inSrcFile.getAbsolutePath());
		
		// Remove the initial path from the full path.
		if (0 == destFilePath.indexOf(inBaseFolder.getAbsolutePath())) {
			destFilePath.delete(0, inBaseFolder.getAbsolutePath().length() + File.separator.length());
		}
		
		// Add back in the Trailing initial path, if indicated
		if (mProps.shouldAppendSourcePathTrailingFolder()) {
			destFilePath.insert(0, inBaseFolder.getName() + File.separator);
		}
		
		// Need to be OS agnostic and replace any FS separators with HTTP separator, if it isn't the same.
		if (! File.separator.equals(StaticUtils.HTTP_SEPARATOR)) {
			int charIndex = destFilePath.indexOf(File.separator);
			while (-1 != charIndex) {
				destFilePath.replace(charIndex, charIndex + File.separator.length(), StaticUtils.HTTP_SEPARATOR);
				charIndex = destFilePath.indexOf(File.separator);
			}
		}
		
		// Make sure it isn't going to barf when passed into the Apache HTTP Client.
		ObjectContainer retObject = null;
		try {
			// Construct the destination root string paying attention as to whether we need to add
			//   an HTTP Separator or not.
			String destinationRoot = mProps.getDestRootPath("destination");
			String objectPath = destinationRoot 
					+ (destinationRoot.lastIndexOf(StaticUtils.HTTP_SEPARATOR) != ( destinationRoot.length() - 1 ) ? StaticUtils.HTTP_SEPARATOR : "")
					+ destFilePath.toString();
			retObject = new ObjectContainer(new URIWrapper(objectPath), mProps);
		} catch(URISyntaxException e) {
			logger.fatal("URL formulation issue (Skipping): \"" + e.getMessage());

			return null;
		}

		/*
		 * Set the core system metadata for the object.
		 */
		SystemMetadataContainer sysMeta = retObject.getSystemMetadata();
		
		// Need the credentials from properties file.
		sysMeta.setCredentials(mProps.getDestEncodedUserName("destination"),
                               mProps.getDestEncodedPassword("destination"));
		
		/*
		 * If we got this far, we are in good shape.  We have a core system metadata and some custom metadata.
		 */
		
		retObject.getCustomMetadata().put(mProps.getAnnotationName(), customMetadata);

		//Now add in the customMetadata previously constructed.
		retval = new LinkedList<ObjectContainer>();
		retval.add(retObject);
		
		return retval;
	}
	
	protected final static int FIELD_SIZE=20;
	
	protected String processDownloaderMetadata(Logger inLogger, String inBaseFileName, String inDataLine) throws ParseException {
		// For the most part, the fields are in 20 character columns and consist of the following fields:
		//   Filename, Aud/Wav, TalkingClock, StartTime, StopTime, CLSCallID, Session, Extension, PhoneNumber,
		//   Logger, LocationLogger, Channel, ServerID, AgentID, UserID, CustomerID, Flags,
		//   Direction, and CompoundID.
		
		// Of these fields, only the following might be filled in:
		//   Filename, StartTime, StopTime, CLSCallID, Session, Logger, Channel, ServerID, AgentID

		// Need to first "normalize" the line because the file path can mess up the field position.
		// So chop off up to the end of the file name.
		int endOfStringIndex = inDataLine.indexOf(inBaseFileName) + inBaseFileName.length();
		
		String dataFields = inDataLine.substring(endOfStringIndex);
		int curFieldPos=0;
		
		// Now collect the following strings from the remainder.
		String StartDate, EndDate, CLSCallID, Session, Logger, Channel, Server, AgentID;

		//  The date fields could take up from 19 to 22 characters, so need to be careful about this.
		//  For Example:
		//    1/1/2014 7:30:00 PM  - 19 chars
		//    12/30/2014 12:30:00 AM - 22 chars
		//
		// Then the date fields can ram into each other like in the following:
		//     7/6/2004 8:47:35 PM7/26/2004 8:48:51 PM

		// Skip over Aud/Wav, TalkingClock Fields
		curFieldPos=FIELD_SIZE*2;

		int AMPMIdx=0;
		
		StartDate = mOutputDateFormat.format(mInputDateFormat.parse(dataFields.substring(curFieldPos, curFieldPos+22).trim()));
		AMPMIdx = dataFields.indexOf("AM", curFieldPos);
		// Make sure if we found the AM, that is in the appropriate range for the field.
		if ( AMPMIdx < curFieldPos+19-2 || AMPMIdx > curFieldPos+22-2) {
			// Didn't find an appropriate "AM", so look for a "PM" marker
			AMPMIdx = dataFields.indexOf("PM", curFieldPos);
			
			// Validate the value.
			if (AMPMIdx < curFieldPos+19-2 || AMPMIdx > curFieldPos+22-2) {
				// Not good.  Did not find an AM/PM marker.
				throw new ParseException("Unexpected string format.  Failed to find StartDate AM/PM marker in valid range.", curFieldPos);
			}
			
		}
		curFieldPos = AMPMIdx + 2;  // Next field position after the AM/PM specification
		
		EndDate = mOutputDateFormat.format(mInputDateFormat.parse(dataFields.substring(curFieldPos, curFieldPos+22).trim()));
		AMPMIdx = dataFields.indexOf("AM", curFieldPos);
		if ( AMPMIdx < curFieldPos+19-2 || AMPMIdx > curFieldPos+22-2) {
			AMPMIdx = dataFields.indexOf("PM", curFieldPos);
			
			if (AMPMIdx < curFieldPos+19-2 || AMPMIdx > curFieldPos+22-2) {
				// Not good.  Did not find an AM/PM marker.
				throw new ParseException("Unexpected string format.  Failed to find StopDate AM/PM marker in valid range.", curFieldPos);
			}
		}
		curFieldPos = AMPMIdx + 2;  // Next field position after the AM/PM specification
		
		CLSCallID = dataFields.substring(curFieldPos, curFieldPos+FIELD_SIZE).trim();
		curFieldPos += FIELD_SIZE;
		
		Session = dataFields.substring(curFieldPos, curFieldPos+FIELD_SIZE).trim();
		curFieldPos += FIELD_SIZE;

		// Skip Extension or Phone Number
		curFieldPos += FIELD_SIZE*2;
		
		Logger = dataFields.substring(curFieldPos, curFieldPos+FIELD_SIZE).trim();
		curFieldPos += FIELD_SIZE;
		
		// Skip Location Logger
		curFieldPos += FIELD_SIZE;
		
		Channel = dataFields.substring(curFieldPos, curFieldPos+FIELD_SIZE).trim();
		curFieldPos += FIELD_SIZE;

		Server = dataFields.substring(curFieldPos, curFieldPos+FIELD_SIZE).trim();
		curFieldPos += FIELD_SIZE;

		AgentID = dataFields.substring(curFieldPos, curFieldPos+FIELD_SIZE).trim();
		
		// Now let's put the information into the XML
		return writeXML(inLogger, StartDate, EndDate, CLSCallID, Session, Logger, Channel, Server, AgentID);
	}
		
	protected String writeXML(Logger inLogger, String inStartDate, String inEndDate, 
			             String inCLSCallID, String inSession, 
			             String inCallLogger, String inChannel, 
			             String inServer, String inAgentID) {

		OutputStream outputStream = new ByteArrayOutputStream();

		try {
			// Now let's put the information into the XML 

			XMLStreamWriter serializer;

			serializer = XMLOutputFactory.newInstance().createXMLStreamWriter(outputStream, "UTF-8");
			serializer.writeStartDocument("UTF-8", null);
			serializer.writeCharacters("\n");

			// Recording Information
			serializer.writeStartElement("CallRecording");
			serializer.writeAttribute("type", mFileType);
			
			serializer.writeCharacters("\n    ");
			serializer.writeStartElement("Logger");
			serializer.writeCharacters(inCallLogger);
			serializer.writeEndElement();

			if (! (null == inChannel || inChannel.isEmpty()) ) {
				serializer.writeCharacters("\n    ");
				serializer.writeStartElement("Channel");
				serializer.writeCharacters(inChannel);
				serializer.writeEndElement();
			}

			if (! (null == inServer || inServer.isEmpty()) ) {
				serializer.writeCharacters("\n    ");
				serializer.writeStartElement("Server");
				serializer.writeCharacters(inServer);
				serializer.writeEndElement();
			}

			if (! (null == inSession || inSession.isEmpty()) ) {
				serializer.writeCharacters("\n    ");
				serializer.writeStartElement("Session");
				serializer.writeCharacters(inSession);
				serializer.writeEndElement();
			}

			if (! (null == inCLSCallID || inCLSCallID.isEmpty()) ) {
				serializer.writeCharacters("\n    ");
				serializer.writeStartElement("CLSCallID");
				serializer.writeCharacters(inCLSCallID);
				serializer.writeEndElement();
			}

			if (! (null == inAgentID || inAgentID.isEmpty()) ) {
				serializer.writeCharacters("\n    ");
				serializer.writeStartElement("AgentId");
				serializer.writeCharacters(inAgentID);
				serializer.writeEndElement();
			}

			serializer.writeCharacters("\n    ");
			serializer.writeStartElement("DateTime");
			serializer.writeCharacters("\n        ");
			serializer.writeStartElement("Start");
			serializer.writeCharacters(inStartDate);
			serializer.writeEndElement();
			serializer.writeCharacters("\n        ");
			serializer.writeStartElement("End");
			serializer.writeCharacters(inEndDate);
			serializer.writeEndElement();
			
			serializer.writeCharacters("\n    ");
			serializer.writeEndElement();

			serializer.writeCharacters("\n");
			serializer.writeEndElement(); // Call Recording End

			serializer.writeEndDocument();
		}
		catch (Exception e)
		{
			inLogger.fatal("Failed to formulate XML in BasicFileInfoGenerator: " + e.getMessage());
			e.printStackTrace();
		}
		
		return outputStream.toString();
	}
}
