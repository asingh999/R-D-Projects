package com.hds.hcp.tools.comet;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hds.hcp.tools.comet.utils.StaticUtils;


public class PauseStopProcessor {
	
	private static Logger logger = LogManager.getLogger();
	private CometProperties mProps;
	private File stopFile;
	private File pauseFile;
	private PauseCallbackInterface mCallbackObject;
	
	public PauseStopProcessor(CometProperties inProps, Object inCallbackObject) {
		mProps = inProps;

		stopFile = new File(mProps.getStopFileName());
		pauseFile = new File(mProps.getPauseFileName());
		
		// Validate the callback object.
		if (null != inCallbackObject) {
			// Make sure it has the needed interface.
			if (inCallbackObject instanceof PauseCallbackInterface) {
				mCallbackObject = (PauseCallbackInterface)inCallbackObject;
			}

			// Humm.  Shouldn't happen unless a programming error.
			if (null == mCallbackObject) {
				logger.warn("Invalid parameter passed to PauseStopProcessor constuctor.  Not an implementation of PauseCallbackInterface");
			}
		}
	}
	
	public PauseStopProcessor(CometProperties inProps) {
		this(inProps, null);
	}
	
	public void checkContinue() throws InterruptedException {
		StaticUtils.TRACE_METHOD_ENTER(logger);

		//  See if we were asked to stop.
		if ( stopRequested() ) {
			logger.warn("User requested stop.");
			
			throw new InterruptedException("Detected User Initiated Stop File");
		}
	
		// See if we should pause as requested.
		if (pauseFile.exists()) {
			logger.warn("Pause requested by User");
			
			do {
				int sleepInSeconds = mProps.getPauseSleepTime();
				
				// If we have a callback object, call it.
				if (null != mCallbackObject) {
					mCallbackObject.pauseCallBack();
				}

				logger.info("Pause sleep for {} seconds", sleepInSeconds);

				Thread.sleep(sleepInSeconds * 1000);

				// Have the stop file override the pause file.
				if ( stopRequested() ) {
					logger.warn("User requested stop. Terminating pause.");
					
					throw new InterruptedException("Detected User Initiated Stop File");
				}
			} while (pauseFile.exists());
			
			logger.warn("Pause resumed by User");
		}

		StaticUtils.TRACE_METHOD_EXIT(logger);
	}

	// Check whether a stop was requested by user.
	public boolean stopRequested() {
		return stopFile.exists();
	}
}
