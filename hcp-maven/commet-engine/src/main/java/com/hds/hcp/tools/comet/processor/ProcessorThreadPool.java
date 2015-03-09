package com.hds.hcp.tools.comet.processor;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.http.client.HttpClient;

import com.hds.hcp.apihelpers.HCPUtils;
import com.hds.hcp.tools.comet.BaseWorkItem;
import com.hds.hcp.tools.comet.CometProperties;
import com.hds.hcp.tools.comet.PauseStopProcessor;
import com.hds.hcp.tools.comet.ThreadPoolQueueInterface;
import com.hds.hcp.tools.comet.WorkItemStatus.WriteStatus;
import com.hds.hcp.tools.comet.utils.StaticUtils;

public class ProcessorThreadPool implements ThreadPoolQueueInterface {
	public ProcessorThreadPool(CometProperties inProps) {
		mProps = inProps;
	}

	private static Logger logger = LogManager.getLogger();
	
	private boolean bIsInitialized = false;
	private AtomicBoolean bIsStopRequested = new AtomicBoolean(false);
	private Thread[] mThreadArray; 
	
	private CometProperties mProps;
	private HttpClient mHttpClient;
	
	private LinkedBlockingQueue<BaseWorkItem> itemQueue;
	
	public void initialize() throws Exception {
		StaticUtils.TRACE_METHOD_ENTER(logger);

		if ( bIsInitialized ) {

			StaticUtils.TRACE_METHOD_EXIT(logger, "Already Initialized");
			return;
		}

		//TODO
		//TODO  Need to do better connection pooling to ensure spreading across HCP Nodes.

		// Get our http client.  NOTE: All threads will use the same instance of httpClient since it
		//   is thread safe and will help with pooling between all threads.
		mHttpClient = HCPUtils.initHttpClient(
				mProps.getHttpClientConnectionTimeout(), 
				mProps.getHttpClientMaxConnections(),
				mProps.getHttpClientMaxConnectionsPerRoute());

		// Allocate the item input queue
		itemQueue = new LinkedBlockingQueue<BaseWorkItem>(mProps.getProcessorTaskQueueSize());
		
 	    bIsInitialized = true;

		StaticUtils.TRACE_METHOD_EXIT(logger);
	}

	public void itemAdd(BaseWorkItem inItem) throws InterruptedException {
		itemQueue.put(inItem);
	}
	public boolean itemAdd(BaseWorkItem inItem, long inTimeout) throws InterruptedException {
		return itemQueue.offer(inItem, inTimeout, TimeUnit.SECONDS);
	}
	
	public BaseWorkItem itemTake() throws InterruptedException { return itemQueue.take(); }
	
	class Worker implements Runnable {
		private PauseStopProcessor mPauseStopProcessor;
		
		public void run() {
			StaticUtils.TRACE_METHOD_ENTER(logger);
	
			logger.debug("Thread Start");

			try {
				BaseItemProcessor itemProcessor;
				BaseWorkItem oneItem;

				mPauseStopProcessor = new PauseStopProcessor(mProps);
				
				/**
				 * Construct and initialize the item processor for this thread based on the class name configured
				 *    for this thread pool.
				 **/
				
				@SuppressWarnings("unchecked")
				Class<BaseItemProcessor> processorClass = (Class<BaseItemProcessor>) Class.forName(mProps.getProcessorClass());
				
				itemProcessor = (BaseItemProcessor)processorClass.newInstance();
				itemProcessor.initialize(mProps, mHttpClient);
				
				/**
				 *  Keep processing forever, until shutdown via interrupt.
				 */
				while( true ) {
					
					// See if we should keep processing. User may have requested Pause.
					try {
						mPauseStopProcessor.checkContinue();
						
					} catch (InterruptedException e) {
						if (mPauseStopProcessor.stopRequested()) {
							bIsStopRequested.set(true);
						}
						
						throw e;
					}
					
					// Go get the next item to work on.
					oneItem = itemQueue.take();
					
					logger.debug("Received Item: {}", oneItem.getName());
					
					try {
						itemProcessor.process(oneItem);
						
						oneItem.getStatus().setObjectStatus(itemProcessor.getStatus().getObjectStatus());
						oneItem.getStatus().setCustomMetadataStatus(itemProcessor.getStatus().getCustomMetadataStatus());
					} catch (Exception e) {
						// Save exception for this failure.
						oneItem.getStatus().setException(e);
						
						// Set the Object Status 
						if (WriteStatus.NOT_SET == itemProcessor.getStatus().getObjectStatus()) {
							oneItem.getStatus().setObjectStatus(WriteStatus.WRITE_FAILURE);
						} else {
							oneItem.getStatus().setObjectStatus(itemProcessor.getStatus().getObjectStatus());
						}
						
						if (WriteStatus.NOT_SET == itemProcessor.getStatus().getCustomMetadataStatus()) {
							oneItem.getStatus().setCustomMetadataStatus(WriteStatus.WRITE_FAILURE);
						} else {
							oneItem.getStatus().setCustomMetadataStatus(itemProcessor.getStatus().getCustomMetadataStatus());
						}
					}
	
					// Mark this one complete.
					oneItem.markProcessed();

					// Dereference to prepare for next.
					oneItem = null;
					
					// Sleep in between elements.  (Default is 0)
					//  Useful for throttling the processing to avoid saturation and timing testing.
					Integer sleepTime = mProps.getProcessorLoadTestingThreadSleep();
					if (0 < sleepTime) {
						logger.debug("Sleeping for {} milliseconds.", sleepTime);
						Thread.sleep(sleepTime);
					}
				}
			} catch (InterruptedException e) {
				if (bIsStopRequested.get()) {
					logger.info("Thread Interrupted (Stop Requested)");
				} else {
					logger.warn("Thread Interrupted (Unexpected)");
				}
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
				logger.fatal("Failed to construct Item Processor Object", e);
				logger.fatal("Thread Aborting");
				return;
			} catch (Exception e) {
				logger.fatal("Thread Aborted (Unhandled Exception)", e);
			}
			
			logger.debug("Thread Exiting");
			
			StaticUtils.TRACE_METHOD_EXIT(logger);
		}
	}
	
	public boolean start() {
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		if ( ! bIsInitialized ) { 
			StaticUtils.TRACE_METHOD_EXIT(logger, "Object not initialized");
			return false;
		}
		
		// Start up the number of threads that is configured (default is 1).
		int numThreads = mProps.getProcessorThreadCount();
		mThreadArray = new Thread[numThreads];
		for (int i = 0; i < numThreads; i++) {
			mThreadArray[i] = new Thread(new Worker());
			mThreadArray[i].setName("ProcessorThread(" + mThreadArray[i].getId() + ")");
			mThreadArray[i].start();
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

		// Wait for all the threads to finish (but only wait for configured time for each thread.
		for (int i = 0; i < mThreadArray.length; i++) {
			mThreadArray[i].join(mProps.getProcessorThreadMaxJoinWait() * 1000);
		}

		StaticUtils.TRACE_METHOD_EXIT(logger);
		return true;
	}
}
