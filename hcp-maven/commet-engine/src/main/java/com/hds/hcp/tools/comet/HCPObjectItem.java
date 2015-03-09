package com.hds.hcp.tools.comet;

import java.io.InputStream;
import java.net.URISyntaxException;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;

import com.hds.hcp.tools.comet.utils.URIWrapper;

public class HCPObjectItem extends BaseWorkItem {

	public HCPObjectItem(String inName, String inBaseFolder) throws URISyntaxException {
		// NOTE: Not using inBaseFolder
		
		mHandle = new URIWrapper(inName);
	}
	
	public HCPObjectItem(BaseWorkItem inName, BaseWorkItem inBaseFolder) throws URISyntaxException {
		mHandle = new URIWrapper(inName.getName());
	}

	public HCPObjectItem(URIWrapper inURI, String inEncUser, String inEncPwd, HttpClient inHttpClient) {
		mHandle = inURI;
		setHttpClient(inHttpClient);
		setCredentials(inEncUser, inEncPwd);
	}
	
	public void setURI(URIWrapper inURIWrapper) {
		mHandle = inURIWrapper;
	}
	
	public URIWrapper getURI() { return (URIWrapper)mHandle; }

	private String mCredentials;
	private HttpClient mHttpClient = null;
	private BasicHeader mCredHttpHeader;

	public void setCredentials(String inCredentials) {
		mCredentials = new String(inCredentials);
		mCredHttpHeader = new BasicHeader("Authorization", "HCP " + getCredentials());
	}
	
	public void setCredentials(String inEncUser, String inEncPwd) {
		setCredentials(inEncUser + ":" + inEncPwd);
	}
	
	public String getCredentials() { return mCredentials; }
	
	public void setHttpClient(HttpClient inClient) { mHttpClient = inClient; }
	
	public HttpClient getHttpClient() { return mHttpClient; }
	
	public boolean exists() throws Exception {
		boolean retval = false;
		
		/*
		 * Setup the HEAD request to check existence of the object.
		 */
		HttpHead httpRequest = new HttpHead(getName());

		// Construct the most appropriate HCP Authentication
		// TODO: This will not support older credential format.
		httpRequest.setHeader(mCredHttpHeader);

		/*
		 * Now execute the HEAD request.
		 */
		HttpResponse httpResponse = mHttpClient.execute(httpRequest);
	        
		// For debugging purposes, dump out the HTTP Response.
		// TODO - how to let this module know to do this.
//		if (mProps.shouldDumpHTTPHeaders())
//			HCPUtils.dumpHttpResponse(httpResponse);

		// Clean up after ourselves and release the HTTP connection to the connection manager.
		EntityUtils.consume(httpResponse.getEntity());
		
		// If we don't have a 404 (Not Found) response, need to further dig..
		if (404 != httpResponse.getStatusLine().getStatusCode()) {
			retval = true; // Be optimistic.
			
			// If the return code is anything BUT 200 range indicating success, we have to throw an exception.
			if (2 != (int)(httpResponse.getStatusLine().getStatusCode() / 100))
			{
				throw new HttpResponseException(httpResponse.getStatusLine().getStatusCode(),
						"Unexpected status returned from " + httpRequest.getMethod() + " ("
						+ httpResponse.getStatusLine().getStatusCode() + ": " 
						+ httpResponse.getStatusLine().getReasonPhrase() + ")");
			}
		}
		
		return retval;
	}

	@Override
	public boolean delete() throws Exception {
		boolean retval = true;
		
		/*
		 * Setup the HEAD request to check existence of the object.
		 */
		HttpDelete httpRequest = new HttpDelete(getName());

		// Construct the most appropriate HCP Authentication
		// TODO: This will not support older credential format.
		httpRequest.setHeader(mCredHttpHeader);

		/*
		 * Now execute the DELETE request.
		 */
		HttpResponse httpResponse = mHttpClient.execute(httpRequest);
	        
		// For debugging purposes, dump out the HTTP Response.
		// TODO
//		if (mProps.shouldDumpHTTPHeaders())
//			HCPUtils.dumpHttpResponse(httpResponse);

		// If the return code is anything BUT 200 range indicating success, we have to throw an exception.
		if (2 == (int)(httpResponse.getStatusLine().getStatusCode() / 100))
		{
			retval = false;
		}
		
		// Clean up after ourselves and release the HTTP connection to the connection manager.
		EntityUtils.consume(httpResponse.getEntity());
		
		return retval;
	}

	@Override
	public boolean setWritable() {
		// UNSUPPORTED
		return false;
	}

	@Override
	public String getName() {
		return mHandle.toString();
	}

	@Override
	public boolean isContainer() {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public boolean isChildOfContainer(BaseWorkItem inParent) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public InputStream getItemInputStream() throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

}
