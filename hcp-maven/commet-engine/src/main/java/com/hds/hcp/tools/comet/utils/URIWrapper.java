package com.hds.hcp.tools.comet.utils;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class URIWrapper {
	private String mScheme;
	private String mHost;
	private String mPath;
	private String mQuery;
	
	private Map<String, String> mQueryParams;
	boolean bNeedsUpdate = true;
	
	// This routine is necessary because URIEncoder is a piece of junk.  Need to encode some stuff manually.
	static public String encode(String inString) throws URISyntaxException {
		String retval;
		
		// Do most of the work.
		try {
			retval = URLEncoder.encode(inString, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new URISyntaxException(inString, e.getMessage());
		}
		
		// Now undo the over-zealous nature of the encoder.
		retval = retval.replaceAll("\\+", "%20");
		retval = retval.replaceAll("%2F", "/");
		return retval.replaceAll("%3A", ":");
	}

	// Copy Constructor
	public URIWrapper(URIWrapper inURIWrapper) throws URISyntaxException {
		mScheme = inURIWrapper.getScheme();
		mHost = inURIWrapper.getHost();
		mPath = inURIWrapper.getRawPath();
		mQuery = inURIWrapper.getQuery();

		syncQueryParams(TO_MAP);
	}
	
	public URIWrapper(String inURIString) throws URISyntaxException {
		int begPos, endPos;
		
		// Get the Scheme
		endPos = inURIString.indexOf("://");
		if (-1 == endPos) {
			throw new URISyntaxException(inURIString, "Could not locate end of URI scheme");
		}
		
		mScheme = inURIString.substring(0, endPos);
		
		// Get the Host
		begPos = endPos + 3;
		endPos = Math.min(inURIString.length(), inURIString.indexOf("/", begPos));
		if (-1 == endPos) endPos = inURIString.length();

		mHost = inURIString.substring(begPos, endPos);
		if (mHost.isEmpty()) {
			throw new URISyntaxException(inURIString, "Could not locate end of URI host");
		}
		
		// Is there any more?
		if (endPos >= inURIString.length()) {
			return; // No we are done.
		}

		// Get path portion
		begPos = endPos + 1;
		
		endPos = inURIString.lastIndexOf("?") - 1;
		if (endPos < begPos) endPos = inURIString.length();
		
		mPath = inURIString.substring(begPos, endPos);
		
		// Make sure the mPath is in a good form.
		URIWrapper.encode(mPath);  // Will throw an error if not.

		// Get Query Portion.
		begPos = endPos + 2;
		if (begPos <= inURIString.length()) {
			mQuery = inURIString.substring(begPos);
			
			if (0 == mQuery.length()) {
				mQuery = null;
				throw new URISyntaxException(inURIString, "Encountered empty URI query");
			}
		}
		
		syncQueryParams(TO_MAP);
	}
	
	public URIWrapper(String inScheme, String inHost, String inPath) throws URISyntaxException {
		this(inScheme, inHost, inPath, null);
	}
	
	public URIWrapper(String inScheme, String inHost, String inPath, String inQuery) throws URISyntaxException {
		
		// First make sure Path can be encoded.
		URIWrapper.encode(inPath);

		// Now set the raw values.
		mScheme = inScheme;
		mHost = inHost;
		mPath = (inPath.charAt(0) == '/' ? inPath.substring(1) : inPath);
		mQuery = inQuery;

		syncQueryParams(TO_MAP);
	}
	
	// Add query parameter with no value.
	public void addQueryParam(String inName) {
		addQueryParam(inName, null);
	}
	
	// Add query parameter with value (optional)
	public void addQueryParam(String inName, String inValue) {
		String value = inValue;
		
		try {
			value = URIWrapper.encode(inValue);
		} catch (URISyntaxException e) {
			// Did our best.  Just pass through.
		}
		
		mQueryParams.put(inName, inName + (null != inValue ? ("=" + value) : ""));

		bNeedsUpdate = true;
	}

	// Check for existence of Query Param.
	public boolean hasQueryParam(String inName) {
		return mQueryParams.containsKey(inName);
	}

    // return the value of the param string.
	// If param doesn't exist, return null. 
	// If param exists but has not value, return empty string
	public String getQueryParamValue(String inName) {
		String retval = null;
		
		String paramValue = mQueryParams.get(inName);
		if (null != paramValue) {
			String[] pieces = paramValue.split("=");
			
			retval = (pieces.length > 1 ? pieces[1] : "");
		}
		
		return retval;
	}
	
	// Remove Query Param
	public void deleteQueryParam(String inName) {
		mQueryParams.remove(inName);
		
		bNeedsUpdate = true;
	}
	
	// Clear out all Query Params
	public void clearQueryParams() {
		mQueryParams.clear();
		
		bNeedsUpdate = true;
	}
	
	public String getScheme() { return mScheme; }
	public String getHost() { return mHost; }
	public String getRawPath() { return mPath; }
	public String getPath() { 
		String retval = null;

		try {
			retval = URIWrapper.encode(mPath);
		} catch (URISyntaxException e) {
			// Should never get here because we already sanitized the raw form.
		}

		return retval;
	}
	public String getQuery() { return constructQueryString(); }
	
	public void setPath(String inPath) throws URISyntaxException {
		mPath = URIWrapper.encode(inPath.charAt(0) == '/' ? inPath.substring(1) : inPath);
	}
	
	public void setRawPath(String inPath) {
		mPath = inPath.charAt(0) == '/' ? inPath.substring(1) : inPath;
	}
	
	private static final int TO_MAP = 0;
	private static final int TO_URI = 1;

	private String constructQueryString() {
		// Putting what we have in the MAP into the URI.
		
		StringBuffer queryParamString = null;
		
		// Do we have any query params to put in URI?
		if ( ! mQueryParams.isEmpty()) {
			queryParamString = new StringBuffer();
			
			Iterator<String> iterator = mQueryParams.values().iterator();
			
			while (iterator.hasNext()) {
				String value = iterator.next();
				
				queryParamString.append(value + "&");
			}
			
			// Remove off trailing "&"
			queryParamString.deleteCharAt(queryParamString.length() - 1 );
		}
		
		return (null == queryParamString ? null : queryParamString.toString());
	}
	
	private void syncQueryParams(int inDirection) throws URISyntaxException {
		
		// Short circuit.
		if ( ! bNeedsUpdate ) {
			return;
		}
		
		if (null == mQueryParams) 
			mQueryParams = new LinkedHashMap<String, String>();

		switch (inDirection) {
		
		case TO_MAP:

			// Putting what we have in the URI into the MAP.
			
			if (null != mQuery && 0 != mQuery.length()) {
				// Yes. Now have to parse it.  Remove the first '?' Char and split on the '&'
				String[] queryParts = mQuery.split("&");

				// Now loop through the parts and put in map.
				for (int i = 0; i < queryParts.length; i++) {
					String[] itemParts = queryParts[i].split("=");
					
					mQueryParams.put(itemParts[0],  queryParts[i]);
				}
			}
			break;

		case TO_URI:
			
			mQuery = constructQueryString();
			
			break;
		default:
			// Internal really shouldn't happen, but if so just ignore.
		}
		
		bNeedsUpdate = false;
	}

	// Returns a URI without the query parameters.
	public URIWrapper toPathOnlyURIWrapper() throws URISyntaxException {
		return new URIWrapper(mScheme, mHost, mPath, null);
	}

	public String toString() {
		String retval = null;
		
		try {
			StringBuilder builder = new StringBuilder();
			builder.append(mScheme + "://");
			builder.append(mHost);
			
			if (null != mPath) builder.append("/" + URIWrapper.encode(mPath));
			if (null != mQueryParams && ! mQueryParams.isEmpty()) {
				builder.append("?" + constructQueryString());
			}
			
			retval = builder.toString();
		} catch (URISyntaxException e) {
			retval = toRawString();
		}
		
		return retval.toString();
	}
	
	public String toRawString() {
		StringBuilder retval = new StringBuilder();
		
		retval.append(mScheme + "://");
		retval.append(mHost);
		
		if (null != mPath) retval.append("/" + mPath);
		if (null != mQueryParams && ! mQueryParams.isEmpty()) {
			retval.append("?" + constructQueryString());
		}
		
		return retval.toString();
	}
	
}
