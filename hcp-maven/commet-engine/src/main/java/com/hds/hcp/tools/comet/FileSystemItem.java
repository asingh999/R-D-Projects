package com.hds.hcp.tools.comet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.concurrent.LinkedBlockingQueue;


public class FileSystemItem extends BaseWorkItem {
	public FileSystemItem(File inFile, File inBaseFolder) {
		setHandle(inFile);
		setBaseSpecification(inBaseFolder);
	}
	
	public FileSystemItem(File inFile, File inBaseFolder, LinkedBlockingQueue<BaseWorkItem> inCompletionQueue) {
		this.setHandle(inFile);
		this.setBaseSpecification(inBaseFolder);
		mCompletionQueue = inCompletionQueue;
	}

	//
	// These four constructors are special. They are used to instantiate a FileSystemItem from the value in a
	//   property file.
	public FileSystemItem(String inFile, String inBaseFolder) {
		setHandle(new File(inFile));
		if (null != inBaseFolder)
			setBaseSpecification(new File(inBaseFolder));
	}
	
	public FileSystemItem(String inFile, String inBaseFolder, LinkedBlockingQueue<BaseWorkItem> inCompletionQueue) {
		setHandle(new File(inFile));
		if (null != inBaseFolder) 
			setBaseSpecification(new File(inBaseFolder));
		mCompletionQueue = inCompletionQueue;
	}
	
	public FileSystemItem(BaseWorkItem inFile, BaseWorkItem inBaseFolder) {
		setHandle(new File(inFile.getName()));
		if (null != inBaseFolder)
			setBaseSpecification(new File(inBaseFolder.getName()));
	}
	
	public FileSystemItem(BaseWorkItem inFile, BaseWorkItem inBaseFolder, LinkedBlockingQueue<BaseWorkItem> inCompletionQueue) {
		setHandle(new File(inFile.getName()));
		if (null != inBaseFolder) 
			setBaseSpecification(new File(inBaseFolder.getName()));
		mCompletionQueue = inCompletionQueue;
	}
	
	public File getFile() { return (File)getHandle(); }
	public File getBaseFolder() { return (File)getBaseSpecification(); }
	
	public String getName() { return ((File)getHandle()).getAbsolutePath(); }
	
	
	@Override
	public InputStream getItemInputStream() throws FileNotFoundException {
		return (InputStream) new FileInputStream((File)getHandle());
	}

	@Override
	public boolean exists() {
		return ((File)getHandle()).exists();
	}

	@Override
	public boolean delete() {
		return ((File)getHandle()).delete();
	}

	@Override
	public boolean setWritable() {
		return ((File)getHandle()).setWritable(true,  true);
	}

	@Override
	public boolean isContainer() {
		return ((File)getHandle()).isDirectory();
	}

	@Override
	public boolean isChildOfContainer(BaseWorkItem inParent) {
		return ((File)getHandle()).getPath().startsWith(((File)inParent.getHandle()).getPath());
	}

}
