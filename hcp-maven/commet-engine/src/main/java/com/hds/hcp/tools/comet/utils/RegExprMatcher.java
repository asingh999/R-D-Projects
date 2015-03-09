package com.hds.hcp.tools.comet.utils;

import java.util.regex.Pattern;

public class RegExprMatcher {
	public RegExprMatcher(String inRegExpr) {
		pattern = Pattern.compile(inRegExpr);
	}
	private Pattern pattern;
	
	public boolean isMatch(String inName) { return pattern.matcher(inName).matches(); }
}
