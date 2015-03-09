package com.hds.hcp.tools.comet.processor;

import java.io.File;

import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hds.hcp.tools.comet.BaseWorkItem;
import com.hds.hcp.tools.comet.CometProperties;
import com.hds.hcp.tools.comet.WorkItemStatus.WriteStatus;
import com.hds.hcp.tools.comet.utils.StaticUtils;

  
public class ProcessorEchoFile extends BaseItemProcessor {

	private static Logger logger = LogManager.getLogger();
	
	/**
	 * Initialize the object by setting up internal data and establishing the HTTP client connection.
	 * 
	 * This routine is called by the ReadFromHCP and WriteToHCP routines, so calling it by the
	 * consumer of this class is unnecessary.
	 *
	 */
	public void initialize(CometProperties inProps, Object inInitBlob) throws ClassCastException {
		StaticUtils.TRACE_METHOD_ENTER(logger);

		if (! bIsInitialized)  // Only initialize if we haven't already
		{
			mProps = inProps;
			
			if ( ! (inInitBlob instanceof DefaultHttpClient)) {
				throw new java.lang.ClassCastException("Invalid object type for input parameter inInitBlob");
			}
			
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
		//  From here on out, we are working with a file for this module.
		//
		
		
		if ( ! ( inElement.getHandle() instanceof File)) {
			logger.fatal("Unexpected calls type.  Expected java.io.File for item handle");

			StaticUtils.TRACE_METHOD_EXIT(logger);
			return false;
		}
		File oneFile = (File)inElement.getHandle();
		
		if ( ! oneFile.exists()) {
			logger.error("File does not exist: {} (Skipping)", oneFile.getAbsolutePath());

			StaticUtils.TRACE_METHOD_EXIT(logger);
			return false;
		}
		
		StaticUtils.TRACE_METHOD_EXIT(logger);
		return true;
	}

}
