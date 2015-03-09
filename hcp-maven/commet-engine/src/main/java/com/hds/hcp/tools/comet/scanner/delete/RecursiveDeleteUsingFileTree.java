package com.hds.hcp.tools.comet.scanner.delete;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RecursiveDeleteUsingFileTree {
	private static Logger logger = LogManager.getLogger();
	/**
	 * Recursively deletes given file path content
	 * @param path
	 */
	public static void recursiveDeleteUsingFileTree(String path) {
		Path directoryToDelete = Paths.get(path);
		DeletingFileVisitor delFileVisitor = new DeletingFileVisitor();
		try{
			Files.walkFileTree(directoryToDelete, delFileVisitor);
		}catch(IOException ioe){
			logger.error("Error deleting file: " + directoryToDelete.toString(), ioe);
		}

	}

	 
/*	public static void main(String[] args) {
		recursiveDeleteUsingFileTree("C:\\delete\\hds-data");
	}*/
}
