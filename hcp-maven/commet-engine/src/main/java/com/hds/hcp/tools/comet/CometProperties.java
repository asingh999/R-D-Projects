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
package com.hds.hcp.tools.comet;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Properties;

import com.hds.hcp.apihelpers.HCPUtils;
import com.hds.hcp.tools.comet.utils.StaticUtils;

public class CometProperties {
	
	static final String DEFAULT_PROPERTIES_FILE = "comet.properties";

	private String mPropertiesFilename = DEFAULT_PROPERTIES_FILE;
	private Properties mProps;
	
	private String sEncodedUserName, sEncodedPassword;
	
	public CometProperties() throws IOException {
		String propFile = System.getProperty("com.hds.hcp.tools.comet.properties.file");
		
		// If we got something from the environment, use it.
		if (null != propFile && 0 < propFile.length()) {
			mPropertiesFilename = propFile;
		}

		refresh();
	}
	
	public CometProperties(String inPropertiesFile) throws IOException {
		mPropertiesFilename = inPropertiesFile;
		
		refresh();
	}
	
	void refresh() throws IOException {
		mProps = new Properties();

		mProps.load(new FileInputStream(mPropertiesFilename));

		// Compute the encoded user/pwd values at initialization/refresh time
		//  for efficiency reasons.
		if (isDestinationPasswordEncoded()) {
			sEncodedPassword = getDestinationPassword();
		} else {
			try {
				sEncodedPassword = HCPUtils.toMD5Digest(getDestinationPassword());
			} catch (Exception e) {
				sEncodedPassword = "";
			}
		}
		
		sEncodedUserName = HCPUtils.toBase64Encoding(getDestinationUserName());
	}
	
	/***
	 * 
	 * SOURCE CONTENT PROPERTIES
	 * 
	 ***/
	public String getSourceClassName() {
		return StaticUtils.resolveEnvVars(mProps.getProperty("source.class"));
	}
	
	public LinkedList<String> getSourceItems() {
		LinkedList<String> retVal = new LinkedList<String>();
		
		String propValue = StaticUtils.resolveEnvVars(mProps.getProperty("source.items", ""));
		
		if ( ! propValue.isEmpty() ) {
			String[] list = propValue.split(",");

			for (int i = 0; i < list.length; i++) {
				if ( ! list[i].isEmpty()) {
					retVal.addLast(list[i]);
				}
			}
		}

		return retVal;
	}
	
	public LinkedList<String> getSourceStartTriggerItems() {
		LinkedList<String> retVal = new LinkedList<String>();
		
		String propValue = StaticUtils.resolveEnvVars(mProps.getProperty("source.startTriggerItems", ""));
		
		if ( ! propValue.isEmpty() ) {
			String[] list = propValue.split(",");

			for (int i = 0; i < list.length; i++) {
				String element = list[i].trim();

				if ( ! element.isEmpty())
					retVal.addLast(element);
				else
					retVal.addLast(null); // Need a place holder.
			}
		}
		
		return retVal;
	}
	
	/***
	 * 
	 * DESTINATION PROPERTIES (Used only to determine the HCP version to use
	 *      advanced features)
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
		return StaticUtils.resolveEnvVars(mProps.getProperty("destination.rootPath"));
	}
	
	/***
	 * 
	 * METADATA GENERATION MODULE PROPERTIES
	 * 
	 ***/
	
	public LinkedList<String> getGeneratorClasses() {
		LinkedList<String> retVal = new LinkedList<String>();
		String[] list = StaticUtils.resolveEnvVars(mProps.getProperty("generator.classes")).split(",");

		for (int i = 0; i < list.length; i++) {
			if ( ! list[i].isEmpty())
				retVal.addLast(list[i]);
		}

		return retVal;
	}

	/***
	 * 
	 * EXECUTION BEHAVIOR PROPERTIES
	 * 
	 ***/
	public static final Integer LOOPCOUNT_INFINITE = -1;
	
	/**
	 **  General Execution Parameters
	 **/
	public Integer getLoopCount() {
		return new Integer(mProps.getProperty("execution.loopCount", "-1"));
	}
	
	public Boolean isInfiniteLoopCount() {
		return new Boolean(getLoopCount().equals(LOOPCOUNT_INFINITE));
	}
	
	public static Boolean isInfiniteLoopCount(int inLoopCount) {
		return new Boolean(inLoopCount == LOOPCOUNT_INFINITE);
	}
	
	public Integer getLoopSleepTime() {
		return new Integer(mProps.getProperty("execution.loopSleepInSeconds", "60"));
	}
	
	public String getStopFileName() {
		return new String(StaticUtils.resolveEnvVars(mProps.getProperty("execution.stopRequestFile", "comet.stop")));
	}
	
	public String getPauseFileName() {
		return new String(StaticUtils.resolveEnvVars(mProps.getProperty("execution.pauseRequestFile", "comet.pause")));
	}

	public Integer getPauseSleepTime() {
		return new Integer(mProps.getProperty("execution.pauseSleepInSeconds", "5"));
	}
	
	public Integer getCompletionPollSleep() {
		return new Integer(mProps.getProperty("execution.completionPollSleep", "500"));
	}

	// Number of items to process before reporting on updated statistics count.
	// [Default: 1000]
	public Integer getStatsReportingThreshold() {
		return new Integer(mProps.getProperty("execution.statsReportingThreshold", "1000"));
	}

	public Boolean shouldDumpHTTPHeaders() {
		return new Boolean(mProps.getProperty("execution.debugging.httpheaders", "false"));
	}

	/**
	 **  Scanner Execution Parameters
	 **/
	public String getScannerClass() {
		return mProps.getProperty("scanner.class");
	}
	
	public Integer getScannerMaxThreadCount() {
		return new Integer(mProps.getProperty("execution.scanner.maxThreadCount", "5"));
	}
	
	public Integer getScannerTaskQueueSize() {
		return new Integer(mProps.getProperty("execution.scanner.taskQueueSize", "100"));
	}
	
	public Boolean shouldScannerDeleteSourceItemsOnSuccess() {
		return new Boolean(mProps.getProperty("execution.scanner.deleteSourceItemsOnSuccess", "false"));
	}
	
	public Boolean shouldScannerDeleteSourceItemsOnNotAttempted() {
		return new Boolean(mProps.getProperty("execution.scanner.deleteSourceItemsOnNotAttempted", "false"));
	}
	
	public Integer getScannerMaxSubmitWaitTimeInSeconds() {
		return new Integer(mProps.getProperty("execution.scanner.maxSubmitWaitTimeInSeconds", "2"));
	}

	public Integer getScannerCompletionPollSleep() {
		return new Integer(mProps.getProperty("execution.scanner.completionPollSleep", "500"));
	}

	public Integer getScannerCheckCompleteQueueThreshold() {
		return new Integer(mProps.getProperty("execution.scanner.checkCompleteQueueThreshold", "50"));
	}
	
	public Integer getScannerCompletionProcessingThreshold() {
		return new Integer(mProps.getProperty("execution.scanner.completionProcessingThreashold", "100"));
	}

	public Integer getScannerThreadMaxJoinWait() {
		return new Integer(mProps.getProperty("execution.scanner.maxJoinWaitInSeconds", "20"));
	}

	public Integer getScannerLoadTestingThreadSleep() {
		return new Integer(mProps.getProperty("execution.scanner.loadTesting.threadSleep", "0"));
	}


	/**
	 **  Processor Execution Parameters
	 **/
	public String getProcessorClass() {
		return mProps.getProperty("processor.class");
	}
	
	public Integer getProcessorThreadCount() {
		return new Integer(mProps.getProperty("execution.processor.threadCount", "25"));
	}
	
	public Integer getProcessorTaskQueueSize() {
		return new Integer(mProps.getProperty("execution.processor.taskQueueSize", "1000"));
	}
	
	public Integer getProcessorThreadMaxJoinWait() {
		return new Integer(mProps.getProperty("execution.processor.maxJoinWaitInSeconds", "20"));
	}

	public Integer getProcessorLoadTestingThreadSleep() {
		return new Integer(mProps.getProperty("execution.processor.loadTesting.threadSleep", "0"));
	}
	
	public Boolean getProcessorValidateHash() {
		return new Boolean(mProps.getProperty("execution.processor.validateHash", "false"));
	}
	
	public Integer getHttpClientConnectionTimeout() {
		return new Integer(mProps.getProperty("execution.httpclient.connectionTimeout", HCPUtils.DEFAULT_CONNECTION_TIMEOUT.toString()));
	}

	public Integer getHttpClientMaxConnections() {
		return new Integer(mProps.getProperty("execution.httpclient.maxConnections", HCPUtils.DEFAULT_MAX_CONNECTIONS.toString()));
	}

	public Integer getHttpClientMaxConnectionsPerRoute() {
		return new Integer(mProps.getProperty("execution.httpclient.maxConnectionsPerRoute", HCPUtils.DEFAULT_MAX_CONNECTIONS_PER_ROUTE.toString()));
	}
}
