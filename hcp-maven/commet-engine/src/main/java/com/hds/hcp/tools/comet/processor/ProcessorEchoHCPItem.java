package com.hds.hcp.tools.comet.processor;

import java.io.IOException;
import java.util.LinkedList;
import java.util.ListIterator;

import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hds.hcp.tools.comet.BaseWorkItem;
import com.hds.hcp.tools.comet.CometProperties;
import com.hds.hcp.tools.comet.MetadataExtractor;
import com.hds.hcp.tools.comet.WorkItemStatus.WriteStatus;
import com.hds.hcp.tools.comet.generator.ObjectContainer;
import com.hds.hcp.tools.comet.generator.SystemMetadataContainer;
import com.hds.hcp.tools.comet.utils.StaticUtils;
import com.hds.hcp.tools.comet.HCPObjectItem;

  
public class ProcessorEchoHCPItem extends BaseItemProcessor {

	private static Logger logger = LogManager.getLogger();
	
	private MetadataExtractor mMetadataGenerator;
	
	/**
	 * Initialize the object by setting up internal data and establishing the HTTP client connection.
	 * 
	 * This routine is called by the ReadFromHCP and WriteToHCP routines, so calling it by the
	 * consumer of this class is unnecessary.
	 * @throws IOException 
	 *
	 */
	public void initialize(CometProperties inProps, Object inInitBlob) throws ClassCastException, IOException {
		StaticUtils.TRACE_METHOD_ENTER(logger);

		if (! bIsInitialized)  // Only initialize if we haven't already
		{
			mProps = inProps;
			
			if ( ! (inInitBlob instanceof DefaultHttpClient)) {
				throw new java.lang.ClassCastException("Invalid object type for input parameter inInitBlob");
			}
			
			mMetadataGenerator = new MetadataExtractor();
			
			bIsInitialized = true;
		}

		StaticUtils.TRACE_METHOD_EXIT(logger);
	}

	public boolean process(BaseWorkItem inElement) {
		StaticUtils.TRACE_METHOD_ENTER(logger);

		if ( ! bIsInitialized ) {
			logger.fatal("Programming Error. Object Not Initialized");

			StaticUtils.TRACE_METHOD_EXIT(logger);
			return false;
		}
		
		if ( null == inElement) {
			logger.fatal("Invalid input parameter.  inElement is null");

			StaticUtils.TRACE_METHOD_EXIT(logger);
			return false;
		}
		
		if ( null == inElement.getHandle()) {
			logger.fatal("Invalid input parameter.  inElement.getHandle() is null");

			StaticUtils.TRACE_METHOD_EXIT(logger);
			return false;
		}

		logger.info("Processing Item:" + inElement.getName());
		
		mStatus.setObjectStatus(WriteStatus.WRITE_SUCCESS);
		mStatus.setCustomMetadataStatus(WriteStatus.WRITE_SUCCESS);

		//
		//  From here on out, we are working with a HCPItem for this module.
		//
		
		
		if ( ! ( inElement instanceof HCPObjectItem)) {
			logger.fatal("Unexpected calls type.  Expected {} for item handle. Received {}", HCPObjectItem.class.getName(), inElement.getClass().getName());

			StaticUtils.TRACE_METHOD_EXIT(logger);
			return false;
		}
		HCPObjectItem oneFile = (HCPObjectItem)inElement;
		
		try {
			if ( ! oneFile.exists()) {
				logger.error("HCPItem does not exist: {} (Skipping)", oneFile.getName());

				StaticUtils.TRACE_METHOD_EXIT(logger);
				return false;
			}
		} catch (Exception e) {
			logger.error("Unexpected exception attempting to delete item: {} (Skipping)", oneFile.getName());
			
			StaticUtils.TRACE_METHOD_EXIT(logger);
			return false;
		}

		LinkedList<ObjectContainer> objectMetadataList = mMetadataGenerator.getMetadataList(inElement);

		/*
		 * If we didn't get any, just exit now.
		 */
		if (null == objectMetadataList) {
			logger.info("No metadata returned for object. Skipping...");

			StaticUtils.TRACE_METHOD_EXIT(logger);
			return Boolean.FALSE;
		}

		/*
		 * Loop through all the object metadata provided and process each
		 * one.
		 */

		ListIterator<ObjectContainer> iter = objectMetadataList
				.listIterator();
		while (iter.hasNext()) {
			ObjectContainer currentItem = iter.next();

			SystemMetadataContainer objSystemMetadata = currentItem.getSystemMetadata();

			logger.info("Contains Retention Setting: {}", objSystemMetadata.getRetention());
		}

		StaticUtils.TRACE_METHOD_EXIT(logger);
		return true;
	}

}
