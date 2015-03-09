package com.prudential.comet;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class AllianceCallMetadata {
	public String WAVEPath;
	public Integer CallID;
	public Date DateTime;
	public Integer AgentId;
	public String Agent;
	public Integer Extension;
	public String ANI;   // Inbound Only
	public Integer DNIS;  // Inbound Only
	public String Skill; // Inbound Only
	public Long Phone; // Outbound Only

	public static final String FIELD_SEPARATOR = "|";
	
	public String toString() {
		StringBuilder outString = new StringBuilder();

		// First output all the common fields.
		outString.append(null != WAVEPath ? WAVEPath : "");
		outString.append(FIELD_SEPARATOR);
		outString.append(null != DateTime ? new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(DateTime) : "");
		outString.append(FIELD_SEPARATOR);
		outString.append(null != CallID ? CallID : "");
		outString.append(FIELD_SEPARATOR);
		outString.append(null != AgentId ? AgentId : "");
		outString.append(FIELD_SEPARATOR);
		outString.append(null != Agent ? Agent : "");
		outString.append(FIELD_SEPARATOR);
		outString.append(null != Extension ? Extension : "");
		outString.append(FIELD_SEPARATOR);

		// If ANI exists, must be an Inbound Call
		if (null != ANI) {
			outString.append(null != ANI ? ANI : "");
			outString.append(FIELD_SEPARATOR);
			outString.append(null != DNIS ? DNIS : "");
			outString.append(FIELD_SEPARATOR);
			outString.append(null != Skill ? Skill : "");
		} else {
			// Must be Outbound
			outString.append(null != Phone ? Phone : "");
		}
		
		return outString.toString();
	};
	
	public void load(String inMetadata, SimpleDateFormat inDateFormatter) throws ParseException, IllegalArgumentException {
		String[] parts = inMetadata.split("\\|", 10);
		
		if (parts.length != 7 && parts.length != 9) {
			throw new IllegalArgumentException("Input String does not have correct number of field");
		}
		
		this.WAVEPath = parts[0];
		this.DateTime = inDateFormatter.parse(parts[1]);
		this.CallID = new Integer(parts[2]);
		this.AgentId = (parts[3].isEmpty() ? null : new Integer(parts[3]));
		this.Agent = (parts[4].isEmpty() ? null : parts[4]);
		this.Extension = (parts[4].isEmpty() ? null : new Integer(parts[5]));
		if (parts.length == 7) {
			this.Phone = (parts[6].isEmpty() ? null : new Long(parts[6]));
			this.ANI = null;
			this.DNIS = null;
			this.Skill = null;
		} else {
			this.Phone = null;
			this.ANI = (parts[6].isEmpty() ? null : parts[6]);
			this.DNIS = new Integer(parts[7]);
			this.DNIS = (parts[7].isEmpty() ? null : new Integer(parts[7]));
			this.Skill = (parts[8].isEmpty() ? null : parts[8]);
		}
	}
	
	public String getHeader() {
		String sharedFields = "WAVEPath" + FIELD_SEPARATOR
				+ "DateTime" + FIELD_SEPARATOR
				+ "CallID" + FIELD_SEPARATOR
				+ "AgentID" + FIELD_SEPARATOR
				+ "Agent" + FIELD_SEPARATOR
				+ "Extension" + FIELD_SEPARATOR;

		return sharedFields 
				+ (null != ANI
				  ? ("ANI" + FIELD_SEPARATOR
						+ "DNIS" + FIELD_SEPARATOR
						+ "Skill") 
				  : "Phone" );
	}
}
