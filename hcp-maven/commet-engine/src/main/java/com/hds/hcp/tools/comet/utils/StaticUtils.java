package com.hds.hcp.tools.comet.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Logger;

public class StaticUtils {
	public static String HCP_GATEWAY = "/rest";
	public static String HTTP_SEPARATOR = "/";

	public static String convertFilePatternToRegExpr(String inFilePattern) {
		String item;
		
		// See if we have a raw Regular Expression as indicated with
		//  the first char of a '/'.
		if (inFilePattern.startsWith("#")) {
			item = inFilePattern.substring(1); // Strip off the indicator.
		} else {
			// No advanced stuff, so translate like a file wild card.
		    item = inFilePattern.replace(".", "\\.") // Keep '.' as is.
			                    .replace("*", ".*")  // Change to regexp 0 or more any chars.
				                .replace("?", ".")   // Change to regexp any 1 char.
				                .replace("!", "^");  // Change the bash not to regexpr 
		}
		
		return item;
	}

	/*
	 * Returns input string with environment variable references expanded, e.g. $SOME_VAR or ${SOME_VAR}
	 */
	public static String resolveEnvVars(String input)
	{
	    if (null == input)
	    {
	        return null;
	    }
	    // match ${ENV_VAR_NAME} or $ENV_VAR_NAME
	    Pattern p = Pattern.compile("\\$\\{(\\w+)\\}|\\$(\\w+)");
	    Matcher m = p.matcher(input); // get a matcher object
	    StringBuffer sb = new StringBuffer();
	    while(m.find()){
	        String envVarName = null == m.group(1) ? m.group(2) : m.group(1);
	        String envVarValue = System.getenv(envVarName);
	        m.appendReplacement(sb, null == envVarValue ? "" : envVarValue);
	    }
	    m.appendTail(sb);
	    return sb.toString();
	}
	
    public static void TRACE_METHOD_ENTER(Logger inLogger, String inMsg) {
    	if (inLogger.isTraceEnabled()) {
    		StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
    		inLogger.trace("ENTERING: {} [{}]", caller.getMethodName(), inMsg);
    	}
    }
    
    public static void TRACE_METHOD_ENTER(Logger inLogger) {
    	if (inLogger.isTraceEnabled()) {
    		StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
    		inLogger.trace("ENTERING: {}", caller.getMethodName());
    	}
    }

    public static void TRACE_METHOD_EXIT(Logger inLogger, String inMsg) {
    	if (inLogger.isTraceEnabled()) {
    		StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
    		inLogger.trace("EXITING: {} [{}]", caller.getMethodName(), inMsg);
    	}
    }
    
    public static void TRACE_METHOD_EXIT(Logger inLogger) {
    	if (inLogger.isTraceEnabled()) {
    		StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
    		inLogger.trace("EXITING: {}", caller.getMethodName());
    	}
    }
}
