package com.hds.hcp.tools.comet.scanner;

import com.hds.hcp.tools.comet.BaseWorkItem;


public abstract class BaseScanner {

//	protected ScannerProperties mProps;
	protected ScannerCompletionInterface mCompletionCallback;
	
	public abstract void initialize() throws Exception;
	public abstract void initialize(Object inBaseSpec, Object inStartItem, ScannerCompletionInterface inCompletionCallback) throws Exception;
	public abstract BaseWorkItem getNextItem();
	
	public abstract boolean deleteItem(BaseWorkItem inItem);
}
