package com.hds.hcp.tools.comet.utils;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hds.hcp.tools.comet.WorkItemStatus.WriteStatus;

public class StatisticsCollector {

	private static Logger logger = LogManager.getLogger();
	
	private long iSuccessObject;
	private long iPartialSuccessObject;
	private long iFailureObject;
	private long iNotProcessedObject;
	private long iSuccessCM;
	private long iPartialSuccessCM;
	private long iFailureCM;
	private long iNotProcessedCM;
	
	public long getObjectSuccesses() { return iSuccessObject; };
	public long getObjectPartialSuccesses() { return iPartialSuccessObject; };
	public long getObjectFailures() { return iFailureObject; };
	public long getObjectNotProcessed() { return iNotProcessedObject; };
	
	public long getCustomMetadataSuccesses() { return iSuccessCM; };
	public long getCustomMetadataPartialSuccesses() { return iPartialSuccessCM; };
	public long getCustomMetadataFailures() { return iFailureCM; };
	public long getCustomMetadataNotProcessed() { return iNotProcessedCM; };

	public StatisticsCollector() {
		reset();
	}
	
	public synchronized void reset() {
		iSuccessObject = iPartialSuccessCM = iFailureObject = iNotProcessedObject = 0;
		iSuccessCM = iPartialSuccessCM = iFailureCM = iNotProcessedCM = 0;
	}
	
	public synchronized void update(StatisticsCollector inStats) {
		iSuccessObject += inStats.getObjectSuccesses();
		iPartialSuccessObject += inStats.getObjectPartialSuccesses();
		iFailureObject += inStats.getObjectFailures();
		iNotProcessedObject += inStats.getObjectNotProcessed();
		
		iSuccessCM += inStats.getCustomMetadataSuccesses();
		iPartialSuccessCM += inStats.getCustomMetadataPartialSuccesses();
		iFailureCM += inStats.getCustomMetadataFailures();
		iNotProcessedCM += inStats.getCustomMetadataNotProcessed();
	}
	
	public synchronized void update(WriteStatus inObject, WriteStatus inCM) {

		// Save the object statistics
		switch(inObject) {
		case WRITE_SUCCESS:
			iSuccessObject++;
			break;
		case WRITE_PARTIAL_SUCCESS:
			// N/A
			break;
		case WRITE_FAILURE:
			iFailureObject++;
			break;
		case WRITE_NOT_ATTEMPTED:
			iNotProcessedObject++;
			break;
		case NOT_SET:
			break;
		}
		
		// Save the custom metadata statistics
		switch(inCM) {
		case WRITE_SUCCESS:
			iSuccessCM++;
			break;
		case WRITE_PARTIAL_SUCCESS:
			iPartialSuccessCM++;
			break;
		case WRITE_FAILURE:
			iFailureCM++;
			break;
		case WRITE_NOT_ATTEMPTED:
			iNotProcessedCM++;
			break;
		case NOT_SET:
			break;
		}
	}

	// Log statistics at the INFO level.
	public void logStats(String inLabel) {
		logStats(Level.INFO, inLabel);
	}

	// General routine to log the statistics at the specified level.
	public void logStats(Level inLevel, String inLabel) {
		synchronized(logger) {
			logger.log(inLevel, inLabel + ":");
			logger.log(inLevel, "                     (Success, Partial Success, Failure, Not Processed)");
			logger.log(inLevel, "    Object:          ({}, {}, {}, {})",
					getObjectSuccesses(),
					getObjectPartialSuccesses(),
					getObjectFailures(), 
					getObjectNotProcessed());
			logger.log(inLevel, "    Custom Metadata: ({}, {}, {}, {})", 
					getCustomMetadataSuccesses(),
					getCustomMetadataPartialSuccesses(),
					getCustomMetadataFailures(),
					getCustomMetadataNotProcessed());
			
		}
	}
}
