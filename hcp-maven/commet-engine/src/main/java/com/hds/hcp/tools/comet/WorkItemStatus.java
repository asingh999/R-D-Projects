package com.hds.hcp.tools.comet;

public class WorkItemStatus {
	/*
	 * Return values for CheckExistanceOnHCP method
	 */
	public enum WriteStatus {
		NOT_SET,
		WRITE_NOT_ATTEMPTED,
		WRITE_SUCCESS,
		WRITE_PARTIAL_SUCCESS,
		WRITE_FAILURE
	}

	private WriteStatus eObjectStatus = WriteStatus.WRITE_NOT_ATTEMPTED;
	private WriteStatus eCustomMetadataStatus = WriteStatus.WRITE_NOT_ATTEMPTED;
	
	private Exception mException;
	
	public Exception getException() { return mException; };
	public void setException(Exception inException) { mException = inException; };
	
	public void setObjectStatus(WriteStatus inStatus) { eObjectStatus = inStatus; };
	public WriteStatus getObjectStatus() { return eObjectStatus; }

	public void setCustomMetadataStatus(WriteStatus inStatus) { eCustomMetadataStatus = inStatus; };
	public WriteStatus getCustomMetadataStatus() { return eCustomMetadataStatus; }
}
