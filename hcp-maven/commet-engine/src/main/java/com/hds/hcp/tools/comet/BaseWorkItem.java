package com.hds.hcp.tools.comet;

import java.io.InputStream;
import java.util.concurrent.LinkedBlockingQueue;

// This is the BaseWorkItem that is used by other work item implementations.

public abstract class BaseWorkItem {
	
	protected Object mHandle;
	protected Object mBaseSpecification;

	// Holds the status for this work item.
	protected WorkItemStatus mStatus = new WorkItemStatus();
	
	// Holds a reference to the completion queue this item should be placed
	//   when processing is done but completion processing needs to be performed
	protected LinkedBlockingQueue<BaseWorkItem> mCompletionQueue;

	// Routine used by processor to indicate this is done with processing.
	public void markProcessed() throws InterruptedException {
		if (null != mCompletionQueue)
			mCompletionQueue.put(this);
	}
	
	// Methods for basic item operations that need to be implemented by the
	//   implementing object
	public abstract boolean exists() throws Exception;
	public abstract boolean delete() throws Exception;
	public abstract boolean setWritable();
	public abstract String getName();
	
	public abstract boolean isContainer();
	public abstract boolean isChildOfContainer(BaseWorkItem inParent);
	
	public abstract InputStream getItemInputStream() throws Exception;
	
	public Object getHandle() { return mHandle; }
	public void setHandle(Object inHandle) { mHandle = inHandle; }
	
	public Object getBaseSpecification() { return mBaseSpecification; }
	public void setBaseSpecification(Object inSpec) { mBaseSpecification = inSpec; }
	
	public WorkItemStatus getStatus() { return mStatus; }
	public void setStatus(WorkItemStatus inStatus) { mStatus = inStatus; }

	public LinkedBlockingQueue<BaseWorkItem> getCompletionQueue() { return mCompletionQueue; }
	public void setCompletionQueue(LinkedBlockingQueue<BaseWorkItem> inQueue) { mCompletionQueue = inQueue; }
}
