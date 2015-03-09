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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hds.hcp.apihelpers.HCPUtils;
import com.hds.hcp.tools.comet.generator.BaseGeneratorProperties;
import com.hds.hcp.tools.comet.generator.CustomMetadataContainer;
import com.hds.hcp.tools.comet.utils.StaticUtils;

public class BaseGeneratorProperties {
	
	static final File DEFAULT_FILE = new File("generator.properties");
	static final String DEFAULT_FILENAME_PROPERTY = "com.hds.hcp.tools.comet.generator.properties.file";

	private File mPropertiesFile = DEFAULT_FILE;
	protected Properties mProps;
	
	private static Logger logger = LogManager.getLogger();

	private String sEncodedUserName = null;
	private String sEncodedPassword = null;
	
	public BaseGeneratorProperties() {
		String propFile = System.getProperty(DEFAULT_FILENAME_PROPERTY);
		
		// If we got something from the environment, use it.
		if (null != propFile && 0 < propFile.length()) {
			mPropertiesFile = new File(propFile);
		}

		refresh();
	}
	
	public BaseGeneratorProperties(File inFile) {
		mPropertiesFile = inFile;
		
		refresh();
	}
	
	public BaseGeneratorProperties(String inFileNameProperty) {
		String propFile = System.getProperty(inFileNameProperty);
		
		// If we got something from the environment, use it.
		if (null != propFile && 0 < propFile.length()) {
			mPropertiesFile = new File(propFile);
		}
		
		refresh();
	}
	
	public void refresh() {
		StaticUtils.TRACE_METHOD_ENTER(logger);

		if (null == mPropertiesFile) {

			StaticUtils.TRACE_METHOD_EXIT(logger, "No Property file");
			return;  // Don't have a file so do nothing.
		}
		
		if ( ! mPropertiesFile.exists() || ! mPropertiesFile.isFile() || ! mPropertiesFile.canRead() ) {
			logger.warn("Property file ({}) is not an existing readable regular file.", mPropertiesFile.getPath());
			return;
		}

		mProps = new Properties();

		try {
			mProps.load(new FileInputStream(mPropertiesFile));
			
			// Compute the encoded user/pwd values at initialization/refresh time
			//  for efficiency reasons. (if the top level is provided)
			if (isDestinationPasswordEncoded()) {
				sEncodedPassword = getDestinationPassword();
			} else {
				try {
					sEncodedPassword = HCPUtils.toMD5Digest(getDestinationPassword());
				} catch (Exception e) {
					logger.warn("WARNING: Failed to encode destination password.  Will be followed by failure to authenticate.");
					sEncodedPassword = "";
				}
			}

			sEncodedUserName = HCPUtils.toBase64Encoding(getDestinationUserName());
			
		} catch (IOException e) {
			logger.fatal("Failed to read properties file ({}). Reason: \"{}\"", mPropertiesFile.getPath(), e.getMessage());
		}

		StaticUtils.TRACE_METHOD_EXIT(logger);
	}
	
	/***
	 * 
	 * DESTINATION PROPERTIES
	 * 
	 ***/
	public String getDestinationUserName() {
		return StaticUtils.resolveEnvVars(mProps.getProperty("destination.user"));
	}

	public String getEncodedDestinationUserName() {
		return sEncodedUserName;
	}
	
	public String getDestinationPassword() {
		return StaticUtils.resolveEnvVars(mProps.getProperty("destination.password"));
	}
	
	public String getEncodedDestinationPassword() {
		return sEncodedPassword;
	}
	
	public Boolean isDestinationPasswordEncoded() {
		return new Boolean(mProps.getProperty("destination.passwordEncoded", "false"));
	}

	public String getDestinationRootPath() {
		return StaticUtils.resolveEnvVars(mProps.getProperty("destination.rootPath")).trim();
	}
	
	public String getAnnotationName() {
		return StaticUtils.resolveEnvVars(mProps.getProperty("destination.annotationName", CustomMetadataContainer.DEFAULT_ANNOTATION));
	}

	public Boolean shouldAppendSourcePathTrailingFolder() {
		return new Boolean(mProps.getProperty("destination.appendSourcePathTrailingFolder", "false"));
	}

	public Boolean shouldDeleteExistingCustomMetadataOnEmpty() {
		return new Boolean(mProps.getProperty("destination.deleteExistingMetadataOnEmpty", "false"));
	}
	
	public Boolean shouldUpdateCustomMetadata() {
		return new Boolean(mProps.getProperty("destination.updateCustomMetadata", "false"));
	}
	
	public Boolean shouldUpdateSystemMetadata() {
		return new Boolean(mProps.getProperty("destination.updateSystemMetadata", "false"));
	}
}
