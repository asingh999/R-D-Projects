package com.hds.hcp.tools.comet.scanner;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Stack;
import java.util.regex.PatternSyntaxException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hds.hcp.tools.comet.BaseWorkItem;
import com.hds.hcp.tools.comet.FileSystemItem;
import com.hds.hcp.tools.comet.scanner.delete.RecursiveDeleteUsingFileTree;
import com.hds.hcp.tools.comet.utils.DirectoryAndPropFilenameFilter;
import com.hds.hcp.tools.comet.utils.RegExprMatcher;
import com.hds.hcp.tools.comet.utils.StaticUtils;

public class FileSystemScanner extends BaseScanner {

	public FileSystemScanner() {};
	
	public FileSystemScanner(File inBaseFolder, File inFolderStartPath, ScannerCompletionInterface inCompletionObject) {
		mBaseFolder = inBaseFolder;
		mFolderStartPath = inFolderStartPath;
		mCompletionCallback = inCompletionObject;
	}

	private class ContextEntry {
		ContextEntry(File inCurrentFolder) {
			currentFolder = inCurrentFolder;
		}
		
		File currentFolder;
		LinkedList<File> filesList;
		LinkedList<File> foldersList;
		LinkedList<File> writeLastFileList;
		
		boolean bFileListSorted = false;
		boolean isDeependStartTrigerSetOnTop = false;
	}
	
	private static Logger logger = LogManager.getLogger();
	
	private ScannerProperties mProps;
	private Stack<ContextEntry> contextStack = new Stack<ContextEntry>();
	private ContextEntry mCurrentContext;
	
	private File mBaseFolder, mFolderStartPath;
	private FilenameFilter mComboFilter;
	
	private LinkedList<RegExprMatcher> mLastFileMatcher = new LinkedList<RegExprMatcher>();

	private boolean isInitialized = false;
	private boolean bStartPathTriggered = false;

	@Override
	public void initialize(Object inBaseFolder, Object inFolderStartPath, ScannerCompletionInterface inCompletionCallback)
			throws Exception {
		
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		// Call the basic initialization function.
		initialize();
		
		if ( ! (inBaseFolder instanceof File) ) {
			logger.fatal("inBaseFolder is not a java.io.File");
			StaticUtils.TRACE_METHOD_EXIT(logger);
			return;
		}
		
		if ( (inFolderStartPath != null) && ! (inFolderStartPath instanceof File) ) {
			logger.fatal("inFolderStartPath is not a java.io.File");
			StaticUtils.TRACE_METHOD_EXIT(logger);
			return;
		}
		
		mCompletionCallback = inCompletionCallback;

		// Note: This function can be used to "re-initialize" the context of this object.
		//    That is why this is not a conditional initialization for these member variables.
		mBaseFolder = (File)inBaseFolder;
		mFolderStartPath = (File)inFolderStartPath;

		mLastFileMatcher = new LinkedList<RegExprMatcher>();

		LinkedList<String> writeLastFilter = mProps.getSourceWriteLastItemList();
		while ( ! writeLastFilter.isEmpty()) {
			// Construct the item and translate to a regular expression.
			String item = writeLastFilter.removeFirst();

			try {
				mLastFileMatcher.add(new RegExprMatcher(item));
			} catch (PatternSyntaxException e) {
				logger.warn("Invalid Regular Expression \"" 
				               + item 
				               + "\" constructed from source.writeLastFileList configuration property. Value ignored.", e);
			}
		}
		
		// Need to reset so next user starts all over again.
		bStartPathTriggered = false;

		StaticUtils.TRACE_METHOD_EXIT(logger);
	}

	@Override
	public void initialize() throws Exception {
		if ( ! isInitialized ) {
			mProps = new ScannerProperties();

			mComboFilter = new DirectoryAndPropFilenameFilter(mProps);

			isInitialized = true;
		}
	}

	private void pushContext() {
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		if (null != mCurrentContext) {
			contextStack.push(mCurrentContext);
		}
		
		mCurrentContext = null;
		
		StaticUtils.TRACE_METHOD_EXIT(logger);
	}
	
	private void popContext() {
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		if ( ! contextStack.isEmpty() ) {
			
			mCurrentContext = contextStack.pop();
			
			logger.debug("Returning to folder: {}", mCurrentContext.currentFolder.getAbsolutePath());
		} else {
			mCurrentContext = null;
		}
		
		StaticUtils.TRACE_METHOD_EXIT(logger);
	}
	
	private void setContext(File inFolder) {
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		mCurrentContext = new ContextEntry(inFolder);
		
		logger.info("Processing directory: {}", mCurrentContext.currentFolder.getAbsolutePath());

		mCurrentContext.filesList = new LinkedList<File>();
		mCurrentContext.foldersList = new LinkedList<File>();
		mCurrentContext.writeLastFileList = new LinkedList<File>();
	
		// Split out regular files from folders.
		File[] children = mCurrentContext.currentFolder.listFiles(mComboFilter);
		if (null != children) {
			// Add them to the List.
			for (int i=0 ; i < children.length ; i++) {
				File item = children[i];
				if (null != item) {
					if (item.isFile()) {

						// Is there a Last File matcher pattern?
						if ( ! mLastFileMatcher.isEmpty()) {
				    		boolean foundMatch = false;
				    		
					    	// See if it matches any of the inclusion specifications.
					    	Iterator<RegExprMatcher> iter = mLastFileMatcher.iterator();
							while ( iter.hasNext() ) {
								if (iter.next().isMatch(item.getName())) {
									mCurrentContext.writeLastFileList.add(item);
									
									foundMatch = true;
									break;
								}
							}

							// Did we match something?
							if (foundMatch) {
								continue;  // Yes. All done with this item
							}
				    	}

						// Reached this point, must be just a regular file to add to the list.
						mCurrentContext.filesList.add(item);

					} else if (item.isDirectory()) {
						mCurrentContext.foldersList.add(item);
					}
				}

				// Remove reference to help with memory management
				children[i] = null;
			}
			children = null;
		}
		
		// Sort the directories so that when they are processed, it is done in some order.
		if (mProps.shouldSortContainers()) {
			Collections.sort(mCurrentContext.foldersList);
		}
		if(!mCurrentContext.isDeependStartTrigerSetOnTop) { // Do not repeat after it's set.
			addStartTriggerPathAsFirstItemAndSetStartTrigger();
		}
		mCurrentContext.bFileListSorted = false;
		
		logger.info("Number of Children Directories: {}", mCurrentContext.foldersList.size());
		logger.info("Number of Children Files: {}", mCurrentContext.filesList.size());
		
		StaticUtils.TRACE_METHOD_EXIT(logger);
	}
	
	
	/**
	 * Checks for exact match or partial match.
	 *  If found remove the exact match or a level able partial match and add the respective items on the top of the list
	 */
	private void addStartTriggerPathAsFirstItemAndSetStartTrigger() {
		if(mFolderStartPath!=null && mFolderStartPath.getAbsolutePath()!=null) {
			String startPathTrigger = mFolderStartPath.getAbsolutePath();
			File deepestStartTriggerItem = null;
			if(mCurrentContext.foldersList.contains(mFolderStartPath)) { // Exact match of start trigger path
				deepestStartTriggerItem = mFolderStartPath;
				mCurrentContext.foldersList.remove(deepestStartTriggerItem);
				mCurrentContext.foldersList.addFirst(deepestStartTriggerItem);			
				mCurrentContext.isDeependStartTrigerSetOnTop = true;
			} else { // Partial match of start trigger path		
				String folderSep = startPathTrigger.indexOf("/")!=-1?"/":"\\"; /*Detect folder path separator*/
				String bestPossibleMatchTriggerPath =  startPathTrigger;
				File bestPossibleMatchTriggerDir = new File(bestPossibleMatchTriggerPath);
				if(deepestStartTriggerItem==null && bestPossibleMatchTriggerDir.exists()) {
					deepestStartTriggerItem = bestPossibleMatchTriggerDir;
				}
				while (bestPossibleMatchTriggerPath.indexOf(folderSep) !=-1){
					bestPossibleMatchTriggerPath =  bestPossibleMatchTriggerPath.substring(0, bestPossibleMatchTriggerPath.lastIndexOf(folderSep));
					bestPossibleMatchTriggerDir = new File(bestPossibleMatchTriggerPath);
					if(deepestStartTriggerItem==null && bestPossibleMatchTriggerDir.exists()) {
						deepestStartTriggerItem = bestPossibleMatchTriggerDir;
					}
					if(mCurrentContext.foldersList.contains(new File(bestPossibleMatchTriggerPath))) {
						mCurrentContext.foldersList.remove(new File(bestPossibleMatchTriggerPath));
						mCurrentContext.foldersList.addFirst(deepestStartTriggerItem);
						mCurrentContext.isDeependStartTrigerSetOnTop = true;
					}
				}
			}
		}
	}	

	/**
	 * Sets the start path trigger flag
	 * @return boolean 
	 */
	private boolean setStartPathTriggered() {
		if ( ! bStartPathTriggered ) {
			if(null == mFolderStartPath){
				bStartPathTriggered = true;
			} else {

				String startTriggerPath = mFolderStartPath.getAbsolutePath();
				if(startTriggerPath==null) { 
					bStartPathTriggered = false;
				} else {
					bStartPathTriggered = mCurrentContext.isDeependStartTrigerSetOnTop;
					logger.info("Start Path triggered.  Passed/Found path specified. Requested({}) Found:({})", mFolderStartPath.getAbsolutePath(), mCurrentContext.currentFolder.getAbsolutePath());
				}
			}

			if ( ! bStartPathTriggered ) {
				System.out.println("\n*****************  " + 1 + "*****************" );
				logger.debug("Start Path NOT yet triggered.");

				mCurrentContext.filesList.clear(); // Nothing to process at this level, clear out the files.
				mCurrentContext.bFileListSorted = true;  // Little efficiency for later.
			}	

		}

		return bStartPathTriggered;
	}

	
	@Override
	public FileSystemItem getNextItem() {
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		if ( ! isInitialized )
			throw new IllegalStateException("Object not initialized before attempted use");
		
		// Is this the first time this is called?
		if (null == mCurrentContext) {
			// Make sure directory provided actually exists.
			if ( ! mBaseFolder.exists() ) {
				logger.warn("Directory does not exist: \"" + mBaseFolder.getAbsolutePath() + "\"");
				StaticUtils.TRACE_METHOD_EXIT(logger);
				return null;
			}
			
			if ( ! mBaseFolder.isDirectory() ) {
				logger.warn("File specified is not a directory: \"" + mBaseFolder.getAbsolutePath() + "\"");
				StaticUtils.TRACE_METHOD_EXIT(logger);
				return null;
			}

			setContext(mBaseFolder);
		}
		
		/*
		 * At this point have a current context.  First pass back files, otherwise go down a folder level.
		 */
		while (true) {
			// If we haven't been triggered yet, then let's see if it is time.
			setStartPathTriggered();

			// If the file list is not yet sorted and we need to, then do so.
			if ( bStartPathTriggered && ! mCurrentContext.bFileListSorted && mProps.shouldSortItems() ) {
				// Sort them so they are in a understandable order.
				Collections.sort(mCurrentContext.filesList);
				mCurrentContext.bFileListSorted = true;
			}
			
			// If we have group of files pick one and send it back.
			if ( ! mCurrentContext.filesList.isEmpty() ) {
				StaticUtils.TRACE_METHOD_EXIT(logger, "Returning "
						+ mCurrentContext.filesList.peek().getParentFile().getName()
				        + File.separator
   		                + mCurrentContext.filesList.peek().getName());
				return new FileSystemItem(mCurrentContext.filesList.remove(), mBaseFolder);
			}

			// At this point all, regular files have been processed.
			//  So now process all the ones to write last.
			if ( ! mCurrentContext.writeLastFileList.isEmpty() ) {
				logger.debug("Processing WriteLastFile element: {}", mCurrentContext.writeLastFileList.peekFirst().getAbsolutePath());
				
				// Wait for all pending operations to complete since the file(s) to be written here must
				//   be last and if multiple written serially.
				try {
					if (null != mCompletionCallback)
						mCompletionCallback.finalizeCompleteItems();
					else
						logger.warn("No CompletionCallback provided.  finalizeCompleteItems not called.");
				} catch (InterruptedException e) {
					logger.info("Received interrupt while waiting for completed items in getNextItem()");
					
					break;  // Just break and return null.
				}

				StaticUtils.TRACE_METHOD_EXIT(logger, "Returning WriteLastFile " 
				         + mCurrentContext.writeLastFileList.peek().getParentFile().getName()
				         + File.separator
  		                 + mCurrentContext.writeLastFileList.peek().getName());
				return new FileSystemItem(mCurrentContext.writeLastFileList.remove(), mBaseFolder);
			}

			// Reached this point, so either there are not files, or all files have already been passed
			//  back.
			logger.debug("All files for folder complete: {}", mCurrentContext.currentFolder.getAbsolutePath());
			
			// Are there any folders at the current context?
			if ( ! mCurrentContext.foldersList.isEmpty() ) {
				// Yes.  So get one and prepare to process it.
				File subFolder = mCurrentContext.foldersList.remove();
				
				pushContext();

				setContext(subFolder);
				
				continue; // Looping back to the top.
			}

			// See if we are supposed to do folder removal when empty.
			// If directed to do folder removal, it will be less efficient in that for every
			//  folder the scanner must wait for the folder items to complete.
			if (mProps.shouldDeleteSourceEmptyContainers()) {
				// Wait for all pending operations to complete.
				try {
					if (null != mCompletionCallback)
						mCompletionCallback.finalizeCompleteItems();
				} catch (InterruptedException e) {
					logger.info("Received interrupt while waiting for completed items in getNextItem()");
					break;  // Just leave the loop and we'll return no item.
				}
				
				// Now remove the folder since the files have been completed and also removed.
				deleteFolder();   // Ignoring any failures.
			}
			
			
			popContext();
			
			// Have we popped all the context and thus all done?
			if (null == mCurrentContext) {
				break; // Yes.  Just break out of loop and return null;
			}
		}
		
		StaticUtils.TRACE_METHOD_EXIT(logger, "No more files to process");
		return null;  // No more to process.
	}

	private boolean deleteFolder() {
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		boolean retval = false;
		
		logger.debug("Deleting Folder: " + mCurrentContext.currentFolder.getAbsolutePath());
		
		//if ( mCurrentContext.currentFolder.delete() ) {
		//	retval = true;
		//} else {
			if (mProps.shouldForceDeleteSourceItems()) {
				if ( mCurrentContext.currentFolder.setWritable(true, true)) {
					logger.debug("Forced delete of folder succeeded.");
					RecursiveDeleteUsingFileTree.recursiveDeleteUsingFileTree(mCurrentContext.currentFolder.getAbsolutePath());
					retval = true; 
				} else {
					logger.warn("Failed to remove folder: {}", mCurrentContext.currentFolder.getAbsolutePath());
				}
			}
		//}

		StaticUtils.TRACE_METHOD_EXIT(logger);
		return retval;
	}
	
	
	public boolean deleteItem(FileSystemItem inItem) {
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		boolean retval = false;
		
		if ( mProps.shouldDeleteSourceItems()) {
			logger.debug("Deleting Item: {}", inItem.getName());
			
			if ( inItem.delete()) {
				retval = true;
			} else {
				logger.warn("Failed to delete source item: {}", inItem.getName());

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
