package com.hds.hcp.tools.comet.test;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LoggingPlay {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Logger logger = LogManager.getLogger();
		
		logger.entry();
		logger.trace("Trace Message");
		logger.debug("Debug Message");
		logger.warn("Warning Message");
		logger.fatal("Fatal Message");
		logger.info("Informational Message");
		logger.fatal("Fatal with Exception", new ClassNotFoundException("just Kidding"));

	}

}
