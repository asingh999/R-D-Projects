package com.hds.hcp.tools.comet.utils;

import java.io.File;
import java.io.FilenameFilter;

import com.hds.hcp.tools.comet.scanner.ScannerProperties;

public class DirectoryAndPropFilenameFilter implements FilenameFilter {

	private DirectoryFileFilter mDirectoryFilter;
	private PropsFilenameFilter mFileFilter;
	
	public DirectoryAndPropFilenameFilter(ScannerProperties inProps) {
		mDirectoryFilter = new DirectoryFileFilter(inProps);
		mFileFilter = new PropsFilenameFilter(inProps);
	}
		
    public boolean accept(File inDir, String inName ) {
    	return mDirectoryFilter.accept(inDir, inName) || mFileFilter.accept(inDir, inName);
	}
}
