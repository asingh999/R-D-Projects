package com.hds.hcp.tools.comet.generator;

import java.net.URI;
import java.net.URISyntaxException;

import com.hds.hcp.tools.comet.utils.URIWrapper;

public class ObjectContainer {

	private SystemMetadataContainer mSystemMeta;
	private CustomMetadataContainer mCustomMeta;

	public ObjectContainer(String inScheme, String inHost, BaseGeneratorProperties inProps) throws URISyntaxException {
		mSystemMeta = new SystemMetadataContainer(inScheme, inHost, inProps);
		mCustomMeta = new CustomMetadataContainer(inProps);
	}

	public ObjectContainer(URI inURI, BaseGeneratorProperties inProps) throws URISyntaxException {
		mSystemMeta = new SystemMetadataContainer(inURI, inProps);
		mCustomMeta = new CustomMetadataContainer(inProps);
	}
	
	public ObjectContainer(URIWrapper inURI, BaseGeneratorProperties inProps) throws URISyntaxException {
		mSystemMeta = new SystemMetadataContainer(inURI, inProps);
		mCustomMeta = new CustomMetadataContainer(inProps);
	}
	
	public SystemMetadataContainer getSystemMetadata() { return mSystemMeta; };
	public CustomMetadataContainer getCustomMetadata() { return mCustomMeta; };
}
