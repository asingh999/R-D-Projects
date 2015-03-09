package com.prudential.comet.generator;

import java.io.File;
import java.io.FilenameFilter;
import java.text.ParseException;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class LoggerWithAltDir extends PrudentialBaseFiles {

	private static Logger logger = LogManager.getLogger();

	private static String LISTING_FOLDER_PATTERN = "Prudential downloader aud file.*";
	private static String LISTING_FOLDER_BASE_PATTERN = "PRUDOWNLOADER_AUD FILE ";
	private static String TAPE_FOLDER_PATTERN = "TAPE\\s*\\d{1,2}$";
	
	private static String LISTING_FILE_PATTERN = "[Pp]rudownloader.*\\.txt";

	private static String FILE_PATTERN = "\\d{8}_\\d{6}_\\d{6}_.*\\.wav";

	private FilenameFilter mFolderFilter, mListingFileFilter;
	
	private RegExprMatcher mTapeFolderMatcher;

	// Putting this in a class so we compile the pattern for efficiency reasons.
	private class RegExprMatcher {
		RegExprMatcher(String inRegExpr) {
			pattern = Pattern.compile(inRegExpr);
		}
		private Pattern pattern;
		
		boolean isMatch(String inName) { return pattern.matcher(inName).matches(); }
	}

	// Class for filtering folders.
	private class FolderFilter implements FilenameFilter {

		private RegExprMatcher mMatcher;
		
		private FolderFilter(String inPattern) {
			mMatcher = new RegExprMatcher(inPattern);
		}
			
	    public boolean accept(File inDir, String inName ) {

	    	// Short circuit: Only want directory files.
	    	File currentFile = new File(inDir.getPath() + File.separatorChar + inName);
	    	if ( ! currentFile.isDirectory()) {
	    		return false;
	    	}
	    	
	    	// Now make sure it matches the file pattern.
	    	return mMatcher.isMatch(inName);
		}
	}
	    
	// Class for filtering Reglar files (Listing files)
	private class ListingFileFilter implements FilenameFilter {

		private RegExprMatcher mMatcher;
		
		private ListingFileFilter(String inPattern) {
			mMatcher = new RegExprMatcher(inPattern);
		}
			
	    public boolean accept(File inDir, String inName ) {

	    	// Short circuit: Only want regular files.
	    	File currentFile = new File(inDir.getPath() + File.separatorChar + inName);
	    	if ( ! currentFile.isFile()) {
	    		return false;
	    	}
	    	
	    	// Now make sure it matches the file pattern.
	    	return mMatcher.isMatch(inName);
		}
	}
	
	public void initialize() { 
		if ( ! isInitialized ) {			

			mFileType = "wav"; 
			mPatternMatch = FILE_PATTERN;
			
			mFolderFilter = new FolderFilter(LISTING_FOLDER_PATTERN);
			mListingFileFilter = new ListingFileFilter(LISTING_FILE_PATTERN);
			mTapeFolderMatcher = new RegExprMatcher(TAPE_FOLDER_PATTERN);
			
			super.initialize();
		}
	}
	
	protected String getCustomMetadata(File inSourceFile) {
		
		String retVal = null;
		
		try {
			/*
			 * Verify the file is of the format we expect for this module
			 */
			
			String fileName = inSourceFile.getName();
	
			// Do pattern match. Not using String.matches() because we don't want to compile the
			//   regular expression every invocation.
			if ( ! mFilePattern.matcher(fileName).matches()) {
				// Did not match, so don't go any further
				logger.debug("Did not match file pattern for filename: \"" + fileName + "\"");
				return null;
			}

			// Split apart the fields. NOTE, there 2 or 3 fields.  Inconsistent
			String fileNameParts[] = fileName.split("\\.");
			if (fileNameParts.length != 2 && fileNameParts.length != 3) {
				// Bad.  Not simple name.ext format.
				logger.warn("File name not well formatted (unexpected number of \".\" in name): \"" + fileName + "\"");
				return null;			

			}
	
			/*
			 *  Build up folder for listing files.
			 *
			 *  NOTES: 
			 *   To find the folder with the metadata files:
			 *    HDD1/HDD3: Search in the audio files path for "TAPE"
			 *      Then go down into folder that starts with "Prudential downloader aud file".
			 *    HDD2: Search in the audio file path for "TAPE"
			 *      Then go up one folder and down into "PRUDOWNLOADER_AUD FILE (TAPE ##)"
			 *    SubNote: Assumes some folder name fix-ups were done.
			 */

			/*
			 * The goal of this section of code is to populate the tapeListingFolder object
			 *  with the File object that contains files to scan through for metadata.
			 *  
			 *  The folder can exist in the following forms:
			 *   HDD1/TAPE#/Prudential downloader aud file.*
			 *   HDD # 2/TAPE ##
			 *   HDD # 2/TAPE## <- Hope to rename this
			 *   HDD # 2/PRUDOWNLOADER_AUD FILE (TAPE ##)
			 *   HDD # 3/TAPE #/Prudential downloader aud file
			 *   
			 *   Processing below is in summary:
			 *    1) Find TAPE folder
			 *    2) Look for Prudential downloader folder in the TAPE folder.
			 *    3) If not there, look in the parent of the TAPE folder 
			 *       for the "PRUDOWNLOADER_AUD FILE*" Folder in the parent.
			 */
			

			// Look for "TAPE" folder.
			File tapeListingFolder = inSourceFile.getParentFile();
			while ( null != tapeListingFolder && ! mTapeFolderMatcher.isMatch(tapeListingFolder.getName()) ) {
				tapeListingFolder = tapeListingFolder.getParentFile();
			}

			// Did we manage to find a "TAPE" Folder?
			if ( null == tapeListingFolder) {
				logger.warn("Did not find parent folder starting with \"TAPE\".  Can't generate metadata.");
				return null; // Outta here.
			}

			// Here we go.   Now look for a "Prudential downloader*" folder.
			File[] children = tapeListingFolder.listFiles(mFolderFilter);
			if (null == children) {
				logger.warn("Unexpected null list returned by folders matches pattern (" 
				        + LISTING_FOLDER_PATTERN + ") in folder \"" 
							+ tapeListingFolder.getPath() 
							+ "\". Trying parent folder.");
			} else if (0 != children.length && 1 != children.length ) {
				logger.warn("Unexpected number of folders matches pattern (" 
			        + LISTING_FOLDER_PATTERN + ") in folder \"" 
						+ tapeListingFolder.getPath() 
						+ "\". Trying parent folder.");
			} else if (1 == children.length) {
				// Only found one, so must be the right one.
				tapeListingFolder = children[0];
			} else {
				// So we did not find any folder, try to find the "PRUDOWNLOADER_AUD*" folder in the parent folder.
				
				File parentFolder = tapeListingFolder.getParentFile();
				String folderName = parentFolder.getPath() + File.separator + LISTING_FOLDER_BASE_PATTERN + "(" + tapeListingFolder.getName() + ")";
				
				tapeListingFolder = new File(folderName);
				
				if ( ! tapeListingFolder.exists() || ! tapeListingFolder.isDirectory() || ! tapeListingFolder.canRead() ) {
					logger.warn("Failed to construct path to existing readable folder named \"" + tapeListingFolder.getPath() + "\". Skipping metadata construction.Can't construct metadata.");
					return null;
				}
			}

			/*
			 * So now we should have a File object for the folder that contains the Files to search through.
			 * Loop through all the files to find the file we are looking for.
			 */
			// Get a list of the files.
			File listingFiles[] = tapeListingFolder.listFiles(mListingFileFilter);
			if (null == listingFiles ) {
				logger.fatal("Unexpected null value returned when returning the list of files in the listing file folder");
				return null;
			}

			int index = 0;
			for (index = 0; index < listingFiles.length; index++) {
				File ListingFile = listingFiles[index];
				
				if ( ! ListingFile.exists() || ! ListingFile.isFile() || ! ListingFile.canRead() ) {
					logger.warn("Listing file is not an existing regular readable file: \"" + ListingFile.getCanonicalPath() + "\"");
					return null;
				}

				logger.debug("Scanning for file \"" + fileNameParts[0] + "\" in file \"" + ListingFile.getPath() );				

				/*
				 * Let's start scanning the current file.
				 */
				String currentLine = null;
				Scanner fileScanner = new Scanner(ListingFile);
		
				try {
					// Keep searching through the file looking for the filename.
					//  Note: It will throw an exception when the EOF is reached.
					//    The null test below it to keep Java happy.
					while( (currentLine = fileScanner.nextLine()) != null )
					{
						if (-1 != currentLine.indexOf(fileNameParts[0]))
						{
					    	break;  // Found the string
					    }
					}
				} catch (NoSuchElementException e) {
					// Oops!!  Didn't find it.  
					logger.debug("Unable to find file \"" + fileName + "\" in listing file.");
					
					fileScanner.close();

					// Move on to next one.
					continue;
				}
				
				fileScanner.close();
			
				/*
				 * Well... If we got here, that means we found a line in the file that matches our
				 *   file name.  So time to go through the fun and parse it.
				 */
				try {
					retVal = processDownloaderMetadata(logger, fileNameParts[0], currentLine);
				} catch (ParseException e) {
					logger.fatal("Unexpected date string format encountered for file (" 
				                  + fileName 
				                  + ") in listing file \"" + ListingFile.getAbsolutePath() + "\"", e);
					
					retVal = null;
				}

				// We found it.  We are done looping through the files.
				break;
			} /* End-for */
		} catch (Exception e) {
			logger.fatal("Unexpected Exception", e);
			
			retVal = null;
		}

		return retVal;
	}
}
