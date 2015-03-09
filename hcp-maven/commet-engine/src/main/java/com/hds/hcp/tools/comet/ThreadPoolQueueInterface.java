package com.hds.hcp.tools.comet;

public interface ThreadPoolQueueInterface {
	public void itemAdd(BaseWorkItem inItem) throws InterruptedException;
	public boolean itemAdd(BaseWorkItem inItem, long inTimeout) throws InterruptedException;
	
	public BaseWorkItem itemTake() throws InterruptedException;
}
