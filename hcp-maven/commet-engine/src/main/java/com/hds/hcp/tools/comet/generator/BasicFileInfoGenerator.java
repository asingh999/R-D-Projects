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
package com.hds.hcp.tools.comet.generator;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import com.hds.hcp.tools.comet.BaseWorkItem;
import com.hds.hcp.tools.comet.utils.StaticUtils;
import com.hds.hcp.tools.comet.utils.URIWrapper;

public class BasicFileInfoGenerator extends BaseMetadataGenerator {

	private static File PROPERTIES_FILE = new File("basefileinfo.properties");
	
	private static Logger logger = LogManager.getLogger();
	
	/*
	 * Construct a private properties class to construct module specific information.
	 */
	private class BasicFileInfoProperties extends BaseGeneratorProperties {
		public BasicFileInfoProperties() {
			super(PROPERTIES_FILE);
		}

		// Define private property file values.
		public String getDateFormat() {
			return mProps.getProperty("metadata.dateFormat", "yyyy/MM/dd HH:mm:ssZ");
		}
		
		public String getShredFilePattern() {
			return StaticUtils.convertFilePatternToRegExpr(mProps.getProperty("metadata.shredFilePattern", ""));
		}
		
		public String getRetentionFilePattern() {
			return StaticUtils.convertFilePatternToRegExpr(mProps.getProperty("metadata.retentionFilePattern", ""));
		}
		
		public String getRetentionValue() {
			return mProps.getProperty("metadata.retentionValue", "");
		}
	}
	
	BasicFileInfoProperties mProps = new BasicFileInfoProperties();

	public void initialize() { return; }
	
	public LinkedList<ObjectContainer> getMetadataList(BaseWorkItem inItem) {
		StaticUtils.TRACE_METHOD_ENTER(logger);

		LinkedList<ObjectContainer> retval = new LinkedList<ObjectContainer>();
		
		/**
		 *  Build the base object metadata
		 */

		if ( ! (inItem.getBaseSpecification() instanceof File) ) {
			logger.fatal("BaseWorkItem.getBaseSpecification() is not a java.io.File");
			// TODO Probably should see about throwing an exception.
			return null;
		}
		
		if ( ! (inItem.getHandle() instanceof File) ) {
			logger.fatal("BaseWorkItem.getHandle() is not a java.io.File");
			// TODO Probably should see about throwing an exception.
			return null;
		}
		
		File inSrcFile = (File)(inItem.getHandle());
		File inBaseFolder = (File)(inItem.getBaseSpecification());
		
		
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
			String destinationRoot = mProps.getDestinationRootPath();
			String objectPath = destinationRoot 
					+ (destinationRoot.lastIndexOf(StaticUtils.HTTP_SEPARATOR) != ( destinationRoot.length() - 1 ) ? StaticUtils.HTTP_SEPARATOR : "")
					+ destFilePath.toString();
			retObject = new ObjectContainer(new URIWrapper(objectPath), mProps);
		} catch(URISyntaxException e) {
			logger.warn("URL formulation issue (Skipping).", e);

			// TODO:  Need something here...  Probably need to think about returning an exception???
			StaticUtils.TRACE_METHOD_EXIT(logger);
			return null;
		}

		/*
		 * Set the core system metadata for the object.
		 */
		SystemMetadataContainer sysMeta = retObject.getSystemMetadata();
		
		// Need the credentials from properties file.
		sysMeta.setCredentials(mProps.getEncodedDestinationUserName(),  mProps.getEncodedDestinationPassword());
		
		if (inSrcFile.getName().matches(mProps.getShredFilePattern())) {
			sysMeta.setShredding(Boolean.TRUE);
		}
		
		if (inSrcFile.getName().matches(mProps.getRetentionFilePattern())) {
			String retentionValue = mProps.getRetentionValue();
			
			if ( ! retentionValue.isEmpty()) {
				sysMeta.setRetention(mProps.getRetentionValue());
			}
		}
		
		/**
		 * Now construct the custom metadata for this object.
		 */
		OutputStream outputStream = new ByteArrayOutputStream();

		// NOTE:  If this module were conditionally processing files, there should be the same conditional
		//    in both this method and the getSystemMetadata method.

		try {
			XMLStreamWriter serializer;

			serializer = XMLOutputFactory.newInstance().createXMLStreamWriter(outputStream, "UTF-8");
			serializer.writeStartDocument("UTF-8", null);
			serializer.writeCharacters("\n");
			serializer.writeStartElement("SourceFileInfo");
			serializer.writeCharacters("\n    ");
			serializer.writeStartElement("Path");
			serializer.writeCharacters(inSrcFile.getCanonicalPath());
			serializer.writeEndElement();
			serializer.writeCharacters("\n    ");
			serializer.writeStartElement("Size");
			serializer.writeCharacters(String.valueOf(inSrcFile.length()));
			serializer.writeEndElement();
			serializer.writeCharacters("\n    ");
			serializer.writeEmptyElement("ModificationDate");
			serializer.writeAttribute("EpochTime", String.valueOf(inSrcFile.lastModified()));
			serializer.writeAttribute("ISO8601Time", "Put-ISO-Time-Here");
			serializer.writeCharacters("\n    ");
			serializer.writeStartElement("MetaDataWriteTime");
			serializer.writeCharacters(new SimpleDateFormat(mProps.getDateFormat()).format(Calendar.getInstance().getTime()));
			serializer.writeEndElement();
			serializer.writeCharacters("\n    ");
			serializer.writeStartElement("ShreddingCandidate");
			serializer.writeCharacters((inSrcFile.getName().matches(mProps.getShredFilePattern()) ? Boolean.TRUE : Boolean.FALSE).toString());
			serializer.writeEndElement();
			serializer.writeEndElement(); // SourceFileInfo
			serializer.writeCharacters("\n");
			serializer.writeEndDocument();
		}
		catch (Exception e)
		{
			logger.fatal("Failed to formulate XML in BasicFileInfoGenerator.", e);
			
			StaticUtils.TRACE_METHOD_EXIT(logger);
			return null;
		}

		// Put the custom metadata into the default annotation on the object.
		retObject.getCustomMetadata().put(outputStream.toString());
		
		// Put the base object into the linked list to be returned.
		retval.add(retObject);
		
		StaticUtils.TRACE_METHOD_EXIT(logger);
		return retval;
	}
}
