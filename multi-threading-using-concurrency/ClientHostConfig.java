package test.multi;


public class ClientHostConfig {

	
    private String host;

    private String port;

    private String protocol;

    private String userName;

    private String password;
    
    static ClientHostConfig  clientHostConfig = null;
    
    public static ClientHostConfig getClientHostConfig() {
    	if(clientHostConfig!=null) {
    		clientHostConfig = new ClientHostConfig();
    	}
		return clientHostConfig;
	}
	private ClientHostConfig(){
        host = System.getProperty("hcp.host");
        port = System.getProperty("hcp.port");
        protocol = System.getProperty("hcp.protocol");
        userName = System.getProperty("hcp.username");
        password = System.getProperty("hcp.password");
    }

	public String getHost() {
		return host;
	}

	public String getPort() {
		return port;
	}

	public String getProtocol() {
		return protocol;
	}

	public String getUserName() {
		return userName;
	}

	public String getPassword() {
		return password;
	}



    

}
