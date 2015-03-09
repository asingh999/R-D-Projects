package com.prudential.tools;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;

import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import com.prudential.comet.AllianceAddInterface;
import com.prudential.comet.AllianceCallMetadata;

/*
 * This tool will convert the XML file into CSV file (actually '|' separated).
 *   Then it easily be additionally processed by standard linux tools.
 */
public class MDConverter implements AllianceAddInterface {
	
	private HashMap<String, AllianceCallMetadata> mMetaCache = new HashMap<String, AllianceCallMetadata>();
	
	public boolean bUseMemory = false;

	void doIt(File inputFile) throws Exception {

		class SAXRecordingEvents extends DefaultHandler {
			SAXRecordingEvents(AllianceAddInterface inAddCallback) {
				mAddCallback = inAddCallback;
			}
			
			AllianceAddInterface mAddCallback;
			AllianceCallMetadata currentRecording = null;
			String currentField = null;
			String currentValue = null;
			
			public void startElement(String namespaceURI, String localName, String qName, Attributes attrs) 
			   throws SAXException {
				
				if (qName.equals("Recordings")) {
					return;
				}
				
				if (qName.equals("Recording")) {
					currentRecording = new AllianceCallMetadata();
					return;
				}

				// Save our context for reading chars.
				currentField = qName;
				currentValue = null;
			}
			
			private String append(String front, String back) {
				return new String((null != front ? front : "") + (null != back ? back : ""));
			}
			
			public void characters(char[] ch,
		              int start,
		              int length)
		                throws SAXException {

				if (null == currentField) {
					// Nothing to process
					return;
				}

				currentValue = append(currentValue, new String(ch, start, length));
				return;
			}
			
			public void endElement(String namespaceURI, String localName, String qName) {

				if (qName.equals("Recordings")) {
					return;
				}
				
				if (qName.equals("Recording")) {
					mAddCallback.add(currentRecording);
					
					currentRecording = null;
					
					return;
				}
				
				// Skip any fields that don't have a value;
				if (null == currentValue)
					return;

				
				try {
					Field thisField = currentRecording.getClass().getDeclaredField(qName);

					if (thisField.getType().getName().equals("java.util.Date")) {
						try {
							thisField.set(currentRecording, new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").parse(currentValue));
						} catch (IllegalArgumentException | ParseException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					} else {
						Object value = thisField.getType().getConstructor(currentValue.getClass()).newInstance(currentValue);

						thisField.set(currentRecording, value);
					}

				} catch (NoSuchFieldException | SecurityException | IllegalAccessException | InstantiationException 
						| IllegalArgumentException | InvocationTargetException | NoSuchMethodException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				currentField = null;
				currentValue = null;
			}
			
		}

	    /*
		 * Mainline for the processing of the XML for an HCP folder.
		 */
		XMLReader xmlReader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
		xmlReader.setContentHandler(new SAXRecordingEvents(this));
		xmlReader.parse(new InputSource(new FileInputStream(inputFile)));

		if (bUseMemory) {
			// Output in completely random order. For Testing
			Iterator<AllianceCallMetadata> iter = mMetaCache.values().iterator();
			while (iter.hasNext()) {
				System.out.println(iter.next().toString());
			}
		}
	}
	
	@Override
	public void add(AllianceCallMetadata inMetadata) {
		if (bUseMemory) {
			mMetaCache.put(inMetadata.WAVEPath, inMetadata);
		} else {
			System.out.println(inMetadata.toString());
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		MDConverter me = new MDConverter();
		
		if (args.length != 1) {
			System.err.println("Usage: MDConverter <Input-XML-File>");
			System.exit(1);
		}

		File inputFile=new File(args[0]);
		
		if (! inputFile.exists() || ! inputFile.canRead() || ! inputFile.isFile()) {
			System.err.println("Specified file is not an existing, readable, and regular file");
			System.exit(1);
		}
		
		// See if we should use memory to load all content.
		me.bUseMemory = (null != System.getenv("USE_MEMORY") ? true : false);

		// Do some work.
		try {
			me.doIt(inputFile);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
