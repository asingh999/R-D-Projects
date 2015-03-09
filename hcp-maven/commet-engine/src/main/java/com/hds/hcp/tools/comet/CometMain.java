/*
 *   Copyright (c) 2014 Hitachi Data Systems, Inc.
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
package com.hds.hcp.tools.comet;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hds.hcp.tools.comet.processor.ProcessorThreadPool;
import com.hds.hcp.tools.comet.scanner.ScannerThreadPool;
import com.hds.hcp.tools.comet.utils.StaticUtils;
import com.hds.hcp.tools.comet.utils.StatisticsCollector;

public class CometMain {

	private static Logger logger = LogManager.getLogger();

	// Local member variables.
	private Boolean bIsInitialized = false;
	private CometProperties mProps;
	
	private ScannerThreadPool mScannerPool = null;
	private ProcessorThreadPool mProcessorPool = null;
	
	LinkedBlockingQueue<BaseWorkItem> mCompletionQueue = new LinkedBlockingQueue<BaseWorkItem>();
	
	// Processor classes.
	private PauseStopProcessor mPauseStopProcessor;
	
	// Statistics Holder.
	private StatisticsCollector mTotalStatistics = new StatisticsCollector();
	
	private LinkedList<BaseWorkItem> mSourceItemList;
	private LinkedList<BaseWorkItem> mSourceStartItemList;
	
	/**
	 * This function take a linked list of strings as provided in the source.* parameters and converts
	 *  it to BaseWorkItems objects of the appropriate parent type as configured in the properties
	 *  file.
	 * @param inStringList - List of strings that should be item "paths".  For file system type, it
	 *     will be file system paths (absolute or relative).
	 * @return A linked list of BaseWorkItems that represent the string passed in.
	 */
	private LinkedList<BaseWorkItem> convertList(LinkedList<String> inStringList)
			throws URISyntaxException, ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		if (null == inStringList) {
			StaticUtils.TRACE_METHOD_EXIT(logger, "No List provide to method");
			
			return null;
		}
		
		LinkedList<BaseWorkItem> retVal = new LinkedList<BaseWorkItem>();

		// Construct a class for a BaseWorkItem derived class for which based on configuration value
		@SuppressWarnings("unchecked")
		Class<BaseWorkItem> theClass = (Class<BaseWorkItem>) Class.forName(mProps.getSourceClassName());

		// Loop through all the strings and construct the configured type of object.
		ListIterator<String> inputIterator = inStringList.listIterator();
		while (inputIterator.hasNext()) {
			String itemName=inputIterator.next();
			BaseWorkItem oneItem = null;

			if (null != itemName) {
				// Construct an appropriate class using the string element of the linked list.
				oneItem 
	  			    = (BaseWorkItem)theClass.getDeclaredConstructor(String.class, String.class)
		               .newInstance(itemName, null);
			}

			// Add it to the return linked list.
			retVal.addLast(oneItem);
		}
		
		StaticUtils.TRACE_METHOD_EXIT(logger);
		return retVal;
	}
	
	/*
	 * This method reads the configuration for the source.items and source.startTargetItems and builds
	 *    a list of BaseWorkItems derived objects. It will also sanitize the items to ensure that
	 *    the values make sense between the two lists.
	 */
	private void buildSourceItems() throws Exception {
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		// First let's validate the list of items and remove any bad entries.
		//  NOTE: Some entries might only be temporarily bad for the current 
		//    loop execution. It might be a list of folders that will eventually 
		//    exist.
		mSourceItemList = convertList(mProps.getSourceItems());
		mSourceStartItemList = convertList(mProps.getSourceStartTriggerItems());

		
		// looping through the mSourceItem list, make sure the corresponding item in
		//  the mSourceStartItemList makes sense.
		ListIterator<BaseWorkItem> sanitizeIterator = mSourceItemList.listIterator();
		
		while (sanitizeIterator.hasNext()) {
			int currentEntryIndex = sanitizeIterator.nextIndex();
			BaseWorkItem currentEntry = sanitizeIterator.next();
			
			BaseWorkItem currentStartItem = null;
			try {
			    currentStartItem = mSourceStartItemList.get(currentEntryIndex);
			} catch (IndexOutOfBoundsException e) {
				// Well we tried. It was a short array, but that is ok, It implies
				//  there isn't one.
			}

			// Make sure the mSourceItemList element is valid.
			if ( ! currentEntry.exists()) {
				logger.warn("Config parameter source.items contains a non-existant item (" 
						+ currentEntry.getName() + ").  Skipping.");
				
				// No good.  Remove it and the corresponding StartItem, if exists.
				mSourceItemList.remove(currentEntry);
				if ( null != currentStartItem) {
					mSourceStartItemList.remove(currentStartItem);
				}
				
			} else if ( ! currentEntry.isContainer() ) {
				logger.warn("Config parameter source.items contains a non-container element ("
					+ currentEntry.getName() + "). Skipping.");
				
				// No good.  Remove it and the corresponding StartItem, if exists.
				mSourceItemList.remove(currentEntry);
				if ( null != currentStartItem) {
					mSourceStartItemList.remove(currentStartItem);
				}
				
			} else {
				// Let's make sure the StartItem matches the source item. Otherwise, not going
				//  to be useful.
				if ( null != currentStartItem && ! currentStartItem.isChildOfContainer(currentEntry) ) {
					// Nice try. Blank out this element.
					mSourceStartItemList.set(currentEntryIndex,  null);
					
					logger.warn("Config parameter source.startTriggerItems element \"{}\" will not exist in source.items element \"{}\". Must be matching specification. Value ignored.", 
							currentStartItem.getName(),
							currentEntry.getName());
				}
			}
		}

		// Make sure we have something to work on.
		if ( mSourceItemList.isEmpty() ) {
			logger.warn("Config parameter \"source.items\" is empty after sanitizing list. Continuing.");
		}

		StaticUtils.TRACE_METHOD_EXIT(logger);
	}

	/**
	 * Initialize the object by setting up internal data and establishing the HTTP client connection.
	 * 
	 * This routine is called by the ReadFromHCP and WriteToHCP routines, so calling it by the
	 * consumer of this class is unnecessary.
	 *
	 */
	private void initialize() throws Exception {
		StaticUtils.TRACE_METHOD_ENTER(logger);

		if (! bIsInitialized)  // Only initialize if we haven't already
		{
			//
			// Setup properties member.
			mProps = new CometProperties();
			
			mPauseStopProcessor = new PauseStopProcessor(mProps);
			
			//
			// Create a worker pool that uses ElementProcessorEcho class for processing.
			mProcessorPool = new ProcessorThreadPool(mProps);
			
			mProcessorPool.initialize();

			logger.info("Starting " + mProps.getProcessorThreadCount() + " ProcessorThreadPool threads");
			mProcessorPool.start();
			
			//
			// Build the list of items to process, so we know how many Scanner threads to start.
			buildSourceItems();

			// Now have a list of folders to process, start the thread pool.
			mScannerPool = new ScannerThreadPool(mProcessorPool, mTotalStatistics, mProps);
			
			mScannerPool.initialize();

			// Only start the number of threads that we need up to the max.
			logger.info("Starting " + Math.min(mSourceItemList.size(), mProps.getScannerMaxThreadCount()) + " ScannerThreadPool threads");
			mScannerPool.start(mSourceItemList.size());
			
			bIsInitialized = true;
		}

		StaticUtils.TRACE_METHOD_EXIT(logger);
	}
 
	/*
	 * Mainline of COMET.  The main line just sets up the environment and submits the work items
	 *   to the scanner thread. Then just waits for it all to complete.
	 */
	public void runComet() throws Exception {
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		// Setup object structures and information.
		initialize();
		
		// Make sure we have something to do.
		if (mSourceItemList.isEmpty()) {
			logger.error("Source Item list is empty after sanitization.  Nothing to work on.");
			logger.info("Aborting Program");

			shutdown();
			StaticUtils.TRACE_METHOD_EXIT(logger);
			return;
		}
		try {
			 processLoop(); // process items
		} catch (InterruptedException e) {
			// Got an interrupt.  Let finally block clean it all up.
			logger.info("Interrupt Received");
		} catch (Exception e) {
			logger.error("Unexpected Exception", e);
			
			throw e;
		} finally {
			shutdown();
		}
		
		mTotalStatistics.logStats("FINAL STATISTICS");
		
		StaticUtils.TRACE_METHOD_EXIT(logger);
	}
	
	private void processLoop() throws Exception {
		int loopEndCount = mProps.getLoopCount();  // Note loop count will stay the same throughout execution

		/*
		 * Check for conflicting parameter settings.
		 */
		// Check for not deleting files and doing infinite loop.
		if (CometProperties.LOOPCOUNT_INFINITE == loopEndCount
				&& ! mProps.shouldScannerDeleteSourceItemsOnSuccess()) {
			logger.fatal("Conflicting Parameters: \"execution.loopCount = -1\" and \"execution.scanner.deleteSourceItemsOnSuccess = false\". Execution Aborted.");
			
			throw new IllegalArgumentException("Incompatible configuration encountered");
		}

		// Tell the user if running in Infinite mode.
		if (CometProperties.LOOPCOUNT_INFINITE == loopEndCount) {
			logger.info("Running in Infinite Loop mode");
		}
		
		/**
		 *  Loop for the number of iterations specified.
		 */
		for (int currentLoop = 1
				; CometProperties.LOOPCOUNT_INFINITE == loopEndCount || currentLoop <= loopEndCount
				; currentLoop++) {

			// Process pausing or stopping.
			mPauseStopProcessor.checkContinue();

			logger.info("Executing Loop (" + currentLoop + " of " + 
					(loopEndCount == CometProperties.LOOPCOUNT_INFINITE ? currentLoop + "Infinite" : loopEndCount) + ")");
			
			// Using our sanitized source item list, submit each one to the scanner.
			int numOutstandingItems = submitSourcePath(); // Keeps track of the number of source paths submitted and finished.

			//Wait for all submitted items to complete.
			numOutstandingItems = waitForSourcePathToComplete(numOutstandingItems);
	
			// Do some prep work only if going to do another loop
			preworkForNextLoop(loopEndCount, currentLoop);

		}
	}
	
	private int submitSourcePath() throws Exception {
		int numOutstandingItems = 0; // Keeps track of the number of source paths submitted and finished.
		
		/*
		 * Using our sanitized source item list, submit each one to the scanner.
		 */
		ListIterator<BaseWorkItem> executeIterator = mSourceItemList.listIterator();
		while (executeIterator.hasNext()) {
			BaseWorkItem currentStartItem = null;
			int currentIndex = executeIterator.nextIndex();

			// See if we were asked to pause or stop.
			mPauseStopProcessor.checkContinue();
			
			BaseWorkItem currentItem = executeIterator.next();
			try {
				currentStartItem = mSourceStartItemList.get(currentIndex);
			} catch (IndexOutOfBoundsException e) {
				// Ignore it because it likely is that the list is smaller.
			}
			
			logger.info("Submitting processing of source.items element \"" + currentItem.getName() + "\"");
			if (null != currentStartItem) {
				logger.info("Using source.targetStartItems element value \"" + currentStartItem.getName() + "\" for start trigger.");
			}
			
			/*
			 * Create a object derived from BaseWorkItem class based on the configuration. 
			 *   This will be submitted to the scanner for processing.
			 */
			@SuppressWarnings("unchecked")
			Class<BaseWorkItem> theClass = (Class<BaseWorkItem>) Class.forName(mProps.getSourceClassName());

			BaseWorkItem onePath 
			    = (BaseWorkItem)theClass.getDeclaredConstructor(BaseWorkItem.class, BaseWorkItem.class)
			                         .newInstance(currentItem, currentStartItem);
			onePath.setCompletionQueue(mCompletionQueue); // This is where the completed item will be placed

			// Submit it to the scanner.
			try {
				mScannerPool.itemAdd(onePath);
			} catch (InterruptedException e) {
				logger.info("Interrupt detected.  Stopping processing.");
				
				throw e;
			}
			numOutstandingItems++;
		}
		
		return numOutstandingItems;
	}
	
	
	private int waitForSourcePathToComplete(int numOutstandingItems)   throws Exception  {
		while (numOutstandingItems > 0) {
			// See if we should keep processing.
			mPauseStopProcessor.checkContinue();
				
			BaseWorkItem result = mCompletionQueue.poll(mProps.getCompletionPollSleep(), TimeUnit.MILLISECONDS);
			
			if (null != result) {
				logger.info("COMPLETED: " + result.getName());
				numOutstandingItems--;
			}
		}
		
		return numOutstandingItems;
	}	
	
	
	
	private void preworkForNextLoop(int loopEndCount, int currentLoop)  throws Exception{
		if (loopEndCount == CometProperties.LOOPCOUNT_INFINITE || (1 < loopEndCount && currentLoop < loopEndCount) ) {
			// Report on Loop statistics.
			mTotalStatistics.logStats("LOOP STATISTICS (" + currentLoop + ")");

			// Refresh the properties.
			try {
				mProps.refresh();
			} catch (IOException e) {
				// Eh?!?!  Gave it a try.
				logger.warn("Unable to refresh properties.", e);
			}

			// Sleep for the desired time.
			int sleepInSeconds = mProps.getLoopSleepTime();
			
			logger.info("Loop sleep for " + sleepInSeconds + " seconds");
			
			// Only sleep in 5 seconds increments so we can check for stop/pause request.
			while (sleepInSeconds > 0) {
				int thisSleep = (sleepInSeconds > 5 ? 5 : sleepInSeconds);
				Thread.sleep(thisSleep * 1000);
				sleepInSeconds -= thisSleep;
				
				// Have the stop file override the pause file.
				mPauseStopProcessor.checkContinue();
			}
		}
	}
	
	void shutdown() {
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		logger.info("Stopping all threads");
		
		//  Top level shutdown.  Try to stop all the pools for a clean-ish shutdown.
		//  Having each in a separate try/catch block was intentional in the case the
		//  ScannerPool shutdown has issue, at least will then try ProcessorPool
		try {
			// Shutting down Scanner pool before Processor pool as Scanner is the feeder.
			if (null != mScannerPool) {
				logger.debug("Stopping Scanner Pool Threads");
				mScannerPool.stop();
				mScannerPool=null;
			}
		} catch (InterruptedException e2) {
			// Enough already. Just ignore.
			logger.warn("Received Interrupt during ScannerPool shutdown processing");
		}
		try {
			// Shutting down Scanner pool before Processor pool as Scanner is the feeder.
			if (null != mProcessorPool) {
				logger.debug("Stopping Processor Pool Threads");
				mProcessorPool.stop();
				mProcessorPool=null;
			}
		} catch (InterruptedException e2) {
			// Enough already. Just ignore.
			logger.warn("Received Interrupt during ProcessorPool shutdown processing");
		}
		
		StaticUtils.TRACE_METHOD_EXIT(logger);
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			CometMain myself = new CometMain();
			
			myself.runComet();
			
			logger.info("Exiting Program");
		} catch (Exception x) {
			logger.fatal("Unhandled Exception caught by main.", x);
			System.exit(-1);
		}
	}

}
