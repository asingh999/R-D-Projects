package com.hds.hcp.tools.comet.generator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;


public class CustomMetadataContainer {

	CustomMetadataContainer() {}

	CustomMetadataContainer(BaseGeneratorProperties inProps) {
		if (null != inProps) {
			bShouldDeleteExistingOnEmpty = inProps.shouldDeleteExistingCustomMetadataOnEmpty();
			bShouldUpdate = inProps.shouldUpdateCustomMetadata();
		}
	}
	
	private Boolean bShouldDeleteExistingOnEmpty = Boolean.FALSE;
	private Boolean bShouldUpdate = Boolean.FALSE;
	
	public static String DEFAULT_ANNOTATION = "default";
	
	//Linked List of annotations
	HashMap<String, byte[]> mAnnotations = new HashMap<String, byte[]>();
	
	public void put(byte[] inValue) {
		put(DEFAULT_ANNOTATION, inValue);
	}
	public void put(String inValue) {
		put(DEFAULT_ANNOTATION, inValue.getBytes());
	}
	
	public Iterator<Entry<String, byte[]>> iterator() {
		return mAnnotations.entrySet().iterator();
	}
	
	public void put(String inName, byte[] inValue) {
		mAnnotations.put(inName, inValue);
	}

	public void put(String inName, String inValue) {
		put(inName, inValue.getBytes());
	}
	
	public byte[] get(String inName) {
		return mAnnotations.get(inName);
	}
	
	public int count() {
		return mAnnotations.size();
	}
	
	public Boolean shouldDeleteExistingCustomMetadataOnEmpty() {
		return bShouldDeleteExistingOnEmpty;
	}
	
	public Boolean shouldUpdateCustomMetadata() {
		return bShouldUpdate;
	}
}
