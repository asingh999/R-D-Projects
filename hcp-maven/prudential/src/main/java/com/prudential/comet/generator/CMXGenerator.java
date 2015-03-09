package com.prudential.comet.generator;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.TimeZone;
import java.util.regex.Pattern;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hds.hcp.tools.comet.BaseWorkItem;
import com.hds.hcp.tools.comet.generator.BaseGeneratorProperties;
import com.hds.hcp.tools.comet.generator.BaseMetadataGenerator;
import com.hds.hcp.tools.comet.generator.ObjectContainer;
import com.hds.hcp.tools.comet.generator.SystemMetadataContainer;
import com.hds.hcp.tools.comet.utils.StaticUtils;
import com.hds.hcp.tools.comet.utils.URIWrapper;
import com.prudential.comet.AllianceCallMetadata;
import com.prudential.comet.CMXItem;

public class CMXGenerator extends BaseMetadataGenerator {

	private static Logger logger = LogManager.getLogger();
	
	static final String BASE_FILENAME_PROPERTY = "com.prudential.properties.file";
	protected SimpleDateFormat mOutputDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
	
	protected static final int RETENTION_PERIOD_YEARS = 7;

	protected String mPatternMatch = "";
	protected boolean isInitialized;
	
	// Used for efficient pattern matching by compiling the pattern once.
	protected Pattern mFilePattern;
	
	protected File mSoxIToolFile;
	
	/*
	 * Construct a private properties class to construct module specific information.
	 */
	protected class AllianceProperties extends BaseGeneratorProperties {
		public AllianceProperties() {
			super(BASE_FILENAME_PROPERTY);
		}

		// Module specific retention value.
		public String getRetentionValue() {
			return mProps.getProperty("metadata.retentionValue", null);
		}
		
		public String getSoxiToolPath() {
			return StaticUtils.resolveEnvVars(mProps.getProperty("tools.soxi", "soxi").trim());
		}
	}

	private AllianceProperties mProps;
	
	@Override
	public void initialize() {
		mFilePattern = Pattern.compile(mPatternMatch);
		
		mProps = new AllianceProperties();

		isInitialized = true;
		
		// Output will be in GMT (Greenwich)
		mOutputDateFormat.setTimeZone(TimeZone.getTimeZone("Greenwich"));
		
		// Get the SOXi tool path.
		String toolPath = mProps.getSoxiToolPath();
		// If operating on windows add the .exe, if needed.
		if (System.getProperty("os.name").startsWith("Windows")) {
			if ( ! toolPath.endsWith(".exe")) {
				toolPath += ".exe";
			}
		}
		
		// Make sure the tool exists.
		mSoxIToolFile = new File(toolPath);
		if ( ! mSoxIToolFile.exists() || ! mSoxIToolFile.canExecute() ) {
			logger.warn("Configuration for tools.soxi is invalid.  Does not point to an existing executable file ({}). Will attempt using buildin Java utilities.", mSoxIToolFile.getAbsolutePath());
			mSoxIToolFile = null;
		}
	}

	@Override
	public LinkedList<ObjectContainer> getMetadataList(BaseWorkItem inItem) {
		StaticUtils.TRACE_METHOD_ENTER(logger);
		
		LinkedList<ObjectContainer> retval = null;

		if (! (inItem instanceof CMXItem)) {
			logger.fatal("BaseWorkItem passed into getMetadataList is not of type CMXItem.");

			// TODO Probably should see about throwing an exception.
			StaticUtils.TRACE_METHOD_EXIT(logger);
			return null;
		}
		
		CMXItem currentItem = (CMXItem)inItem;
		
		/**
		 *  Build the base object metadata
		 */

		if ( ! (currentItem.getBaseSpecification() instanceof File) ) {
			logger.fatal("BaseWorkItem.getBaseSpecification() is not a java.io.File");

			// TODO Probably should see about throwing an exception.
			StaticUtils.TRACE_METHOD_EXIT(logger);
			return null;
		}
		
		if ( ! (currentItem.getHandle() instanceof File) ) {
			logger.fatal("BaseWorkItem.getHandle() is not a java.io.File");

			// TODO Probably should see about throwing an exception.
			StaticUtils.TRACE_METHOD_EXIT(logger);
			return null;
		}
		
		File currentSrcFile = (File)(currentItem.getHandle());
		File currentBaseFolder = (File)(currentItem.getBaseSpecification());
			
		// Now ready to allocate the return value Linked List.
		retval = new LinkedList<ObjectContainer>();

		/*
		 * Construct the custom metadata now before iterating through all the destinations,
		 *   since all destinations will receive custom metadata.
		 */
		byte[] customMetadata = buildCustomMetadata(currentSrcFile, currentItem.getMetadata());

		// If we haven't already allocated the return value, get it now.
		//   This is done inside the loop so that if the destination information
		//   is empty, the function will return null instead of an empty list.

		// Build out the destination for the one item.
		//
		// First formulate the destination object path based on source file path, base folder path,
		//   and the settings in the properties file.
		//
		StringBuffer destFilePath = new StringBuffer(currentSrcFile.getAbsolutePath());
		
		// Remove the initial path from the full path.
		if (0 == destFilePath.indexOf(currentBaseFolder.getAbsolutePath())) {
			destFilePath.delete(0, currentBaseFolder.getAbsolutePath().length() + File.separator.length());
		}
		
		// Add back in the Trailing initial path, if indicated
		if (mProps.shouldAppendSourcePathTrailingFolder()) {
			destFilePath.insert(0, currentBaseFolder.getName() + File.separator);
		}
		
		// Need to be OS agnostic and replace any FS separators with HTTP separator, if it isn't the same.
		if (! File.separator.equals(StaticUtils.HTTP_SEPARATOR)) {
			int charIndex = destFilePath.indexOf(File.separator);
			while (-1 != charIndex) {
				destFilePath.replace(charIndex, charIndex + File.separator.length(), StaticUtils.HTTP_SEPARATOR);
				charIndex = destFilePath.indexOf(File.separator);
			}
		}
		
		// Make sure it isn't going to barf when passed into the Apache HTTP Client.
		ObjectContainer retObject = null;
		try {
			// Construct the destination root string paying attention as to whether we need to add
			//   an HTTP Separator or not.
			String destinationRoot = mProps.getDestinationRootPath();
			String objectPath = destinationRoot 
					+ (destinationRoot.lastIndexOf(StaticUtils.HTTP_SEPARATOR) != ( destinationRoot.length() - 1 ) ? StaticUtils.HTTP_SEPARATOR : "")
					+ destFilePath.toString();
			retObject = new ObjectContainer(new URIWrapper(objectPath), mProps);
		} catch(URISyntaxException e) {
			logger.fatal("URL formulation issue (Skipping)...", e);

			StaticUtils.TRACE_METHOD_EXIT(logger);
			return null;
		}

		/*
		 * Set the core system metadata for the object.
		 */
		SystemMetadataContainer sysMeta = retObject.getSystemMetadata();
		
		// Need the credentials from properties file.
		sysMeta.setCredentials(mProps.getEncodedDestinationUserName(),
                               mProps.getEncodedDestinationPassword());
		
		sysMeta.setRetention(mProps.getRetentionValue());

		/*
		 * Set the custom metadata (if any) and write it to the configured annotation
		 */
		if (null != customMetadata)
			retObject.getCustomMetadata().put(mProps.getAnnotationName(), customMetadata);

		// Add it to the LinkedList that will be returned.
		retval.add(retObject);
		
		StaticUtils.TRACE_METHOD_EXIT(logger);
		return retval;
	}

	private byte[] buildCustomMetadata(File inSrcFile, AllianceCallMetadata inMetadata) {
		OutputStream outputStream = new ByteArrayOutputStream();

		try {
			// Extract the needed information from the filename
			Integer Channel = new Integer(inSrcFile.getName().substring(14, 17));
			Integer Server = new Integer(inSrcFile.getName().substring(17, 19));

			//
			// Extract the Call Duration from the WAV file.
			//
			Long Duration = null;
			Float GranularDuration = null;
			
			// First try to use the soxi utility configured.
			if (null != mSoxIToolFile) {
				try {
					ProcessBuilder soxiProcessBuilder = new ProcessBuilder(mSoxIToolFile.getAbsolutePath(), "-D", inSrcFile.getAbsolutePath());

					Process soxiProcess = soxiProcessBuilder.start();
					
					BufferedReader br = new BufferedReader(new InputStreamReader(soxiProcess.getInputStream()));
	 				String retString = br.readLine();
					
	 				// If we have a string, let's process it.
	 				if (null != retString && ! retString.isEmpty()) {
						GranularDuration = new Float(retString);
						if (0 != GranularDuration) {
							Duration = new Long((long)(GranularDuration + 0.5));  // And round then to long
						}
					}
				} catch (IOException e) {
					logger.warn("Failed to obtain WAV file duration using soxi utility", e);
				}
			}
			
			if (null == Duration) {
				try {
					// Get the Duration from the file.
					AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(inSrcFile);
					AudioFormat format = audioInputStream.getFormat();
					long frames = audioInputStream.getFrameLength();
					float granularDuration = (frames / format.getFrameRate());
					Duration = new Long((long)(granularDuration + 0.5));  // And round then to long
				} catch (UnsupportedAudioFileException | IOException e) {
					logger.warn("Failed to obtain WAV file duration using Java javax.sound library.", e);
				}			
			}

			// Report debugger information on duration
			if (null != Duration) {
				logger.debug("Successfully extracted duration from WAV file: {} ({})", Duration, GranularDuration);
			}

			// Now construct StartTime and EndTime.
			String StartTime = mOutputDateFormat.format(inMetadata.DateTime);
			
			String EndTime = null;
			if (null != Duration) {
				Date rawEndTime = new Date();
				rawEndTime.setTime(inMetadata.DateTime.getTime() + (Duration * 1000));
				
				EndTime = mOutputDateFormat.format(rawEndTime);
			}

			// Now let's put the information into the XML 

			XMLStreamWriter serializer;

			serializer = XMLOutputFactory.newInstance().createXMLStreamWriter(outputStream, "UTF-8");
			serializer.writeStartDocument("UTF-8", null);
			serializer.writeCharacters("\n");

			// Recording Information
			serializer.writeStartElement("CallRecording");
			String fileType="";
			String[] fileNameParts = inSrcFile.getName().split("\\.");
			fileType=(fileNameParts.length > 1 ? fileNameParts[fileNameParts.length - 1] : "");
			
			serializer.writeAttribute("type", fileType);

			serializer.writeCharacters("\n    ");
			serializer.writeStartElement("CallID");
			serializer.writeCharacters(inMetadata.CallID.toString());
			serializer.writeEndElement();

			serializer.writeCharacters("\n    ");
			serializer.writeStartElement("Channel");
			serializer.writeCharacters(Channel.toString());
			serializer.writeEndElement();

			serializer.writeCharacters("\n    ");
			serializer.writeStartElement("Server");
			serializer.writeCharacters(Server.toString());
			serializer.writeEndElement();

			if (null != inMetadata.AgentId) {
				serializer.writeCharacters("\n    ");
				serializer.writeStartElement("AgentId");
				serializer.writeCharacters(inMetadata.AgentId.toString());
				serializer.writeEndElement();
			}

			if (null != inMetadata.Agent){
				serializer.writeCharacters("\n    ");
				serializer.writeStartElement("Agent");
				serializer.writeCharacters(inMetadata.Agent);
				serializer.writeEndElement();
			}

			if (null != inMetadata.Extension){
				serializer.writeCharacters("\n    ");
				serializer.writeStartElement("Extension");
				serializer.writeCharacters(inMetadata.Extension.toString());
				serializer.writeEndElement();
			}

			serializer.writeCharacters("\n    ");
			serializer.writeStartElement("DateTime");
			serializer.writeCharacters("\n        ");
			serializer.writeStartElement("Start");
			serializer.writeCharacters(StartTime);
			serializer.writeEndElement();
			if (null != EndTime) {
				serializer.writeCharacters("\n        ");
				serializer.writeStartElement("End");
				serializer.writeCharacters(EndTime);
				serializer.writeEndElement();
			}

			// DateTime End
			serializer.writeCharacters("\n    ");
			serializer.writeEndElement();

			if (null != Duration) {
				serializer.writeCharacters("\n    ");
				serializer.writeStartElement("Duration");
				serializer.writeCharacters(null != Duration ? Duration.toString() : null);
				serializer.writeEndElement();
			}

			serializer.writeCharacters("\n    ");
			if (null != inMetadata.ANI) {
				// Must be Inbound
				serializer.writeStartElement("Direction");
				serializer.writeCharacters("Inbound");
				serializer.writeEndElement();

				if (null != inMetadata.ANI){
					serializer.writeCharacters("\n    ");
					serializer.writeStartElement("ANI");
					serializer.writeCharacters(inMetadata.ANI);
					serializer.writeEndElement();
				}

				if (null != inMetadata.DNIS){
					serializer.writeCharacters("\n    ");
					serializer.writeStartElement("DNIS");
					serializer.writeCharacters(inMetadata.DNIS.toString());
					serializer.writeEndElement();
				}

				if (null != inMetadata.Skill){
					serializer.writeCharacters("\n    ");
					serializer.writeStartElement("Skill");
					serializer.writeCharacters(inMetadata.Skill);
					serializer.writeEndElement();
				}
			} else {
				// Must be Outbound
				serializer.writeStartElement("Direction");
				serializer.writeCharacters("Outbound");
				serializer.writeEndElement();

				if (null != inMetadata.Phone) {
					serializer.writeCharacters("\n    ");
					serializer.writeStartElement("DialedNumber");
					serializer.writeCharacters(inMetadata.Phone.toString());
					serializer.writeEndElement();
				}
			}

			// CallRecording End
			serializer.writeCharacters("\n");
			serializer.writeEndElement();

			serializer.writeEndDocument();
		}
		catch (Exception e)
		{
			logger.fatal("Failed to formulate XML in BasicFileInfoGenerator", e);
			return null;
		}
		
		return outputStream.toString().getBytes();
	}

}
