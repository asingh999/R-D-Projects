package com.hds.hcp.tools.comet.generator;

import java.net.URI;
import java.net.URISyntaxException;
import java.lang.IllegalArgumentException;

import com.hds.hcp.tools.comet.utils.StaticUtils;
import com.hds.hcp.tools.comet.utils.URIWrapper;


public class SystemMetadataContainer {
	
	public SystemMetadataContainer(String scheme, String inHost, BaseGeneratorProperties inProps) throws URISyntaxException {
		mObjectURI = new URIWrapper(scheme, inHost, StaticUtils.HCP_GATEWAY, null);
		
		if (null != inProps) {
			bShouldUpdate = inProps.shouldUpdateSystemMetadata();
		}
	}

	public SystemMetadataContainer(URI inURI, BaseGeneratorProperties inProps) throws URISyntaxException {
		this(inURI.getScheme(), inURI.getHost(), inProps);

		setObjectPath(inURI.getRawPath());
	}

	public SystemMetadataContainer(URIWrapper inURI, BaseGeneratorProperties inProps) throws URISyntaxException {
		mObjectURI = inURI;
		
		if (null != inProps) {
			bShouldUpdate = inProps.shouldUpdateSystemMetadata();
		}
	}

	private Boolean bShouldUpdate = Boolean.FALSE;
	private URIWrapper mObjectURI;
	private String sCredentials = "";

	private class HCPHostSpecification{
	
		private String sTenant, sNamespace, sHCPName;

		public HCPHostSpecification(String inHost) {
			setHost(inHost);
		}
		
		public void setHost(String inHost) throws IllegalArgumentException {
			String[] parts = inHost.split("\\.");
			
			if (null == parts || parts.length < 3) {
				throw new IllegalArgumentException("Minimum number of 3 '.' separated elements not found");
			}
			
			String namespace, tenant, HCPName;
			
			namespace = parts[0];
			tenant = parts[1];
			HCPName = parts[2];
			for (int i = 3; i < parts.length; i++) {
				HCPName = HCPName + "." + parts[i];
			}
			
			setHost(tenant, namespace, HCPName);
		}
		
		public void setHost(String inTenant, String inNamespace, String inHCPName)
				throws IllegalArgumentException {
			// Save these just in case.
			String origTenant = sTenant;
			String origNamespace = sNamespace;
			String origHCPName = sHCPName;
			
			sTenant = inTenant; sNamespace = inNamespace; sHCPName = inHCPName;

			// Now validate the values passed in.
			try {
				// Note: not assigning to a variable on purpose. Just want to use the URIWrapper
				//  to do a sanity check and assigning to a variable causes compile warning.
				new URIWrapper("http://" + getHost());
			} catch (URISyntaxException e) {
				sTenant = origTenant; sNamespace = origNamespace; sHCPName = origHCPName;

				throw new IllegalArgumentException("Provided input is improper for DNS host name in URL");
			}
		}
		
		public String getHost() { return sNamespace + "." + sTenant + "." + sHCPName; }

		public void setHCPName(String inHCPName) {sHCPName = inHCPName;}
		public String getHCPName() { return sHCPName; }

		public void setTenant(String inTenant) {sTenant = inTenant;}
		public String getTenant() { return sTenant; }

		public void setNamespace(String inNamespace) {sNamespace = inNamespace;}
		public String getNamespace() { return sNamespace; }
	}
	
	
	public Boolean shouldUpdateSystemMetadata() {
		return bShouldUpdate;
	}
	
	public String getHCPName() { 
		HCPHostSpecification host = new HCPHostSpecification(mObjectURI.getHost());
		
		return host.getHCPName();
	};
	
	public void setHCPName(String inParam) throws IllegalArgumentException {
		HCPHostSpecification host = new HCPHostSpecification(mObjectURI.getHost());
		host.setHCPName(inParam);
		
		URIWrapper origURI = mObjectURI;
		
		try {
			mObjectURI = new URIWrapper(origURI.getScheme(), host.getHost(), origURI.getRawPath(), origURI.getQuery());
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("URI could not be constructed with specified tenant (" + inParam + ")");
		}
	};

	public String getTenant() { 
		HCPHostSpecification host = new HCPHostSpecification(mObjectURI.getHost());
		
		return host.getTenant();
	};
	public void setTenant(String inParam) throws IllegalArgumentException {
		HCPHostSpecification host = new HCPHostSpecification(mObjectURI.getHost());
		host.setTenant(inParam);
		
		URIWrapper origURI = mObjectURI;
		
		try {
			mObjectURI = new URIWrapper(origURI.getScheme(), host.getHost(), origURI.getRawPath(), origURI.getQuery());
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("URI could not be constructed with specified tenant (" + inParam + ")");
		}
	};

	public String getNamespace() { 
		HCPHostSpecification host = new HCPHostSpecification(mObjectURI.getHost());
		
		return host.getNamespace();
	};
	public void setNamespace(String inParam) throws IllegalArgumentException {
		HCPHostSpecification host = new HCPHostSpecification(mObjectURI.getHost());
		host.setNamespace(inParam);
		
		URIWrapper origURI = mObjectURI;
		
		try {
			mObjectURI = new URIWrapper(origURI.getScheme(), host.getHost(), origURI.getRawPath(), origURI.getQuery());
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("URI could not be constructed with specified namespace (" + inParam + ")");
		}
	};

	public void setCredentials(String inEncodedUserName, String inEncodedPassword) {
		sCredentials = inEncodedUserName + ":" + inEncodedPassword;
	}
	public void setCredentials(String inCredentials) { sCredentials = inCredentials; }
	public String getCredentials() { return sCredentials; }
	
	public String getObjectPath() { return mObjectURI.getPath(); };
	public String getRawObjectPath() { return mObjectURI.getRawPath(); };
	public void setObjectPath(String inPath) throws URISyntaxException { 
		boolean addGateway = inPath.indexOf(StaticUtils.HCP_GATEWAY) == 0 ? false : true;
		boolean addHTTPSep = inPath.indexOf(StaticUtils.HTTP_SEPARATOR) == 0 ? false : true;

		mObjectURI.setPath((addGateway ? StaticUtils.HCP_GATEWAY : "") + (addHTTPSep ? StaticUtils.HTTP_SEPARATOR : "") + inPath);
	};

	public String getRetention() { return mObjectURI.getQueryParamValue("retention"); };
	public void setRetention(String inParam) { 
		if (null == inParam) {
			mObjectURI.deleteQueryParam("retention");
		} else {
			mObjectURI.addQueryParam("retention", inParam);
		}
	};
	public boolean isRetentionSet() { return mObjectURI.hasQueryParam("retention"); };

	public Boolean getShredding() {
		return mObjectURI.hasQueryParam("shred") ? new Boolean(mObjectURI.getQueryParamValue("shred")) : null;
	};
	public void setShredding(Boolean inParam) { 
		if (null == inParam) {
			mObjectURI.deleteQueryParam("shred");
		} else {
			mObjectURI.addQueryParam("shred", inParam.toString());
		}
	};
	public boolean isShreddingSet() { return mObjectURI.hasQueryParam("shred"); };

	public Boolean getIndexing() {
		return mObjectURI.hasQueryParam("index") ? new Boolean(mObjectURI.getQueryParamValue("index")) : null;
	};
	public void setIndexing(Boolean inParam) { 
		if (null == inParam) {
			mObjectURI.deleteQueryParam("index");
		} else {
			mObjectURI.addQueryParam("index", inParam.toString());
		}
	};
	public boolean isIndexingSet() { return mObjectURI.hasQueryParam("index"); };

	public Boolean getHold() {
		return mObjectURI.hasQueryParam("hold") ? new Boolean(mObjectURI.getQueryParamValue("hold")) : null;
	};
	public void setHold(Boolean inParam) { 
		if (null == inParam) {
			mObjectURI.deleteQueryParam("hold");
		} else {
			mObjectURI.addQueryParam("hold", inParam.toString());
		}
	};
	public boolean isHoldSet() { return mObjectURI.hasQueryParam("hold"); };

	public URIWrapper toURIWrapper() throws URISyntaxException { return new URIWrapper(mObjectURI); };
	public URIWrapper toPathOnlyURI() throws URISyntaxException { return mObjectURI.toPathOnlyURIWrapper(); };
	
	public void fromURI(URI inURI) throws URISyntaxException { 
		URIWrapper newURI = new URIWrapper(inURI.getScheme(), inURI.getHost(), inURI.getRawPath(), mObjectURI.getQuery());
		
		mObjectURI = newURI;
	}
}
