package com.hds.hcp.tools.comet.processor;

import com.hds.hcp.tools.comet.BaseWorkItem;
import com.hds.hcp.tools.comet.CometProperties;
import com.hds.hcp.tools.comet.WorkItemStatus;



public abstract class BaseItemProcessor {
	protected CometProperties mProps;

	protected WorkItemStatus mStatus = new WorkItemStatus();
	
	protected Boolean bIsInitialized = false;
	
	WorkItemStatus getStatus() { return mStatus; };
	
	/**
	 * Initialize the object by setting up internal data and establishing the HTTP client connection.
	 * 
	 * This routine is called by the ReadFromHCP and WriteToHCP routines, so calling it by the
	 * consumer of this class is unnecessary.
	 *
	 */
	abstract public void initialize(CometProperties inProps, Object inInitBlob) throws Exception;

	abstract public boolean process(BaseWorkItem inElement) throws Exception;
}
