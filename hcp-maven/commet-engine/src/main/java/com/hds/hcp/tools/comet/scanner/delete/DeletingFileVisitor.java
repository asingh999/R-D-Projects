package com.hds.hcp.tools.comet.scanner.delete;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DeletingFileVisitor extends SimpleFileVisitor<Path>{
	private static Logger logger = LogManager.getLogger();
	@Override
	public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
		if(attributes.isRegularFile()){
			logger.debug("Deleting Regular File: " + file.toString());
			Files.deleteIfExists(file);
		}
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult postVisitDirectory(Path directory, IOException ioe) throws IOException {
		logger.debug("Deleting Directory: " + directory.toString());
		if (isDirEmpty(directory)) {
			Files.deleteIfExists(directory);
		}
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult visitFileFailed(Path file, IOException ioe) throws IOException {
		logger.error("Error deleting file: : " + file.toString(), ioe);
		return FileVisitResult.CONTINUE;
	}

	private static boolean isDirEmpty(final Path directory) throws IOException {
	    try(DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {
	        return !dirStream.iterator().hasNext();
	    }
	}
}
