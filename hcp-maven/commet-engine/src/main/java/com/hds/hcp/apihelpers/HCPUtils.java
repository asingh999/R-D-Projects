/*
 *   Copyright (c) 2011 Hitachi Data Systems, Inc.
 *
 *   Permission is hereby granted to  this software and associated
 *   documentation files (the "Software"), subject to the terms and
 *   conditions of the Sample Source Code License (SSCL) delivered
 *   with this Software. If you do not agree to the terms and
 *   conditions of the SSCL,
 *
 *     (i)  you must close this file and delete all copies of the
 *          Software, and
 *     (ii) any permission to use the Software is expressly denied.
 *
 */

package com.hds.hcp.apihelpers;

import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

public class HCPUtils {
	
	// Static strings for HCP authentication.
	public static final String HTTP_AUTH_HEADER = "Authorization";
	
	// Legacy values for HCP Authentication
	public static final String NS_AUTH_COOKIE_LEGACY = "hcp-ns-auth";
	public static final String API_AUTH_COOKIE_LEGACY = "hcp-api-auth";
	
	public static boolean mDumpHeaders = true;

	/**
	 * This class is the X509 Trust Manager for SSL connections. This is a
	 * promiscuous implementation that indicates that all SSL keys are trusted.
	 */
	private static class MyX509TrustManager implements X509TrustManager {

		MyX509TrustManager() {
		}

		/*
		 * Delegate to the default trust manager.
		 */
		public void checkClientTrusted(X509Certificate[] chain, String authType)
		         throws CertificateException {
		    // I am easy
		}

		/*
		 * Delegate to the default trust manager.
		 */
		public void checkServerTrusted(X509Certificate[] chain, String authType)
		              throws CertificateException {
		     // I am easy
		}

		/*
		 * Merely pass this through.
		 */
		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}
	}
	
	/*
	 * Provide the ability to override port numbers for HTTP/HTTPS
	 */
	private static int HTTPSPort = 443;
	private static int HTTPPort = 80;
	
	public static int getHTTPSPort() { return HTTPSPort; };
	public static void setHTTPSPort(int port) {
		HTTPSPort = port;
	}
	
	public static int getHTTPPort() { return HTTPPort; };
	public static void setHTTPPort(int port) {
		HTTPPort = port;
	}
	
	public static void clearDumpHeaders() { mDumpHeaders = false; };
	public static void setDumpHeaders() { mDumpHeaders = true; };

	public static final Integer DEFAULT_CONNECTION_TIMEOUT=5000; // Number of milliseconds for timeout
	public static final Integer DEFAULT_MAX_CONNECTIONS_PER_ROUTE=50;
	public static final Integer DEFAULT_MAX_CONNECTIONS=1000;
	
	/**
	 * Initialize and return an HttpClient interface connection object for this program. 
	 * This will ready the connection to be able to handle either HTTP or HTTPS
	 * URL requests.
	 */
	public static HttpClient initHttpClient() throws Exception
	{
		return initDefaultHttpClient(DEFAULT_CONNECTION_TIMEOUT, DEFAULT_MAX_CONNECTIONS, DEFAULT_MAX_CONNECTIONS_PER_ROUTE);
	}
	
	public static HttpClient initHttpClient(int inConnectionTimeout, int inMaxConnections, int inMaxConnectionsPerRoute) throws Exception
	{
		return initDefaultHttpClient(inConnectionTimeout, inMaxConnections, inMaxConnectionsPerRoute);
	}
	
	/**
	 * Initialize and return an DefaultHttpClient connection object for this program. 
	 * This will ready the connection to be able to handle either HTTP or HTTPS
	 * URL requests.
	 */
	public static DefaultHttpClient initDefaultHttpClient(int inConnectionTimeout, int inMaxConnections, int inMaxConnectionsPerRoute ) throws Exception
	{
		/*
		 * Construct an SSLSocketFactory that defines a dummy TrustManager that to accept
		 *   any SSL requests.  Same behavior as if the -k option was requested on the
		 *   curl command on Unix/Linux.
		 */

		// Setup a TrustManager to a private one that essentially says everything is good.
		SSLContext sslcontext = SSLContext.getInstance("TLS");
		sslcontext.init(null, new TrustManager[] {new MyX509TrustManager()}, null);
		
		// Setup the SSLSocketFactory to allow all host names
		SSLSocketFactory sslSocketFactory = new SSLSocketFactory(sslcontext, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
		
		/*
		 * Setup the HTTP and HTTPS Schemes where HTTP points to the generic SocketFactory,
		 *  and HTTPS points to the to the SSLSocketFactory we just created.
		 */
		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", HTTPPort, PlainSocketFactory.getSocketFactory()));
		schemeRegistry.register(new Scheme("https", HTTPSPort, sslSocketFactory));
		
		/*
		 * Construct a ThreadSafeClientConnManager that uses the SchemeRegistry setup above.
		 * This will be used to create the HttpClient.
		 */
		PoolingClientConnectionManager connectionMgr = new PoolingClientConnectionManager(schemeRegistry);
	
		/*
		 * Setup the connection manager to use some HCP best practices:
		 *  Max Connections to specified per node and total maximum.
		 */
		connectionMgr.setDefaultMaxPerRoute(inMaxConnectionsPerRoute);
		connectionMgr.setMaxTotal(inMaxConnections);
		
		/*
		 * Setup Client Connection parameters.
		 */
		HttpParams clientParams = new BasicHttpParams();
		
		//+ Make sure connection request does not block indefinitely. Limit to this many milliseconds.
		HttpConnectionParams.setConnectionTimeout(clientParams, inConnectionTimeout);
		
		//+ Make sure it does not redirect. This can happen when invalid user credentials are provided.
		//+   This is a bug that is fixed in HCP 5.0. So this will not be need for HCP 5.0 clients.
		clientParams.setParameter(ClientPNames.HANDLE_REDIRECTS, false);
		
		//+ HCP sends back a cookie that requires BROWSER compatibility mode to avoid warning.
		clientParams.setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.BROWSER_COMPATIBILITY);
		
		/*
		 * Construct and return the HttpClient that will be used by all HTTP/HTTPS requests.
		 */
		return new DefaultHttpClient(connectionMgr, clientParams);
	}
	
	/**
	 * Helper routine to build an MD5 Digest of a string.
	 * @param sInStr - String to convert to MD5 encoding.
	 * @return String in encoded in MD5 form.
	 * @throws Exception
	 */
	public static String toMD5Digest(String sInStr) throws Exception {
		// If null just pass back null;
		if (null == sInStr) return null;
		
		StringBuffer mOutDigest = new StringBuffer("");

		try {
			MessageDigest pMD = MessageDigest.getInstance("MD5");

			byte pDigest[] = pMD.digest(sInStr.getBytes());
	    	  
			  // Convert to string.
			for(int i=0; i < pDigest.length; i++) {

				// Add a leading zero if necessary.  The toHexString does not do it.
				if (0 == (0xF0 & pDigest[i]))
					mOutDigest.append("0");
				
	    		mOutDigest.append(Integer.toHexString(0xFF & pDigest[i])); 
	    	}
	    }
	    catch (Exception e) {
	    	System.err.println("Error: " + e.getMessage());
	    	e.printStackTrace();
	    	throw e;
	    }
	      
	    return mOutDigest.toString();
	}

	/**
	 * Helper routine to build an base64 encoding of a string.
	 * @param sInStr - String to convert to base64 encoding.
	 * @return String in encoded in base64 form.
	 * @throws Exception
	 */
	public static String toBase64Encoding(String sInStr) {
		// If null just pass back null
		if (null == sInStr) return null;
		
		// Construct an B64Encoder that does not put line separators in the output string.
		byte separator[] = {};
		Base64 B64Encoder = new Base64(80, separator);
		
		return new String(B64Encoder.encode(sInStr.getBytes()));
	}
	
	/**
	 * Routine that dumps out the HTTP header to system out.
	 */

	public static void dumpHttpResponse(HttpResponse inHttpResponse) {

		// Dump out the status line
		System.out.println(inHttpResponse.getStatusLine());
	
		// Dump out Header values.
		Header responseHeaders[] = inHttpResponse.getAllHeaders();
	    
	    for (int i = 0; i < responseHeaders.length; i++) {
	    	System.out.println(responseHeaders[i].toString());
	    }
	}
}
