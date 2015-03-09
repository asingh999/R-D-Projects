package com.hds.hcp.tools.comet.test;

import java.io.File;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hds.hcp.tools.comet.BaseWorkItem;
import com.hds.hcp.tools.comet.CometProperties;
import com.hds.hcp.tools.comet.FileSystemItem;
import com.hds.hcp.tools.comet.processor.ProcessorThreadPool;
import com.hds.hcp.tools.comet.scanner.FileSystemScanner;

public class TestScannerToWorkers {

	static Logger logger = LogManager.getLogger();

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			CometProperties myProps;

			logger.info("STARTING Program");

			FileSystemScanner myScanner = new FileSystemScanner(new File("SampleData"), null, null);
			myScanner.initialize();
			myProps = new CometProperties();
			
			// Create a worker pool that uses ElementProcessorEcho class for processing.
			ProcessorThreadPool myPool = new ProcessorThreadPool(myProps);
			
			myPool.initialize();

			logger.info("Starting " + myProps.getProcessorThreadCount() + " threads");
			myPool.start();
			LinkedBlockingQueue<BaseWorkItem> completionQueue = new LinkedBlockingQueue<BaseWorkItem>();

			int itemCount = 0;
			FileSystemItem oneItem = myScanner.getNextItem();
			while (null != oneItem) {

				oneItem.setCompletionQueue(completionQueue);
				myPool.itemAdd(oneItem);

				itemCount++;
				oneItem = myScanner.getNextItem();
			}
			
			logger.info("Submitted: " + itemCount + " Files");
			
			while (0 != itemCount--) {
				FileSystemItem result = (FileSystemItem)completionQueue.take();

				logger.info("File Name:     " + result.getFile().getAbsolutePath());
				logger.info("Object Status: " + result.getStatus().getObjectStatus());
				logger.info("CM Status:     " + result.getStatus().getCustomMetadataStatus());
			}

			logger.info("Stopping all threads");
			myPool.stop();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
