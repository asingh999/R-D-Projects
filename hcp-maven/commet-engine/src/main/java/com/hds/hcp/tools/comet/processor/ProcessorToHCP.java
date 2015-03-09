package com.hds.hcp.tools.comet.processor;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.InputMismatchException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;

import com.hds.hcp.apihelpers.HCPUtils;
import com.hds.hcp.tools.comet.BaseWorkItem;
import com.hds.hcp.tools.comet.CometProperties;
import com.hds.hcp.tools.comet.MetadataExtractor;
import com.hds.hcp.tools.comet.WorkItemStatus.WriteStatus;
import com.hds.hcp.tools.comet.generator.CustomMetadataContainer;
import com.hds.hcp.tools.comet.generator.ObjectContainer;
import com.hds.hcp.tools.comet.generator.SystemMetadataContainer;
import com.hds.hcp.tools.comet.utils.StaticUtils;
import com.hds.hcp.tools.comet.utils.URIWrapper;

//
// TODO:
// TODO:  Need retry processor (5xx errors) and better error reporting for HTTP failures (4xx).
// TODO:

public class ProcessorToHCP extends BaseItemProcessor {
	private static Logger logger = LogManager.getLogger();

	// Local member variables.
	private Boolean bCanUseWholeIO = false;
	private Boolean bCanUseAuthorizationHeader = false;
	private Boolean bCanUseAnnotations = false;
	private HttpClient mHttpClient;
	private String sHCPVersion;
	private MetadataExtractor mMetadataGenerator;

	private static String UNKNOWN_HCP_VERSION = "<unknown>";
	private static final int HASH_READ_BUFFER_SIZE = 8*1024;

	public enum ObjectStateEnum {
		OBJECT_DOES_NOT_EXIST, OBJECT_ONLY, OBJECT_AND_CUSTOM_METADATA
	};

	private class ObjectState {
		private ObjectStateEnum mState = ObjectStateEnum.OBJECT_DOES_NOT_EXIST;
		private HashSet<String> mAnnotationList = new HashSet<String>();

		public ObjectStateEnum getState() {
			return mState;
		}

		public void setState(ObjectStateEnum inState) {
			mState = inState;
		}

		public void addAnnotation(String inName) {
			mAnnotationList.add(inName);
			mState = ObjectStateEnum.OBJECT_AND_CUSTOM_METADATA;
		}
	}

	private Header constructAuthorizationHeader(ObjectContainer inMetadata) {
		if (!bCanUseAuthorizationHeader) {
			// Pre-6.0 Style
			return new BasicHeader("Cookie", HCPUtils.NS_AUTH_COOKIE_LEGACY
					+ "=" + inMetadata.getSystemMetadata().getCredentials());
		}

		return new BasicHeader("Authorization", "HCP "
				+ inMetadata.getSystemMetadata().getCredentials());
	}

	/**
	 * Initialize the object by setting up internal data and establishing the
	 * HTTP client connection.
	 * 
	 * This routine is called by the ReadFromHCP and WriteToHCP routines, so
	 * calling it by the consumer of this class is unnecessary.
	 * 
	 */
	@Override
	public void initialize(CometProperties inProps, Object inInitBlob)
			throws Exception {

		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		if (!bIsInitialized) // Only initialize if we haven't already
		{
			mProps = inProps;

			if ( ! (inInitBlob instanceof DefaultHttpClient)) {
				throw new java.lang.ClassCastException(
						"Invalid object type for input parameter inInitBlob");
				
			}

			mHttpClient = (HttpClient) inInitBlob;

			mMetadataGenerator = new MetadataExtractor();

			// Determine if we can use Whole I/O by looking at the version
			// number.
			sHCPVersion = GetHCPVersion(mProps.getDestinationRootPath());
			logger.debug("Detected HCP Version: {}", sHCPVersion);
			try {
				Float tmpValue = Float.valueOf(sHCPVersion.substring(0, 3));
				if (4.1 <= tmpValue) {
					bCanUseWholeIO = true;

					logger.debug("Will be using Whole I/O for content");
				}
				if (6.0 <= tmpValue) {
					bCanUseAuthorizationHeader = true;
					bCanUseAnnotations = true;

					logger.debug("Will be using Authorization Header and Annotations");
				}
			} catch (NumberFormatException x) {
				if (!sHCPVersion.equals(UNKNOWN_HCP_VERSION)) {
					throw x;
				}
			}

			bIsInitialized = true;
		}

		StaticUtils.TRACE_METHOD_EXIT(logger);
	}

	/**
	 * Internal function to look at the HCP machine and determine the passed
	 * object exists on the system and whether it has custom-metadata or not. It
	 * accomplishes this by doing an HCP HTTP REST HEAD request to the object
	 * and looks at the metadata returned about the object.
	 * 
	 * @param inDestinationPath
	 *            URL to HCP file to retrieve state.
	 * @return ObjectState enumeration value as to the state on the HCP system.
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws HttpResponseException
	 */
	private String GetHCPVersion(String inDestination)
			throws ClientProtocolException, IOException, HttpResponseException {

		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		String retVal = UNKNOWN_HCP_VERSION;

		try {
			logger.debug("Getting HCP Version");
			URIWrapper toplevel = null;

			try {
				URIWrapper destURI = new URIWrapper(inDestination);

				toplevel = new URIWrapper(destURI.getScheme(),
						destURI.getHost(), "/rest", null);
			} catch (URISyntaxException x) {
				logger.warn("Failed to formulate URI to get HCP version.  Falling back to <unknown>.");

				StaticUtils.TRACE_METHOD_EXIT(logger);
				return retVal;
			}

			/*
			 * Setup the HEAD request to check existence top level path.
			 */
			HttpHead httpRequest = new HttpHead(toplevel.toString());

			// Must use old HCP Authentication since it is lowest common
			// denominator.
			httpRequest.setHeader("Cookie", HCPUtils.NS_AUTH_COOKIE_LEGACY
					+ "=" + mProps.getEncodedDestinationUserName() + ":"
					+ mProps.getEncodedDestinationPassword());

			/*
			 * Now execute the HEAD request.
			 */
			HttpResponse httpResponse = mHttpClient.execute(httpRequest);

			// For debugging purposes, dump out the HTTP Response.
			if (mProps.shouldDumpHTTPHeaders())
				HCPUtils.dumpHttpResponse(httpResponse);

			// If we don't have a 404 (Not Found) response, need to further
			// dig..
			if (404 != httpResponse.getStatusLine().getStatusCode()) {

				// If the return code is anything BUT 200 range indicating
				// success, we have to throw an exception.
				if (2 != (int) (httpResponse.getStatusLine().getStatusCode() / 100)) {
					// Clean up after ourselves and release the HTTP connection
					// to the connection manager.
					EntityUtils.consume(httpResponse.getEntity());

					throw new HttpResponseException(httpResponse
							.getStatusLine().getStatusCode(),
							"Unexpected status returned from "
									+ httpRequest.getMethod()
									+ " ("
									+ httpResponse.getStatusLine()
											.getStatusCode()
									+ ": "
									+ httpResponse.getStatusLine()
											.getReasonPhrase() + ")");
				}

				// Get the header value indicating custom metadata.
				Header cmHeader = httpResponse
						.getFirstHeader("X-HCP-SoftwareVersion");

				if (null != cmHeader)
					retVal = cmHeader.getValue();
			}

			// Clean up after ourselves and release the HTTP connection to the
			// connection manager.
			EntityUtils.consume(httpResponse.getEntity());

		} catch (Exception x) {
			logger.warn("Unable to determine HCP Version", x);
		}

		StaticUtils.TRACE_METHOD_EXIT(logger);
		return retVal;
	}

	@Override
	public boolean process(BaseWorkItem inItem) throws Exception {
		StaticUtils.TRACE_METHOD_ENTER(logger);

		if ( ! bIsInitialized ) {
			logger.fatal("Programming Error. Object Not Initialized");
			StaticUtils.TRACE_METHOD_EXIT(logger);
			return false;
		}
		
		if ( null == inItem) {
			logger.fatal("Invalid input parameter.  inElement is null");
			StaticUtils.TRACE_METHOD_EXIT(logger);
			return false;
		}
		
		if ( null == inItem.getHandle()) {
			logger.fatal("Invalid input parameter.  inElement.getHandle() is null");
			StaticUtils.TRACE_METHOD_EXIT(logger);
			return false;
		}

		logger.debug("Processing Item: {}", inItem.getName());
		
		mStatus.setObjectStatus(WriteStatus.WRITE_NOT_ATTEMPTED);
		mStatus.setCustomMetadataStatus(WriteStatus.WRITE_NOT_ATTEMPTED);

		if (!inItem.exists()) {
			logger.warn("Item does not exist: {} (Skipping)", inItem.getName());
			return false;
		}

		boolean retVal = WriteToHCP(inItem);
		
		StaticUtils.TRACE_METHOD_EXIT(logger);
		return retVal;
	}

	/**
	 * Internal function to look at the HCP machine and determine the passed
	 * object exists on the system and whether it has custom-metadata or not. It
	 * accomplishes this by doing an HCP HTTP REST HEAD request to the object
	 * and looks at the metadata returned about the object.
	 * 
	 * @param inSystemMetadata
	 *            URL to HCP file to retrieve state.
	 * @return ObjectState enumeration value as to the state on the HCP system.
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws HttpResponseException
	 */
	private ObjectState CheckExistanceOnHCP(ObjectContainer inMetadata)
			throws ClientProtocolException, IOException, HttpResponseException,
			URISyntaxException {

		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		// Going to assume the object doesn't exist.
		ObjectState retVal = new ObjectState();

		String URIString = inMetadata.getSystemMetadata().toPathOnlyURI()
				.toString();
		logger.debug("Checking for Object Existance on HCP: {}", URIString);

		/*
		 * Setup the HEAD request to check existence of the object.
		 */
		HttpHead httpRequest = new HttpHead(URIString);

		// Construct the most appropriate HCP Authentication
		httpRequest.setHeader(constructAuthorizationHeader(inMetadata));

		/*
		 * Now execute the HEAD request.
		 */
		HttpResponse httpResponse = mHttpClient.execute(httpRequest);

		// For debugging purposes, dump out the HTTP Response.
		if (mProps.shouldDumpHTTPHeaders())
			HCPUtils.dumpHttpResponse(httpResponse);

		// If we don't have a 404 (Not Found) response, need to further dig..
		if (404 != httpResponse.getStatusLine().getStatusCode()) {

			// If the return code is anything BUT 200 range indicating success,
			// we have to throw an exception.
			if (2 != (int) (httpResponse.getStatusLine().getStatusCode() / 100)) {
				// Clean up after ourselves and release the HTTP connection to
				// the connection manager.
				EntityUtils.consume(httpResponse.getEntity());

				throw new HttpResponseException(httpResponse.getStatusLine()
						.getStatusCode(), "Unexpected status returned from "
						+ httpRequest.getMethod() + " ("
						+ httpResponse.getStatusLine().getStatusCode() + ": "
						+ httpResponse.getStatusLine().getReasonPhrase() + ")");
			}

			/*
			 * At this point we know we have an object on the remote system, so
			 * need to determine if it has custom-metadata or not.
			 */
			// Get the header value indicating custom metadata.
			Header cmHeader = httpResponse
					.getFirstHeader("X-HCP-Custom-Metadata");

			retVal.setState(ObjectStateEnum.OBJECT_ONLY); // Assume only an
															// object exists.

			// if the header exists and
			if (Boolean.valueOf(cmHeader.getValue())) {

				// Check for the specific annotation existence.
				if (bCanUseAnnotations) {
					// If we can have annotations, check to see if the
					// annotation exists.
					Header annotationHeader = httpResponse
							.getFirstHeader("X-HCP-CustomMetadataAnnotations");

					if (null == annotationHeader) {
						// If this comes back null, it may be that annotations
						// have not yet been used, so HCP
						// is not returning back the header. But we still might
						// be asking to write the
						// default header.
						retVal.addAnnotation(CustomMetadataContainer.DEFAULT_ANNOTATION);
					} else {
						// Loop through all the annotation names and add it to
						// the return value.
						String annotations[] = annotationHeader.getValue()
								.split(";");

						for (int idx = 0; idx < annotations.length; idx++) {
							retVal.addAnnotation(annotations[idx]);
						}
					}
				} else {
					// Can't have annotation, but says there is custom metadata.
					retVal.setState(ObjectStateEnum.OBJECT_AND_CUSTOM_METADATA);
				}
			}
		}

		// Clean up after ourselves and release the HTTP connection to the
		// connection manager.
		EntityUtils.consume(httpResponse.getEntity());

		StaticUtils.TRACE_METHOD_EXIT(logger);
		return retVal;
	}
	
	private void ValidateHash(HttpResponse inResponse, InputStream inItemStream) throws Exception {
		// Only do it if we are configured to do it.
		if (mProps.getProcessorValidateHash()) {
			
			// Retrieve the header for the hash.
			Header hashValueHeader = inResponse.getFirstHeader("X-HCP-Hash");
			
			if (null == hashValueHeader) {
				logger.fatal("Configuration requested to validate object Hash, but hash value does not exist in response.");

				StaticUtils.TRACE_METHOD_EXIT(logger);
				
				throw new HttpResponseException(inResponse.getStatusLine()
						.getStatusCode(), "Expected HTTP header X-HCP-Hash is missing from response");
			}

			// There must be two parts:  HashType HashValue
			String parts[] = hashValueHeader.getValue().split(" ");
			if (parts.length != 2) {
				logger.fatal("Hash value header returned from HCP is in an unexpected format.");
				
				StaticUtils.TRACE_METHOD_EXIT(logger);
				
				throw new HttpResponseException(inResponse.getStatusLine()
						.getStatusCode(), "Unexpected HTTP header X-HCP-Hash format encountered");
			}
			
			String hashType = parts[0];
			String hashValue = parts[1];
			
			logger.debug("Received object Hash: [{}] {}", hashType, hashValue);

			// Compute the digest from the input stream.
			MessageDigest md = null;
			try {
				md = MessageDigest.getInstance(hashType);
			} catch (NoSuchAlgorithmException e) {
				logger.fatal("Hash type returned from HCP is not supported by this software.", e);
				
				throw e;
			}
			
			try {
				byte buffer[] = new byte[HASH_READ_BUFFER_SIZE];
				int numBytes;
				numBytes = inItemStream.read(buffer);
				while (0 < numBytes) {
					md.update(buffer, 0, numBytes);
					
					numBytes = inItemStream.read(buffer);
				} 
			} catch (IOException e) {
				logger.fatal("Unexpected I/O failure reading file to compute hash.", e);
				
				throw e;
			}

			// Build a string out of it.
			StringBuffer computedHashString = new StringBuffer();
			
			byte[] computedHash = md.digest();
			
			for (int i = 0; i < computedHash.length; i++) {
				String hex = Integer.toHexString(0xff & computedHash[i]).toUpperCase();
				if (hex.length() == 1) { computedHashString.append('0'); }
				computedHashString.append(hex);
			}
			
			logger.debug("Computed object Hash: [{}] {}", hashType, computedHashString.toString());
			
			if ( ! hashValue.equals(computedHashString.toString())) {
				logger.fatal("Computed Hash ({}) does not match HCP hash ({}).", computedHashString.toString(), hashValue);
				
				throw new InputMismatchException("HCP Ingest hash does not match computed hash.");
			}
		}
	}

	/**
	 * This method performs a PUT of an object data file and/or custom metadata
	 * depending on the state of the object on the HCP system and the
	 * configuration of the execution based on the properties file and the HCP
	 * system version.
	 * @throws Exception 
	 */
	private Boolean WriteToHCP(BaseWorkItem inItem)
			throws Exception {
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		Boolean retVal = Boolean.TRUE; // Let's be optimistic.

		try {
			LinkedList<ObjectContainer> objectMetadataList = mMetadataGenerator
					.getMetadataList(inItem);

			/*
			 * If we didn't get any, just exit now.
			 */
			if (null == objectMetadataList) {
				logger.info("No metadata returned for object. Skipping");

				StaticUtils.TRACE_METHOD_EXIT(logger);
				return Boolean.FALSE;
			}

			/*
			 * Loop through all the object metadata provided and process each
			 * one.
			 */

			ListIterator<ObjectContainer> iter = objectMetadataList
					.listIterator();
			while (iter.hasNext()) {
				ObjectContainer currentItem = iter.next();

				SystemMetadataContainer objSystemMetadata = currentItem
						.getSystemMetadata();

				// Fail if metadata has annotations returned from the modules,
				// but HCP doesn't support it.
				if (!bCanUseAnnotations
						&& (1 < currentItem.getCustomMetadata().count() || (1 == currentItem
								.getCustomMetadata().count() && null == currentItem
								.getCustomMetadata()
								.get(CustomMetadataContainer.DEFAULT_ANNOTATION)))) {
					// / Oops.. Asked to write annotations, but it is not
					// supported on the HCP.
					logger.fatal("Extraction modules returning annotations and the version of HCP does not support it");
					mStatus.setObjectStatus(WriteStatus.WRITE_FAILURE);

					StaticUtils.TRACE_METHOD_EXIT(logger);
					return Boolean.FALSE;
				}

				/*
				 * See what the state of this object is on HCP system. No
				 * object, object only, object with custom-metadata.
				 * 
				 * Act accordingly based on configuration.
				 */
				ObjectState oneObject = CheckExistanceOnHCP(currentItem);

				switch (oneObject.getState()) {

				case OBJECT_DOES_NOT_EXIST:
					logger.debug("Performing Addition of both Object and Metadata to {}", 
							objSystemMetadata.toPathOnlyURI().toString());
					WriteBothDataAndMetadataToHCP(currentItem,
							inItem);

					break;

				case OBJECT_ONLY:
					logger.debug("Performing Addition/Update of Metadata to {}",
							objSystemMetadata.toPathOnlyURI().toString());
					WriteCustomMetadataToHCP(currentItem);

					if (currentItem.getSystemMetadata()
							.shouldUpdateSystemMetadata()) {
						logger.debug("Requested to update System Metadata on existing object: {}",
								objSystemMetadata.toPathOnlyURI().toString());
						UpdateSystemMetadataToHCP(currentItem);
					}
					break;

				case OBJECT_AND_CUSTOM_METADATA:
					if (currentItem.getCustomMetadata()
							.shouldUpdateCustomMetadata()) {
						logger.debug("Performing Replacement/Update of Custom-Metadata to {}",
								objSystemMetadata.toPathOnlyURI().toString());
						WriteCustomMetadataToHCP(currentItem);
					} else {
						logger.debug("Custom Metadata already exists.  Not updated.");
					}

					if (currentItem.getSystemMetadata()
							.shouldUpdateSystemMetadata()) {
						logger.debug("Requested to update System Metadata on existing object: {}",
								objSystemMetadata.toPathOnlyURI().toString());
						UpdateSystemMetadataToHCP(currentItem);
					}
					break;

				default:
					logger.fatal("***** BUG! BUG! Silly function is not returning a invalid value!! *****");
					break;
				}
			}
		} catch (IOException x) {
			logger.error("Failed to write/update content to HCP (Skipping). ", x);

			retVal = Boolean.FALSE;
		}

		StaticUtils.TRACE_METHOD_EXIT(logger);
		return retVal;
	}

	private void WriteObjectToHCP(ObjectContainer inMetadata, BaseWorkItem inItem)
			throws FileNotFoundException, IOException, URISyntaxException, Exception {

		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		/*
		 * Setup the PUT request for just the object.
		 */
		HttpPut httpRequest = new HttpPut(inMetadata.getSystemMetadata().toURIWrapper().toString());

		InputStream itemStream = inItem.getItemInputStream();
		if (null == itemStream) {
			logger.error("No input stream available to write object content.  Object no object written.");

			throw new FileNotFoundException("No input stream availble for item.");
		}
		
		// Point the HttpRequest to the input stream.
		httpRequest.setEntity(new InputStreamEntity(itemStream, -1));

		// Construct the most appropriate HCP Authentication
		httpRequest.setHeader(constructAuthorizationHeader(inMetadata));

		/*
		 * Now execute the PUT request.
		 */
		HttpResponse httpResponse = mHttpClient.execute(httpRequest);

		// For debugging purposes, dump out the HTTP Response.
		if (mProps.shouldDumpHTTPHeaders())
			HCPUtils.dumpHttpResponse(httpResponse);

		// If the return code is anything BUT 200 range indicating success, we
		// have to throw an exception.
		if (2 != (int) (httpResponse.getStatusLine().getStatusCode() / 100)) {
			mStatus.setObjectStatus(WriteStatus.WRITE_FAILURE);

			// Clean up after ourselves and release the HTTP connection to the
			// connection manager.
			EntityUtils.consume(httpResponse.getEntity());

			StaticUtils.TRACE_METHOD_EXIT(logger);
			
			throw new HttpResponseException(httpResponse.getStatusLine()
					.getStatusCode(), "Unexpected status returned from "
					+ httpRequest.getMethod() + " ("
					+ httpResponse.getStatusLine().getStatusCode() + ": "
					+ httpResponse.getStatusLine().getReasonPhrase() + ")");
		}

		// See if we should validate the hash returned.
		try {
			ValidateHash(httpResponse, inItem.getItemInputStream());
		} catch (Exception e) {
			// Clean up after ourselves and release the HTTP connection to the
			// connection manager.
			EntityUtils.consume(httpResponse.getEntity());
			
			StaticUtils.TRACE_METHOD_EXIT(logger);
			
			throw e;
		}

		// Clean up after ourselves and release the HTTP connection to the
		// connection manager.
		EntityUtils.consume(httpResponse.getEntity());

		logger.debug("Successfully wrote \"{}\" to \"{}\"",
				inItem.getName(), inMetadata.getSystemMetadata().toPathOnlyURI().toString());
		mStatus.setObjectStatus(WriteStatus.WRITE_SUCCESS);

		StaticUtils.TRACE_METHOD_EXIT(logger);
	}

	private void UpdateSystemMetadataToHCP(ObjectContainer inMetadata)
			throws URISyntaxException, ClientProtocolException, IOException {

		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		/*
		 * First see if we actually have some system metadata we should try and
		 * update.
		 */
		String queryParam = inMetadata.getSystemMetadata().toURIWrapper().getQuery();
		if (null == queryParam || 0 == queryParam.length()) {
			logger.info("No System Metadata to update");

			StaticUtils.TRACE_METHOD_EXIT(logger);
			return;
		}

		/*
		 * Setup the POST request for just the object.
		 */
		HttpPost httpRequest = new HttpPost(inMetadata.getSystemMetadata()
				.toURIWrapper().toString());

		// Point the HttpRequest to the input stream.
		httpRequest.setEntity(new InputStreamEntity(new ByteArrayInputStream(
				queryParam.getBytes()), -1));

		// Construct the most appropriate HCP Authentication
		httpRequest.setHeader(constructAuthorizationHeader(inMetadata));

		/*
		 * Now execute the POST request.
		 */
		HttpResponse httpResponse = mHttpClient.execute(httpRequest);

		// For debugging purposes, dump out the HTTP Response.
		if (mProps.shouldDumpHTTPHeaders())
			HCPUtils.dumpHttpResponse(httpResponse);

		// If the return code is anything BUT 200 range indicating success, we
		// have to throw an exception.
		if (2 != (int) (httpResponse.getStatusLine().getStatusCode() / 100)) {
			mStatus.setObjectStatus(WriteStatus.WRITE_FAILURE);

			// Clean up after ourselves and release the HTTP connection to the
			// connection manager.
			EntityUtils.consume(httpResponse.getEntity());

			logger.error("Failed to update System Metadata on {}", 
					inMetadata.getSystemMetadata().toPathOnlyURI().toString());

			throw new HttpResponseException(httpResponse.getStatusLine()
					.getStatusCode(), "Unexpected status returned from "
					+ httpRequest.getMethod() + " ("
					+ httpResponse.getStatusLine().getStatusCode() + ": "
					+ httpResponse.getStatusLine().getReasonPhrase() + ")");
		}

		// Clean up after ourselves and release the HTTP connection to the
		// connection manager.
		EntityUtils.consume(httpResponse.getEntity());

		logger.debug("Successfully updated system metadata on \"{}\"",
				inMetadata.getSystemMetadata().toPathOnlyURI().toString());
		mStatus.setObjectStatus(WriteStatus.WRITE_SUCCESS);

		StaticUtils.TRACE_METHOD_EXIT(logger);
	}

	/**
	 * Method is the main entry point to write both Object data and
	 * Custom-Metadata. It will make a determination as to whether Whole Object
	 * I/O should be performed or single operations.
	 * 
	 * @param inSystemMetadata
	 * @param inSourceFile
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private void WriteBothDataAndMetadataToHCP(
			ObjectContainer inMetadata, BaseWorkItem inItem)
			throws FileNotFoundException, IOException, URISyntaxException, Exception {

		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		/*
		 * Either do Whole Object I/O or in two pieces.
		 */
		if (this.bCanUseWholeIO && 1 == inMetadata.getCustomMetadata().count()) {
			WriteWholeObjectToHCP(inMetadata, inItem);
		} else {
			logger.debug("Performing two operations to transfer object and Custom Metadata to HCP");

			// First write Object only.
			WriteObjectToHCP(inMetadata, inItem);

			/*
			 * Now that the Object is on the system, write the custom Metadata.
			 */
			WriteCustomMetadataToHCP(inMetadata);
		}
		
		StaticUtils.TRACE_METHOD_EXIT(logger);
	}

	/**
	 * Delete custom-metadata ONLY to an already existing object in HCP.
	 * 
	 * @param inMetadata
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private void DeleteCustomMetadataOnHCP(ObjectContainer inMetadata,
			String inName) throws IOException, URISyntaxException {
		logger.info("Deleting existing Custom Metadata on URL:  {}",
				inMetadata.getSystemMetadata().toPathOnlyURI().toString());

		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		/*
		 * Setup the PUT request specifying that you are writing
		 * custom-metadata.
		 */

		// First construct a URI.
		URIWrapper deleteURI = inMetadata.getSystemMetadata().toURIWrapper();
		deleteURI.clearQueryParams();
		deleteURI.addQueryParam("type", "custom-metadata");
		if (this.bCanUseAnnotations) {
			deleteURI.addQueryParam("annotation", "inName");
		}

		HttpDelete httpRequest = new HttpDelete(deleteURI.toString());

		// Construct the most appropriate HCP Authentication
		httpRequest.setHeader(constructAuthorizationHeader(inMetadata));

		/*
		 * Now execute the DELETE request.
		 */
		HttpResponse httpResponse = mHttpClient.execute(httpRequest);

		// For debugging purposes, dump out the HTTP Response.
		if (mProps.shouldDumpHTTPHeaders())
			HCPUtils.dumpHttpResponse(httpResponse);

		// If the return code is anything BUT 200 range indicating success, we
		// have to throw an exception.
		if (2 != (int) (httpResponse.getStatusLine().getStatusCode() / 100)) {
			HttpResponseException exception = new HttpResponseException(
					httpResponse.getStatusLine().getStatusCode(),
					"Unexpected status returned from "
							+ httpRequest.getMethod() + " ("
							+ httpResponse.getStatusLine().getStatusCode()
							+ ": "
							+ httpResponse.getStatusLine().getReasonPhrase()
							+ ")");

			logger.error(exception.getMessage());
		}

		// Clean up after ourselves and release the HTTP connection to the
		// connection manager.
		EntityUtils.consume(httpResponse.getEntity());
		
		StaticUtils.TRACE_METHOD_EXIT(logger);
	}

	/**
	 * Writes custom-metadata ONLY to an already existing object in HCP.
	 * 
	 * @param inSystemMetadata
	 * @param inSourceFile
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private void WriteCustomMetadataToHCP(ObjectContainer inMetadata)
			throws FileNotFoundException, IOException, URISyntaxException {

		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		boolean bWroteSomething = false;
		
		/*
		 * Get the metadata for the local file. If some constructed, then write
		 * it to the existing object on HCP system.
		 */
		Iterator<Entry<String, byte[]>> iter = inMetadata.getCustomMetadata()
				.iterator();
		while (iter.hasNext()) {
			Entry<String, byte[]> oneItem = iter.next();

			byte[] value = oneItem.getValue();
			if (null == value || 0 == value.length) {
				logger.info("No data for annotation ({}) for object: {}",
						oneItem.getKey(), inMetadata.getSystemMetadata().toURIWrapper().toString());

				if (inMetadata.getCustomMetadata()
						.shouldDeleteExistingCustomMetadataOnEmpty()) {
					this.DeleteCustomMetadataOnHCP(inMetadata, oneItem.getKey());
				}

				continue; // Go to next one, if any.
			}

			logger.debug("Sending PUT Custom Metadata ({}) to URL: {}",
					oneItem.getKey(), inMetadata.getSystemMetadata().toPathOnlyURI().toString());

			/*
			 * Setup the PUT request specifying that you are writing
			 * custom-metadata.
			 */

			// First construct a URI. Can't use any system metadata that may be
			// supplied for writing custom metadata.
			URIWrapper writeURI = inMetadata.getSystemMetadata().toURIWrapper();
			writeURI.clearQueryParams();
			writeURI.addQueryParam("type", "custom-metadata");
			if (bCanUseAnnotations)
				writeURI.addQueryParam("annotation", oneItem.getKey());

			HttpPut httpRequest = new HttpPut(writeURI.toString());

			// Point the HttpRequest to the input stream.
			httpRequest.setEntity(new InputStreamEntity(
					new ByteArrayInputStream(value), -1));

			// Construct the most appropriate HCP Authentication
			httpRequest.setHeader(constructAuthorizationHeader(inMetadata));

			/*
			 * Now execute the PUT request.
			 */
			HttpResponse httpResponse = mHttpClient.execute(httpRequest);

			// For debugging purposes, dump out the HTTP Response.
			if (mProps.shouldDumpHTTPHeaders())
				HCPUtils.dumpHttpResponse(httpResponse);

			// If the return code is anything BUT 200 range indicating success,
			// we have to throw an exception.
			if (2 != (int) (httpResponse.getStatusLine().getStatusCode() / 100)) {
				if (mStatus.getCustomMetadataStatus() != WriteStatus.WRITE_PARTIAL_SUCCESS)
					mStatus.setCustomMetadataStatus(WriteStatus.WRITE_FAILURE);

				// Clean up after ourselves and release the HTTP connection to
				// the connection manager.
				EntityUtils.consume(httpResponse.getEntity());

				throw new HttpResponseException(httpResponse.getStatusLine()
						.getStatusCode(), "Unexpected status returned from "
						+ httpRequest.getMethod() + " ("
						+ httpResponse.getStatusLine().getStatusCode() + ": "
						+ httpResponse.getStatusLine().getReasonPhrase() + ")");
			}

			// Clean up after ourselves and release the HTTP connection to the
			// connection manager.
			EntityUtils.consume(httpResponse.getEntity());

			bWroteSomething = true;
			
			// For now assume partial success.
			mStatus.setCustomMetadataStatus(WriteStatus.WRITE_PARTIAL_SUCCESS);
		}

		// If we write the custom-metadata, set success, otherwise if we get here
		//  there wasn't any metadata to write.
		if (bWroteSomething) {
			mStatus.setCustomMetadataStatus(WriteStatus.WRITE_SUCCESS);
		} else {
			logger.debug("No custom metadata provided for object.");
			// Status is already NOT_PROCESSED.
		}
		
		StaticUtils.TRACE_METHOD_EXIT(logger);
	}

	/**
	 * Write both the object and its custom-metadata (if any) to HCP using the
	 * Whole I/O feature of HCP 4.1. This mechanism performs the operation with
	 * a single HTTP request to HCP.
	 * 
	 * @param inSystemMetadata
	 *            Full URL to object to add to HCP.
	 * @param inSourceFile
	 *            Local file to write to HCP.
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private void WriteWholeObjectToHCP(ObjectContainer inMetadata,
			BaseWorkItem inItem) throws FileNotFoundException, IOException,
			URISyntaxException, Exception {

		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		if (1 != inMetadata.getCustomMetadata().count()) {
			logger.fatal("BUG BUG.  Should not be calling WriteWholeObjectIO unless there is only one annotation");

			StaticUtils.TRACE_METHOD_EXIT(logger);
			return;
		}

		// Create our Custom-Metadata
		Entry<String, byte[]> cmInfo = inMetadata.getCustomMetadata()
				.iterator().next();

		// If there is no custom metadata, then don't do a Whole I/O. HCO isn't
		// happy with no CM when
		// metadata validation is enabled.
		byte[] cmData = (cmInfo == null ? null : cmInfo.getValue());
		if (null == cmData || 0 == cmData.length) {

			logger.debug("No Custom Metdata. Only writing Object, not doing whole I/O");

			// Write Data.
			WriteObjectToHCP(inMetadata, inItem);

			// All done.
			return;
		}

		/*
		 * Setup the PUT request specifying it is whole-object I/O
		 */

		// First construct a URI with whole-object and annotation added.
		URIWrapper writeURI = inMetadata.getSystemMetadata().toURIWrapper();
		writeURI.addQueryParam("type", "whole-object");
		if (bCanUseAnnotations)
			writeURI.addQueryParam("annotation", inMetadata.getCustomMetadata()
					.iterator().next().getKey());

		HttpPut httpRequest = new HttpPut(writeURI.toString());

		// Construct the most appropriate HCP Authentication
		httpRequest.setHeader(constructAuthorizationHeader(inMetadata));

		/*
		 * Construct our Whole I/O payload consisting of object and custom
		 * metadata
		 */

		// Create our data file InputStream
		InputStream dataFile = inItem.getItemInputStream();
		
		// Create our custom metadata stream
		ByteArrayInputStream cmDataIS = new ByteArrayInputStream(cmData);

		// Point the HttpRequest to the input stream with a Sequenced stream
		httpRequest.setEntity(new InputStreamEntity(new SequenceInputStream(
				dataFile, cmDataIS), -1));

		// Put the size of the data portion of the whole object into the
		// X-HCP-Size header value.
		httpRequest.setHeader("X-HCP-Size",
				String.valueOf(dataFile.available()));

		/*
		 * Now execute the PUT request.
		 */
		HttpResponse httpResponse = mHttpClient.execute(httpRequest);

		// For debugging purposes, dump out the HTTP Response.
		if (mProps.shouldDumpHTTPHeaders())
			HCPUtils.dumpHttpResponse(httpResponse);

		// If the return code is anything BUT 200 range indicating success, we
		// have to throw an exception.
		if (2 != (int) (httpResponse.getStatusLine().getStatusCode() / 100)) {
			mStatus.setObjectStatus(WriteStatus.WRITE_FAILURE);
			mStatus.setCustomMetadataStatus(WriteStatus.WRITE_FAILURE);

			// Clean up after ourselves and release the HTTP connection to the
			// connection manager.
			EntityUtils.consume(httpResponse.getEntity());

			throw new HttpResponseException(httpResponse.getStatusLine()
					.getStatusCode(), "Unexpected status returned from "
					+ httpRequest.getMethod() + " ("
					+ httpResponse.getStatusLine().getStatusCode() + ": "
					+ httpResponse.getStatusLine().getReasonPhrase() + ")");
		}

		// See if we should validate the hash returned.
		try {
			ValidateHash(httpResponse, inItem.getItemInputStream());
		} catch (HttpResponseException e) {
			// Clean up after ourselves and release the HTTP connection to the
			// connection manager.
			EntityUtils.consume(httpResponse.getEntity());

			dataFile.close();
			
			StaticUtils.TRACE_METHOD_EXIT(logger);
			
			throw e;
		}

		// Clean up after ourselves and release the HTTP connection to the
		// connection manager.
		EntityUtils.consume(httpResponse.getEntity());

		dataFile.close();

		mStatus.setObjectStatus(WriteStatus.WRITE_SUCCESS);
		mStatus.setCustomMetadataStatus(WriteStatus.WRITE_SUCCESS);
		
		StaticUtils.TRACE_METHOD_EXIT(logger);
	}
}
