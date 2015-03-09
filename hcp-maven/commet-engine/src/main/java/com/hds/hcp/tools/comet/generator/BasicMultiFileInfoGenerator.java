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

import com.hds.hcp.apihelpers.HCPUtils;
import com.hds.hcp.tools.comet.BaseWorkItem;
import com.hds.hcp.tools.comet.generator.BaseGeneratorProperties;
import com.hds.hcp.tools.comet.generator.BaseMetadataGenerator;
import com.hds.hcp.tools.comet.generator.BasicMultiFileInfoGenerator;
import com.hds.hcp.tools.comet.generator.ObjectContainer;
import com.hds.hcp.tools.comet.generator.SystemMetadataContainer;
import com.hds.hcp.tools.comet.utils.StaticUtils;
import com.hds.hcp.tools.comet.utils.URIWrapper;

public class BasicMultiFileInfoGenerator extends BaseMetadataGenerator {

	private static File PROPERTIES_FILE = new File("basemultifileinfo.properties");
	
	private static Logger logger = LogManager.getLogger();
	
	/*
	 * Construct a private properties class to construct module specific information.
	 */
	private class BasicMultiFileInfoProperties extends BaseGeneratorProperties {
		public BasicMultiFileInfoProperties() {
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
		
		public String getDestUserName(int id) {
			return mProps.getProperty("dest" + id + ".user");
		}

		public String getDestEncodedUserName(int id) {
			return HCPUtils.toBase64Encoding(mProps.getProperty("dest" + id + ".user"));
		}

		public String getDestPassword(int id) {
			return mProps.getProperty("dest" + id + ".password");
		}
		
		public String getDestEncodedPassword(int id) {
			if (isDestPasswordEncoded(id)) {
				return getDestPassword(id);
			} else {
				try {
					return HCPUtils.toMD5Digest(getDestPassword(id));
				} catch (Exception e) {
					logger.warn("WARNING: Failed to encode destination password.  Will be followed by failure to authenticate.");
					return "";
				}
			}
		}
		
		public Boolean isDestPasswordEncoded(int id) {
			return new Boolean(mProps.getProperty("dest" + id + ".passwordEncoded", "false"));
		}

		public String getDestRootPath(int id) {
			return mProps.getProperty("dest" + id + ".rootPath");
		}
	}
	
	BasicMultiFileInfoProperties mProps = new BasicMultiFileInfoProperties();

	public void initialize() { return; }
	
	private ObjectContainer constructBaseObject(File inBaseFolder, File inSrcFile, int inObjectID) {
		StaticUtils.TRACE_METHOD_ENTER(logger);

		ObjectContainer retObject = null;

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
		
		try {
			// Construct the destination root string paying attention as to whether we need to add
			//   an HTTP Separator or not.
			String destinationRoot = mProps.getDestRootPath(inObjectID);
			String objectPath = destinationRoot 
					+ (destinationRoot.lastIndexOf(StaticUtils.HTTP_SEPARATOR) != ( destinationRoot.length() - 1 ) ? StaticUtils.HTTP_SEPARATOR : "")
					+ destFilePath.toString();
			retObject = new ObjectContainer(new URIWrapper(objectPath), mProps);
		} catch(URISyntaxException e) {
			logger.warn("URL formulation issue (Skipping): \"" + e.getMessage());
	
			// TODO:  Need something here...  Probably need to think about returning an exception???
			retObject = null;
		}
		
		StaticUtils.TRACE_METHOD_EXIT(logger);
		return retObject;
	}

	public LinkedList<ObjectContainer> getMetadataList(BaseWorkItem inItem) {
		StaticUtils.TRACE_METHOD_ENTER(logger);

		LinkedList<ObjectContainer> retval = new LinkedList<ObjectContainer>();
		
		int objectID;
		OutputStream outputStream;
		ObjectContainer retObject;
		SystemMetadataContainer sysMeta;

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
		
		File inSrcFile = (File)inItem.getHandle();
		
		/****
		 **** Build the First object information
		 ****/
		objectID = 1;
		
		retObject = constructBaseObject((File)inItem.getBaseSpecification(), inSrcFile, objectID);
		if (null == retObject) {
			logger.fatal("constructBaseObject did not return an object for first object");

			// TODO: Need something here... Probably need to think about returning an exception???
			StaticUtils.TRACE_METHOD_EXIT(logger);
			return null;
		}

		/*
		 * Set the core system metadata for the object.
		 */
		sysMeta = retObject.getSystemMetadata();
		
		// Need the credentials from properties file.
		sysMeta.setCredentials(mProps.getDestEncodedUserName(objectID),  mProps.getDestEncodedPassword(objectID));
		
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
		outputStream = new ByteArrayOutputStream();

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
			serializer.writeCharacters("\n    ");
			serializer.writeStartElement("Owner");
			serializer.writeCharacters(mProps.getDestUserName(objectID));
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

		// Put the custom metadata into the configured annotation on the object.
		retObject.getCustomMetadata().put(mProps.getAnnotationName(), outputStream.toString());
		
		// Put the base object into the linked list to be returned.
		retval.add(retObject);
		
		/****
		 **** Build the Second object information
		 ****/
		objectID = 2;

		retObject = constructBaseObject((File)inItem.getBaseSpecification(), inSrcFile, objectID);
		if (null == retObject) {
			logger.fatal("constructBaseObject did not return an object for second object");
			
			// TODO: Need something here... Probably need to think about returning an exception???
			StaticUtils.TRACE_METHOD_EXIT(logger);
			return null;
		}

		/*
		 * Set the core system metadata for the object.
		 */
		sysMeta = retObject.getSystemMetadata();
		
		// Need the credentials from properties file.
		sysMeta.setCredentials(mProps.getDestEncodedUserName(objectID),  mProps.getDestEncodedPassword(objectID));
		

		/**
		 * Now construct the custom metadata for this object.
		 */
		outputStream = new ByteArrayOutputStream();

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
			serializer.writeStartElement("Owner");
			serializer.writeCharacters(mProps.getDestUserName(objectID));
			serializer.writeEndElement();
			serializer.writeEndElement(); // SourceFileInfo
			serializer.writeCharacters("\n");
			serializer.writeEndDocument();
		}
		catch (Exception e)
		{
			logger.fatal("Failed to formulate XML in BasicFileInfoGenerator.", e);
			e.printStackTrace();
			
			StaticUtils.TRACE_METHOD_EXIT(logger);
			return null;
		}

		// Put the custom metadata into the configured annotation on the object.
		retObject.getCustomMetadata().put(mProps.getAnnotationName(), outputStream.toString());
		
		// Put the base object into the linked list to be returned.
		retval.add(retObject);
		
		StaticUtils.TRACE_METHOD_EXIT(logger);
		return retval;
	}
}
