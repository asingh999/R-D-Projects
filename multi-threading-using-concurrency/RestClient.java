package test.multi;


public class RestClient {


    public String sendXmlMessage(String file, String restUrl, String userName, String Password) {

       System.out.println(String.format("Posting file %s to url %s", file, restUrl));
       return "Test Success";

    }

}
