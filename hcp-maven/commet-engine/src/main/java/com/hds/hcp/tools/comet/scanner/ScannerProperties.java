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
package com.hds.hcp.tools.comet.scanner;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Properties;

import com.hds.hcp.tools.comet.utils.StaticUtils;

public class ScannerProperties {
	
	static final String DEFAULT_PROPERTIES_FILE = "scanner.properties";

	private String mPropertiesFilename = DEFAULT_PROPERTIES_FILE;
	protected Properties mProps;
	
	public ScannerProperties() throws IOException {
		String propFile = System.getProperty("com.hds.hcp.tools.comet.scanner.properties.file");
		
		// If we got something from the environment, use it.
		if (null != propFile && 0 < propFile.length()) {
			mPropertiesFilename = propFile;
		}

		refresh();
	}
	
	public ScannerProperties(String inPropertiesFile) throws IOException {
		mPropertiesFilename = inPropertiesFile;
		
		refresh();
	}
	
	public void refresh() throws IOException {
		mProps = new Properties();

		mProps.load(new FileInputStream(mPropertiesFilename));
	}
	
	/***
	 * 
	 * SOURCE CONTENT PROPERTIES
	 * 
	 ***/
	
	public LinkedList<String> getSourceInclusionList() {
		LinkedList<String> retVal = new LinkedList<String>();
		String[] list = StaticUtils.resolveEnvVars(mProps.getProperty("source.itemInclusion", "")).split(",");

		for (int i = 0; i < list.length; i++) {
			if ( ! list[i].isEmpty()) {
				retVal.addLast(StaticUtils.convertFilePatternToRegExpr(list[i]));
			}
		}

		return retVal;
	}

	public LinkedList<String> getSourceExclusionList() {
		LinkedList<String> retVal = new LinkedList<String>();
		String[] list = StaticUtils.resolveEnvVars(mProps.getProperty("source.itemExclusion", "")).split(",");

		for (int i = 0; i < list.length; i++) {
			if ( ! list[i].isEmpty()) {
				retVal.addLast(StaticUtils.convertFilePatternToRegExpr(list[i]));
			}
		}

		return retVal;
	}
	
	public LinkedList<String> getSourceWriteLastItemList() {
		LinkedList<String> retVal = new LinkedList<String>();
		String[] list = StaticUtils.resolveEnvVars(mProps.getProperty("source.writeLastItemList", "")).split(",");

		for (int i = 0; i < list.length; i++) {
			if ( ! list[i].isEmpty()) {
				retVal.addLast(StaticUtils.convertFilePatternToRegExpr(list[i]));
			}
		}

		return retVal;
	}
	
	public Boolean shouldSortItems() {
		return new Boolean(StaticUtils.resolveEnvVars(mProps.getProperty("source.sortItems",  "true")));
	}

	public Boolean shouldSortContainers() {
		return new Boolean(StaticUtils.resolveEnvVars(mProps.getProperty("source.sortContainers",  "true")));
	}

	public Boolean shouldDeleteSourceItems() {
		return new Boolean(mProps.getProperty("execution.deleteSourceItems", "false"));
	}
	
	public Boolean shouldForceDeleteSourceItems() {
		return new Boolean(mProps.getProperty("execution.forceDeleteSourceItems", "false"));
	}
	
	public Boolean shouldDeleteSourceEmptyContainers() {
		return new Boolean(mProps.getProperty("execution.deleteSourceEmptyContainers", "false"));
	}
	
}
