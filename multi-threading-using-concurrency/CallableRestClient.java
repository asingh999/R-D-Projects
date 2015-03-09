package test.multi;

import java.util.concurrent.Callable;


public class CallableRestClient  implements Callable<String> {

    private String file;
    private String url;
    private String userName;
    private String password;


    

    public CallableRestClient(String file, String url, String userName, String password){
        this.file=file;
        this.url=url;
        this.userName=userName;
    }

    @Override
    public String call() throws Exception {
    	//System.out.println("************************ Waiting before Starting *************************");
    	//Thread.sleep(3000);
    	RestClient birc = new RestClient();
        String response = birc.sendXmlMessage(file, url, userName, password);
        //TO DO: Save response
        //System.out.println("************************* Done *************************");
        return "";
    }

}
