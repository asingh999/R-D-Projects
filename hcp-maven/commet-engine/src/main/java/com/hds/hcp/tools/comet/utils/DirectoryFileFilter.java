package com.hds.hcp.tools.comet.utils;

import java.io.File;
import java.io.FilenameFilter;

import com.hds.hcp.tools.comet.scanner.ScannerProperties;

public class DirectoryFileFilter implements FilenameFilter {

	public DirectoryFileFilter(ScannerProperties inProps) {
		// Don't do anything with props at this point.  Might be something in the future.
	}
		
    public boolean accept(File inDir, String inName ) {

    	// Is a directory file?  Want to always accept all directories
    	//   otherwise we will only process them if they match the path specifications.
    	return new File(inDir.getPath() + File.separatorChar + inName).isDirectory();
	}
}
