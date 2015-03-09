package com.hds.hcp.tools.comet.scanner;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hds.hcp.tools.comet.BaseWorkItem;
import com.hds.hcp.tools.comet.CometProperties;
import com.hds.hcp.tools.comet.PauseCallbackInterface;
import com.hds.hcp.tools.comet.PauseStopProcessor;
import com.hds.hcp.tools.comet.ThreadPoolQueueInterface;
import com.hds.hcp.tools.comet.WorkItemStatus.WriteStatus;
import com.hds.hcp.tools.comet.utils.StaticUtils;
import com.hds.hcp.tools.comet.utils.StatisticsCollector;

public class ScannerThreadPool {
	private static Logger logger = LogManager.getLogger();

	public ScannerThreadPool(Object inProcessorPool, StatisticsCollector inStats, CometProperties inProps) {
		mProps = inProps;
		mProcessorPool = (ThreadPoolQueueInterface)inProcessorPool;
		mTotalStatistics  = inStats;
	}
	
	private StatisticsCollector mTotalStatistics;

	private int mStatsThreshold;
	
	private boolean bIsInitialized = false;
	private AtomicBoolean bIsStopRequested = new AtomicBoolean(false);
	
	protected ThreadPoolQueueInterface mProcessorPool;
	private Thread[] mThreadArray; 
	
	private CometProperties mProps;
	
	private LinkedBlockingQueue<BaseWorkItem> itemQueue;

	public void initialize() throws Exception {
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		if ( ! bIsInitialized ) {
			
			// Allocate the item input queue
			itemQueue = new LinkedBlockingQueue<BaseWorkItem>();
			
			mStatsThreshold = mProps.getStatsReportingThreshold();
			
	 	    bIsInitialized = true;
		}
		
		StaticUtils.TRACE_METHOD_EXIT(logger);
	}

	/*
	 * Task Queue Methods
	 */
	public void itemAdd(BaseWorkItem inItem) throws InterruptedException { itemQueue.put(inItem); }
	public boolean itemAdd(BaseWorkItem inItem, long inTimeout) throws InterruptedException { return itemQueue.offer(inItem, inTimeout, TimeUnit.SECONDS); }
	
	public BaseWorkItem itemTake() throws InterruptedException { return itemQueue.take(); }

	/**
	 * Worker Thread implementation.
	 */
	class Worker implements Runnable, PauseCallbackInterface, ScannerCompletionInterface {

		PauseStopProcessor mPauseStopProcessor = new PauseStopProcessor(mProps, this);
		
		LinkedBlockingQueue<BaseWorkItem> mCompletionQueue = new LinkedBlockingQueue<BaseWorkItem>(mProps.getScannerTaskQueueSize());
		
		long mOutstandingItemCount = 0;
		BaseScanner mScanner;

		StatisticsCollector mScannerItemStatistics;
		String mScannerItemName;
		
		public void run() {
			StaticUtils.TRACE_METHOD_ENTER(logger);
	
			logger.debug("Thread Start");
			
			BaseWorkItem oneItem;
			
			/**
			 * Perform initialization
			 **/
			logger.debug("Using Scanner class: {}", mProps.getScannerClass());

			try {
				@SuppressWarnings("unchecked")
				Class<BaseScanner> theClass = (Class<BaseScanner>) Class.forName(mProps.getScannerClass());

				mScanner = (BaseScanner)theClass.newInstance();
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			try {
			} catch (Exception e) {
				logger.fatal("ScannerThread Failed Thread Initialization. Thread Crashed!!", e);

				StaticUtils.TRACE_METHOD_EXIT(logger);
				return;
			}

			/**
			 * Main Processing Loop
			 **/
			try {
				// Create a Base Scanner class for this thread based on configuration
				@SuppressWarnings("unchecked")
				Class<BaseScanner> theClass = (Class<BaseScanner>) Class.forName(mProps.getScannerClass());

				mScanner = (BaseScanner)theClass.newInstance();
				
				/**
				 *  Keep processing forever, until shutdown via interrupt or asked nicely by PauseStopProcessor.
				 */
				while( true ) {

					// See if we should keep processing.
					try {
						mPauseStopProcessor.checkContinue();
						
					} catch (InterruptedException e) {
						if (mPauseStopProcessor.stopRequested()) {
							bIsStopRequested.set(true);
						}
						
						throw e;
					}
					
					// Get the next item to work on.
					oneItem = itemQueue.take();
					mScannerItemName = oneItem.getName();
					
					logger.info("STARTING: {}", mScannerItemName);
					
					mOutstandingItemCount = 0;
					mScannerItemStatistics = new StatisticsCollector();  // Get a freshy each time.
					
					// Process the item, but don't crash the thread if something goes bad.
					try {
						mScanner.initialize(oneItem.getHandle(), oneItem.getBaseSpecification(), this);
						
						BaseWorkItem scannerItem = mScanner.getNextItem();
						
						if (null == scannerItem) {
							logger.warn("Nothing for scanner to work on. ");
						}

						int maxWaitTime = mProps.getScannerMaxSubmitWaitTimeInSeconds();
		
						// While we have items to add...
						while (null != scannerItem) {
							
							// See if we should keep processing.
							try {
								mPauseStopProcessor.checkContinue();
								
							} catch (InterruptedException e) {
								if (mPauseStopProcessor.stopRequested()) {
									bIsStopRequested.set(true);
								}
								
								/*
								// TODO FUTURE POSSIBLITY. See about reporting on what was actually
								// TODO    processed when stop is requested.  Not terribly important
								// TODO    but might make stop a little more recoverable since the user
								// TODO    would know what was actually done.
								*/
								
								throw e;
							}

							// Submit an item to the Processor thread pool.
							logger.debug("Adding item to Processor Queue: {}", scannerItem.getName());
							scannerItem.setCompletionQueue(mCompletionQueue);
							if ( ! mProcessorPool.itemAdd(scannerItem, maxWaitTime) ) {
								long count = processCompleteItems((long)mProps.getScannerCompletionProcessingThreshold());
								mOutstandingItemCount -= count;
								
								logger.debug("ItemAdd wait timeout. Processed {} completed items. {} items still outstanding.", count, mOutstandingItemCount);

								continue;  // Back to the top and try the item again.
							}

							// Update Counters because we submitted an item.
							mOutstandingItemCount++;

							// See if it is time to do the periodic processing of completed items.
							if (mOutstandingItemCount > mProps.getScannerCheckCompleteQueueThreshold()) {
								long count = processCompleteItems((long)mProps.getScannerCompletionProcessingThreshold());
								mOutstandingItemCount -= count;
								
								logger.debug("Periodic processing of {} completed items. {} items still outstanding.", count, mOutstandingItemCount);
							}

							// Go get another one and process that now.
							scannerItem = mScanner.getNextItem();
							
							if (null != scannerItem) {
								// For Testing only. Sleep in between work items.  (Default is 0)
								//  Useful for throttling the processing to avoid saturation.
								Integer sleepTime = mProps.getScannerLoadTestingThreadSleep();
								if (0 != sleepTime) {
									logger.debug("Sleeping for {} milliseconds.", sleepTime);
									Thread.sleep(sleepTime);
								}
							}
						}

						// All individual items have been submitted.  Now wait for them all to complete.
						finalizeCompleteItems();
						
						// Now that we are done, update the total with the scanner item information.
						if (null != mTotalStatistics)
							mTotalStatistics.update(mScannerItemStatistics);

						mScannerItemStatistics.logStats("SCANNER COMPLETION STATISTICS (" + this.mScannerItemName + ")");

						logger.info("FINISHED: {}", oneItem.getName());
					} catch (InterruptedException e) {
						// Asked to stop.
						throw e;
					} catch (Exception e) {
						logger.fatal("Unexpected Exception. Exception passed back to caller", e);
						
						// Save exception for this failure.
						oneItem.getStatus().setException(e);
					}
	
					// Mark this one complete.
					oneItem.markProcessed();
	
					// Dereference to prepare for next.
					oneItem = null;
				}
			} catch (InterruptedException e) {
				if (bIsStopRequested.get()) {
					logger.info("Thread Interrupted (Stop Requested)");
				} else {
					logger.warn("Thread Interrupted (Unexpected)");
				}
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
				logger.fatal("Failed to construct Scanner Object", e);
				logger.fatal("Thread Aborting");
				return;
			} catch (Exception e) {
				logger.fatal("Unexpected Exception. Thread Crashed.", e);
			}
			
			logger.debug("Thread Exiting");
			StaticUtils.TRACE_METHOD_EXIT(logger);
		}

		void processBaseWorkItem(BaseWorkItem inItem) {
			StaticUtils.TRACE_METHOD_ENTER(logger);
			
			// Build a complex string
			StringBuilder logString = new StringBuilder();
			
			Exception theException = inItem.getStatus().getException();
			
			logString.append("Item Status: [" + inItem.getStatus().getObjectStatus() + ", " 
					+ inItem.getStatus().getCustomMetadataStatus());
			if (null != theException) {
				logString.append(", " + inItem.getStatus().getException().getClass().getName());
			}
			logString.append("] " + inItem.getName());
			if (null != theException) {
				logString.append("\n    Message: " + theException.getMessage());
			}

			logger.info(logString);

			// Update Scanner level Statistics.
			mScannerItemStatistics.update(inItem.getStatus().getObjectStatus(), inItem.getStatus().getCustomMetadataStatus());
			
			// If the write to HCP succeeded and we should delete the item from the source, then do it.
			if ( mProps.shouldScannerDeleteSourceItemsOnSuccess()
			     && WriteStatus.WRITE_SUCCESS == inItem.getStatus().getObjectStatus()
			     && (WriteStatus.WRITE_SUCCESS == inItem.getStatus().getCustomMetadataStatus()
			         || WriteStatus.WRITE_NOT_ATTEMPTED == inItem.getStatus().getCustomMetadataStatus()) ) {
				if ( ! mScanner.deleteItem(inItem) ) {
					logger.warn("Failed to delete source item: {}", inItem.getName());
				}
			} else if ( mProps.shouldScannerDeleteSourceItemsOnNotAttempted()
					    && WriteStatus.WRITE_NOT_ATTEMPTED == inItem.getStatus().getObjectStatus()
					    && (WriteStatus.WRITE_SUCCESS == inItem.getStatus().getCustomMetadataStatus()
						    || WriteStatus.WRITE_NOT_ATTEMPTED == inItem.getStatus().getCustomMetadataStatus()) ) {
				if ( ! mScanner.deleteItem(inItem) ) {
					logger.warn("Failed to delete source item: {}", inItem.getName());
				}
			}

			// Periodically report on statistics.
			if (0 >= --mStatsThreshold) {
				mScannerItemStatistics.logStats("Periodic Statistics (" + mScannerItemName + ")");
				mStatsThreshold = mProps.getStatsReportingThreshold();
			}
			
			StaticUtils.TRACE_METHOD_EXIT(logger);
		}
		
		@Override
		public long finalizeCompleteItems() throws InterruptedException {
			StaticUtils.TRACE_METHOD_ENTER(logger);
			
			long itemCount = 0;

			try {
				logger.debug("finalizeCompleteItems: Outstanding Item Count: " + mOutstandingItemCount);
				
				// Process until all outstanding items are completed.
				while (itemCount < mOutstandingItemCount) {
					// First see if we were asked to kindly pause/stop.
					mPauseStopProcessor.checkContinue();
	
					// Coming out of a pause, the items might have already been collected!!
					
					BaseWorkItem oneItem = mCompletionQueue.poll();
					if (null != oneItem) {
						// Set to go.  Process an item.
						processBaseWorkItem(oneItem);
						
						itemCount++;
					} else {
						logger.debug("finalizeCompletedItems: Sleeping for {} milliseconds", mProps.getScannerCompletionPollSleep());
						
						Thread.sleep(mProps.getScannerCompletionPollSleep());
					}
				}
			} catch (InterruptedException e) {
				if (mPauseStopProcessor.stopRequested()) {
					bIsStopRequested.set(true);
				}
				
				throw e;
			} finally {
				// Update outstanding count whether we got an exception or just exiting normally.
				mOutstandingItemCount -= itemCount;
			}
			
			logger.debug("finalizeCompleteItems: Completed {} Items", itemCount);
			
			StaticUtils.TRACE_METHOD_EXIT(logger);
			return itemCount;
		}
		
		// Process all items until empty.
		long processCompleteItems() { return processCompleteItems(-1); }

		// Process queue until no more items or maximum has been reached.
		long processCompleteItems(long inMaxToProcess) {
			StaticUtils.TRACE_METHOD_ENTER(logger);
			
			long itemCount = 0;

			BaseWorkItem oneItem;
			while (null != (oneItem = mCompletionQueue.poll()) ) {
				processBaseWorkItem(oneItem);
				
				itemCount++;
				
				// See if we have processed all we were asked to.  -1 means until empty.
				if (inMaxToProcess > 0 && itemCount >= inMaxToProcess) {
					break;
				}
			}

			StaticUtils.TRACE_METHOD_EXIT(logger);
			return itemCount;
		}

		@Override
		public void pauseCallBack() {
			StaticUtils.TRACE_METHOD_ENTER(logger);
			
			long count = processCompleteItems((long)mProps.getScannerCheckCompleteQueueThreshold());

			if (0 < count) {
				logger.debug("Pause periodic processing of {} completed items.", count);
			}
			
			mOutstandingItemCount -= count;
			
			StaticUtils.TRACE_METHOD_EXIT(logger);
		}
	}
	
	public boolean start() {
		return start(mProps.getScannerMaxThreadCount());
	}
	
	public boolean start(int inNumThreads) {
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		if ( ! bIsInitialized ) { 
			StaticUtils.TRACE_METHOD_EXIT(logger, "Object not initialized");
			return false;
		}

		// Only going to start the minimum number of threads.  Whatever is the reasonable smaller
		//   of what is passed into the function and what is allowed as specified by the configuration.
		//   An of course always start at least one thread.
		int numThreads = Math.min(Math.max(inNumThreads, 1),  Math.max(mProps.getScannerMaxThreadCount(), 1));

		// Start up the number of threads that is configured (default is 1).
		mThreadArray = new Thread[numThreads];
		for (int i = 0; i < numThreads; i++) {
			mThreadArray[i] = new Thread(new Worker());
			mThreadArray[i].setName("ScannerThread(" + mThreadArray[i].getId() + ")");
			mThreadArray[i].start();
		}
		
		StaticUtils.TRACE_METHOD_EXIT(logger);
		return true;
	}
	
	public boolean join() throws InterruptedException {
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		// Wait for all the threads to finish (but only wait for configured time for each thread.
		for (int i = 0; i < mThreadArray.length; i++) {
			mThreadArray[i].join(mProps.getScannerThreadMaxJoinWait() * 1000);
		}

		StaticUtils.TRACE_METHOD_EXIT(logger);
		return true;
	}
	
	public boolean stop() throws InterruptedException {
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		if ( ! bIsInitialized ) {
			StaticUtils.TRACE_METHOD_EXIT(logger, "Object not initialized");
			return false;
		}
		
		bIsStopRequested.set(true);
		
		// If there are no threads, just return as a failure.
		if (null == mThreadArray) return false;
		
		// Stop all the threads that were started.
		for (int i = 0; i < mThreadArray.length; i++) {
			mThreadArray[i].interrupt();
		}

		// Wait for threads to exit.
		join();

		StaticUtils.TRACE_METHOD_EXIT(logger);
		return true;
	}
}
