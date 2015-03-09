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

import java.text.SimpleDateFormat;
import java.util.LinkedList;

import com.hds.hcp.tools.comet.BaseWorkItem;


// This class is the base class for all customized modules for extracting metadata from the file passed in.
//
//  How these methods are used...
//
//  The initialize() method is called once per initiation of COMET program for each module configured in
//    the comet.properties file in the order configured.
//
//  For every file COMET encounters, it first tries to obtain System Metadata then Custom Metadata using the
//     following method.
//
//  To Obtain System Metadata, this is accomplished by calling getSystemMetadata for each configured module in the
//     order configured.  Once one module returns system metadata, COMET stops calling getSystemMetadata
//     for any remaining modules.
//
//  Once the System Metadata generation attempt is finished, COMET will next collect Custom Metadata
//     by calling the getCustomMetadata method of the module that provided the Custom Metadata.  If that
//     module does not return any System Metadata, it will then call getCustomMetadata for each configured
//     module in the order configured. Once one module returns Custom Metadata, COMET stops calling
//     getCustomMetadata for any remaining modules.

public abstract class BaseMetadataGenerator {
	
	// This date formatter might be helfpul for any deriving classes to format a retention date
	//   on HCP and requires the correct format.
	protected SimpleDateFormat mHCPRetentionDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

	public abstract void initialize() throws Exception;

	public abstract LinkedList<ObjectContainer> getMetadataList(BaseWorkItem inItem);
}
