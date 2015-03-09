package com.prudential.comet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hds.hcp.tools.comet.CometMain;

public class RunComet {

	private static Logger logger = LogManager.getLogger();
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
