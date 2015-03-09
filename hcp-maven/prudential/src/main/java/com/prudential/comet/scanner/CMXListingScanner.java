package com.prudential.comet.scanner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hds.hcp.tools.comet.BaseWorkItem;
import com.hds.hcp.tools.comet.FileSystemItem;
import com.hds.hcp.tools.comet.scanner.BaseScanner;
import com.hds.hcp.tools.comet.scanner.ScannerCompletionInterface;
import com.hds.hcp.tools.comet.scanner.ScannerProperties;
import com.hds.hcp.tools.comet.utils.PropsFilenameFilter;
import com.hds.hcp.tools.comet.utils.StaticUtils;
import com.prudential.comet.AllianceCallMetadata;
import com.prudential.comet.CMXItem;

public class CMXListingScanner extends BaseScanner {

	public CMXListingScanner() {};
	
	public CMXListingScanner(File inBaseFolder, File inFolderStartPath, ScannerCompletionInterface inCompletionObject) {
		mBaseFolder = inBaseFolder;
		mStartPath = inFolderStartPath;
		mCompletionCallback = inCompletionObject;
	}

	private static Logger logger = LogManager.getLogger();
	
	private File mBaseFolder, mStartPath;
	
	private BufferedReader mListingFileReader;
	
	private boolean isInitialized = false;
	private boolean bStartFileTriggered = false;
	
	private PropsFilenameFilter mFilterHelper;

	private SimpleDateFormat mInputDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	
	/*
	 * Construct a private properties class to construct module specific information.
	 */
	private class CMXProperties extends ScannerProperties {
		public CMXProperties() throws IOException {
			super();
		}

		// Define private property file values.
		public String getListingFileName() {
			return StaticUtils.resolveEnvVars(mProps.getProperty("source.listingFileName"));
		}
		
		public String getMetadataTimeZone() {
			return mProps.getProperty("metadata.timeZone");
		}
	}

	private CMXProperties mProps;

	@Override
	public void initialize(Object inBaseFolder, Object inStartPath, ScannerCompletionInterface inCompletionCallback)
			throws Exception {
		
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		// Call the basic initialization function.
		initialize();
		
		if ( ! (inBaseFolder instanceof File) ) {
			logger.fatal("inBaseFolder is not a java.io.File");
			StaticUtils.TRACE_METHOD_EXIT(logger);
			return;
		}
		
		if ( (inStartPath != null) && ! (inStartPath instanceof File) ) {
			logger.fatal("inFolderStartPath is not a java.io.File");
			StaticUtils.TRACE_METHOD_EXIT(logger);
			return;
		}
		
		mCompletionCallback = inCompletionCallback;

		// Note: This function can be used to "re-initialize" the context of this object.
		//    That is why this is not a conditional initialization for these member variables.
		mBaseFolder = (File)inBaseFolder;
		mStartPath = (File)inStartPath;

		// Need to reset so next user starts all over again.
		bStartFileTriggered = false;
		
		// Setup the listing file we will read from for the file list and the file metadata.
		//  
		File listingFile = new File(mProps.getListingFileName());
		if ( ! listingFile.exists() || ! listingFile.isFile() || ! listingFile.canRead()) {
			logger.fatal("Invalid configuration for listingFileName (" + listingFile.getName() + "). Does not refer to an existing, regular, and readable file.");
			
			throw new IllegalArgumentException("Listing File Configuration does not refer to an existing, regular, and readable file");
		}
		
		mListingFileReader = new BufferedReader(new FileReader(listingFile));
		
		mFilterHelper = new PropsFilenameFilter(mProps);

		StaticUtils.TRACE_METHOD_EXIT(logger);
	}

	@Override
	public void initialize() throws Exception {
		if ( ! isInitialized ) {
			mProps = new CMXProperties();

			// Setup input date/time timezone based on configuration, if any.
			TimeZone tz = TimeZone.getDefault();
			String timeZoneName = mProps.getMetadataTimeZone();
			if (null != timeZoneName) {
				tz = TimeZone.getTimeZone(timeZoneName);
				if ( ! timeZoneName.equals("GMT") && tz.getID().equals("GMT")) {
					logger.warn("Metadata timezone might not be invalid. Defaulting to time zone of executing system.");
				}
			}
			
			logger.debug("Using time zone ({}) for input date/time values.", tz.getID());
			mInputDateFormat.setTimeZone(tz);

			isInitialized = true;
		}
	}

	@Override
	public CMXItem getNextItem() {
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		if ( ! isInitialized )
			throw new IllegalStateException("Object not initialized before attempted use");
		
		CMXItem retItem = null;
		try {
			/*
			 * Scan the input file for something to return.  If called the first time, it will start
			 *    at the beginning of the file.  If a subsequent call, it will be continue the scan from
			 *    the current location.
			 */
			String currentLine = mListingFileReader.readLine();

			while (null != currentLine) {
				AllianceCallMetadata currentItemMetadata = new AllianceCallMetadata();
				try {
					currentItemMetadata.load(currentLine, mInputDateFormat);
				} catch (IllegalArgumentException | ParseException e) {
					logger.warn("Unexpected exception parsing listing line (" + currentLine + "). Skipping...", e);

					currentLine = mListingFileReader.readLine();
					continue;
				}
				
				// If we haven't been triggered yet, then let's see if it is time.
				if ( ! bStartFileTriggered ) {
					
					// If the caller did not specify a start path, we have to assume it needs to be triggered.
					if (null == mStartPath) {
						bStartFileTriggered = Boolean.TRUE;
						logger.debug("Start Path triggered.  No path specified.");
					} else 	{
						// See if we have passed this file path.
						if (0 <= currentItemMetadata.WAVEPath.compareTo(mStartPath.getName())) {
							bStartFileTriggered = Boolean.TRUE;
							logger.debug("Start File triggered.  Passed/Found file specified.");
						}
					}
					
					// If start file still is not triggered, clean out the file list because we should not
					//   process it for this folder.
					if ( ! bStartFileTriggered ) {
						logger.debug("Start File NOT yet triggered.");
						
						// Next line and start at the top.
						currentLine = mListingFileReader.readLine();
						continue;
					}
				}

				// Construct the WAVFile File.
				File wavFile = new File(mBaseFolder.getCanonicalPath() + File.separator + currentItemMetadata.WAVEPath);
				
				// See if we should be working on this file by checking the inclusion and exclusion lists.
				if ( ! mFilterHelper.accept(wavFile.getParentFile(), wavFile.getName())) {
					logger.debug("File does match inclusion/exclusion criteria (" + wavFile.getCanonicalPath() + ")");
					
					// Nope.  Go to next.
					currentLine = mListingFileReader.readLine();
					continue;
				}
				
				// Make sure it is valid.
				if (! wavFile.exists() || ! wavFile.isFile() || ! wavFile.canRead()) {
					logger.warn("File generated from listing file (" + wavFile.getCanonicalPath() + ") is not an existing, regular, and readable file.  Skipping...");
					
					// Next line and start at the top.
					currentLine = mListingFileReader.readLine();
					continue;
				}
				
				retItem = new CMXItem(wavFile, mBaseFolder);
				retItem.setMetadata(currentItemMetadata);
				break; // All done
			}
		} catch (IOException e) {
			logger.fatal("Unexpected exception reading listing file", e);
			
			throw new IllegalStateException("Unexpected failure reading listing file.");
		}
		

		// Report if we have hit the end of the list.
		if (null == retItem) {
			try {
				mListingFileReader.close();
			} catch (IOException e) {
				logger.warn("Unexpected exception closing listing file reader", e);
			}
			mListingFileReader = null;
			
			StaticUtils.TRACE_METHOD_EXIT(logger, "No more files to process");
		} else {
			StaticUtils.TRACE_METHOD_EXIT(logger, "Returning valid next item");
		}
		
		return retItem;
	}

	public boolean deleteItem(FileSystemItem inItem) {
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		boolean retval = false;
		
		if ( mProps.shouldDeleteSourceItems()) {
			logger.debug("Deleting Item: " + inItem.getName());
			
			if ( inItem.delete()) {
				retval = true;
			} else {
				logger.warn("Failed to delete source item: " + inItem.getName());

				if (mProps.shouldForceDeleteSourceItems()) {
					if ( inItem.setWritable() && inItem.delete() ) {
						logger.debug("Forced delete of item succeeded.");
						retval = true;
					} else {
						logger.warn("Failed to delete source item after attempt to make writable");
					}
				}
			}
		}
		
		StaticUtils.TRACE_METHOD_EXIT(logger);
		return retval;
	}

	@Override
	public boolean deleteItem(BaseWorkItem inItem) {
		return deleteItem((FileSystemItem) inItem);
	}
}
