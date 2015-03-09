package com.hds.hcp.tools.comet.test;

import java.io.File;
import java.util.concurrent.LinkedBlockingQueue;

import com.hds.hcp.tools.comet.BaseWorkItem;
import com.hds.hcp.tools.comet.CometProperties;
import com.hds.hcp.tools.comet.FileSystemItem;
import com.hds.hcp.tools.comet.processor.ProcessorThreadPool;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestWorkerThreadPool {

	static Logger logger = LogManager.getLogger();

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		CometProperties mProps;

		logger.info("STARTING Program");
		try {
			
			mProps = new CometProperties();
			
			// Create a worker pool that uses ElementProcessorEcho class for processing.
			ProcessorThreadPool mProcessorPool = new ProcessorThreadPool(mProps);
			
			mProcessorPool.initialize();

			logger.info("Starting " + mProps.getProcessorThreadCount() + " threads");
			mProcessorPool.start();
			LinkedBlockingQueue<BaseWorkItem> completionQueue = new LinkedBlockingQueue<BaseWorkItem>();
			
			mProcessorPool.itemAdd(new FileSystemItem(new File("SampleData/applause2_x.wav"), null, completionQueue));
			mProcessorPool.itemAdd(new FileSystemItem(new File("SampleData/bear_growl_y.wav"), null, completionQueue));
			mProcessorPool.itemAdd(new FileSystemItem(new File("SampleData/bird.wav"), null, completionQueue));

			for (int toProcessCount = 3; toProcessCount > 0; toProcessCount--) {
				FileSystemItem result = (FileSystemItem)completionQueue.take();

				System.out.println("File Name:     " + result.getFile().getName());
				System.out.println("Object Status: " + result.getStatus().getObjectStatus());
				System.out.println("CM Status:     " + result.getStatus().getCustomMetadataStatus());
			}

//			FileSystemWalker walker = new FileSystemWalker(myPool.itemQueues, myPSProcessor, myStats, myProps);
			
//			walker.doWalk(myProps.getSourcePaths().getFirst(), null, null);
			
			logger.info("Stopping all threads");
			mProcessorPool.stop();
			
		} catch (Exception e) {
			e.printStackTrace();
		} 
		
		logger.info("ENDING Program");
	}
}
