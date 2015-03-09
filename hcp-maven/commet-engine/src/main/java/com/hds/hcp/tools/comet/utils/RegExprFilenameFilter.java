package com.hds.hcp.tools.comet.utils;

import java.io.File;
import java.io.FilenameFilter;

public class RegExprFilenameFilter implements FilenameFilter {
	private RegExprMatcher mMatcher;
	
	public RegExprFilenameFilter(String inPattern) {
		mMatcher = new RegExprMatcher(inPattern);
	}
	
	public RegExprFilenameFilter(RegExprMatcher inMatcher) {
		mMatcher = inMatcher;
	}
		
    public boolean accept(File inDir, String inName ) {
    	return mMatcher.isMatch(inName);
	}
}
