package com.hds.hcp.tools.comet.utils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.regex.PatternSyntaxException;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.hds.hcp.tools.comet.scanner.ScannerProperties;

public class PropsFilenameFilter implements FilenameFilter {
	private static Logger logger = LogManager.getLogger();

	private ScannerProperties mProps;
	private LinkedList<RegExprMatcher> mInclusionList, mExclusionList;
	private boolean bIsStrict = true;
	
	public PropsFilenameFilter(ScannerProperties inProps, boolean inIsStrict) {
		mProps = inProps;
		bIsStrict = inIsStrict;

		mInclusionList = new LinkedList<RegExprMatcher>();
		mExclusionList = new LinkedList<RegExprMatcher>();

		// Build the FileExtension list.  Important to do the exclusion list first
		//  because any dups in the inclusion list will override this.
		LinkedList<String> exclusionList = mProps.getSourceExclusionList();
		while ( ! exclusionList.isEmpty()) {
			// Construct the item and translate to a regular expression.
			String item = exclusionList.removeFirst();

			try {
				mExclusionList.add(new RegExprMatcher(item));
			} catch (PatternSyntaxException e) {
				logger.warn("Invalid Regular Expression \"" 
				               + item 
				               + "\" constructed from source.fileExclusion configuration property. Value ignored.", e); 
			}
		}
		
		LinkedList<String> inclusionList = mProps.getSourceInclusionList();
		while ( ! inclusionList.isEmpty()) {
			// Construct the item and translate to a regular expression.
			String item = inclusionList.removeFirst();

			try {
				mInclusionList.add(new RegExprMatcher(item));
			} catch (PatternSyntaxException e) {
				logger.warn("Invalid Regular Expression \"" 
				               + item 
				               + "\" constructed from source.fileInclusion configuration property. Value ignored.", e);
			}
		}
		
	}
	public PropsFilenameFilter(ScannerProperties inProps) {
		this(inProps, true);
	}
		
    public boolean accept(File inDir, String inName ) {

    	boolean retVal = true;

    	/*
    	 * The idea behind this filter is if a file matches an 
    	 *   exclusion, it won't be accepted.
    	 *   
    	 *   If there is an inclusion list and it hasn't already
    	 *   been excluded, then only include if there is a match
    	 *   in the inclusion list.
    	 */
    	
    	// Short circuit: No specifications
    	if ( mInclusionList.isEmpty() && mExclusionList.isEmpty()) {
    		return retVal;
    	}
    	
    	if (bIsStrict) {
        	// Short circuit: Only Regular Files
        	if ( ! (new File(inDir.getPath() + File.separatorChar + inName)).isFile() ) {
        		retVal = false;
        		return retVal;
        	}
    	}

    	// First check the exclusion and return if any matches.
    	if ( ! mExclusionList.isEmpty() ) {
    		Iterator<RegExprMatcher> iter = mExclusionList.iterator();
    		
    		while ( iter.hasNext() ) {
    			if (iter.next().isMatch(inName)) {
    				// Matched against one exclusion, don't accept.
    				retVal = false;
    				break;  // Done searching.
    			}
    		}
    	}
    	
    	// If the exclusion list did not block the file, process 
    	//  the inclusion list.
    	if ( (true == retVal) && ! mInclusionList.isEmpty() ) {
    		
    		retVal = false; // Since we have an inclusion list
    		                // going to assume we don't find a match.
    		
    		Iterator<RegExprMatcher> iter = mInclusionList.iterator();
    		
    		while ( iter.hasNext() ) {
    			if (iter.next().isMatch(inName)) {
    				retVal = true;
    				break; // Found a match, done looking.
    			}
    		}
    	}

		return retVal;
	}
}
