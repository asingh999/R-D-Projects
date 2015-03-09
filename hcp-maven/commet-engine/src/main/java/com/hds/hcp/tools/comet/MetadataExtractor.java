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

import java.io.IOException;
import java.util.LinkedList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hds.hcp.tools.comet.generator.BaseMetadataGenerator;
import com.hds.hcp.tools.comet.generator.ObjectContainer;
import com.hds.hcp.tools.comet.utils.StaticUtils;

public class MetadataExtractor {

	private static Logger logger = LogManager.getLogger();
	
	private CometProperties mProps;
	private boolean isInitialized = false;
	private LinkedList<String> mMetadataGeneratorClasses;
	private LinkedList<BaseMetadataGenerator> mMetadataGeneratorInstances;

	public MetadataExtractor() throws IOException {
		this(new CometProperties());
	}

	public MetadataExtractor(CometProperties inProps) throws IOException {
		mProps = inProps;
		
		mMetadataGeneratorClasses = mProps.getGeneratorClasses();
	}
	
	private void initialize() {
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		if (! isInitialized) {
			if (null == mMetadataGeneratorInstances) {
				mMetadataGeneratorInstances = new LinkedList<BaseMetadataGenerator>();
			} else {
				mMetadataGeneratorInstances.clear();  // Shouldn't be anything in it, but just in case.
			}
			
			/*
			 *  Loop through all the class names, instantiate an instance, and call the initialization method on them.
			 */
			for (int i = 0; i < mMetadataGeneratorClasses.size(); i++) {
				String currentClass = mMetadataGeneratorClasses.get(i);
				
				try {
					logger.info("Initializing metadata generator class: {}", currentClass);
					
					@SuppressWarnings("unchecked")
					Class<BaseMetadataGenerator> theClass = (Class<BaseMetadataGenerator>) Class.forName(currentClass);

					BaseMetadataGenerator thisInstance = (BaseMetadataGenerator)theClass.newInstance();

					thisInstance.initialize(); 
					
					// Now that we successfully initialized the instance, add it to the list.
					mMetadataGeneratorInstances.add(thisInstance);
				} 
				catch (Exception e) {
					// Some possible exceptions are: 
					//  ClassNotFoundException | IllegalAccessException | InstantiationException | ClassCastException
					logger.warn("Unable to construct class: " + currentClass, e);
				}
			}
			
			isInitialized = true;
		}
		
		StaticUtils.TRACE_METHOD_EXIT(logger);
	}
	
    public LinkedList<ObjectContainer> getMetadataList(BaseWorkItem inItem) {
		StaticUtils.TRACE_METHOD_ENTER(logger);

		LinkedList<ObjectContainer> retVal = null;
			
		logger.info("Requesting Metadata Generation for {}", inItem.getName());
		
		// Call initialize just in case this is the first time being called.
		initialize();

		BaseMetadataGenerator metadataGeneratorInstance = null;
		
		/**
		 * Now we are going to call each configured class in order until one of them 
		 *   returns back some metadata or the end of the list has been reached.
		 */
		for (int i = 0; i < mMetadataGeneratorInstances.size(); i++) {
			metadataGeneratorInstance = mMetadataGeneratorInstances.get(i);
			
			String currentClass = metadataGeneratorInstance.getClass().getName();
			
			logger.debug("Calling class for system metadata: {}", currentClass);
			
			retVal = metadataGeneratorInstance.getMetadataList(inItem);
			
			// If we managed to get back some metadata, then break out of the loop and return it.
			if (null != retVal) {
				logger.debug("Metadata Generated using class: {}", currentClass);
				
				break;
			}
			
			logger.debug("Metadata not returned by class: {}", currentClass);
		}

		
		if (null == retVal || retVal.isEmpty()) {
			logger.warn("No Custom Metadata Generated for: {}", inItem.getName());
			
			metadataGeneratorInstance = null;
		}
			
		StaticUtils.TRACE_METHOD_EXIT(logger);
		
		return retVal;
	}
}
