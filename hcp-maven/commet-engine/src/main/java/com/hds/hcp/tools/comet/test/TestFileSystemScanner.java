package com.hds.hcp.tools.comet.test;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hds.hcp.tools.comet.FileSystemItem;
import com.hds.hcp.tools.comet.scanner.FileSystemScanner;

public class TestFileSystemScanner {

	static Logger logger = LogManager.getLogger();

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		logger.info("STARTING Program");

		FileSystemScanner myScanner = new FileSystemScanner(new File("SampleData"), null, null);
		try {
			myScanner.initialize();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		FileSystemItem oneItem = myScanner.getNextItem();
		while (null != oneItem) {
			logger.info("FileSystemItem: " + oneItem.getName());
			oneItem = myScanner.getNextItem();
		}
	}

}
