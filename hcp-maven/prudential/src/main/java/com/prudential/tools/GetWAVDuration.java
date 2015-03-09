package com.prudential.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

public class GetWAVDuration {

	/**
	 * @param args
	 * @throws UnsupportedAudioFileException 
	 */
	public static void main(String[] args) {
		
		if (args.length <= 0) {
			System.out.println();
			System.out.println("Usage: " + GetWAVDuration.class.getSimpleName() + " <Wave-File-Name> ...");
			System.out.println();

			System.exit(1);
		}

		for (int i=0; i < args.length; i++) {
			File file = new File(args[i]);
			if ( ! file.exists() || ! file.isFile() || ! file.canRead() ) {
				System.err.println(file.getPath() + ": not an existing, readable, and regular file.");
				continue;
			}

			// Get the SOXi tool path.
			String toolPath = "./soxi";
			// If operating on windows add the .exe, if needed.
			if (System.getProperty("os.name").startsWith("Windows")) {
				if ( ! toolPath.endsWith(".exe")) {
					toolPath += ".exe";
				}
			}
			
			// Make sure the tool exists.
			File mSoxIToolFile = new File(toolPath);
			if ( ! mSoxIToolFile.exists() || ! mSoxIToolFile.canExecute() ) {
				System.out.println("Configuration for tools.soxi is invalid.  Does not point to an existing executable file. Will attempt using buildin Java utilities.");
				mSoxIToolFile = null;
			}
			
			Long Duration = null;
			Float GranularDuration = null;
			ProcessBuilder soxiProcessBuilder = new ProcessBuilder(mSoxIToolFile.getAbsolutePath(), "-D", file.getAbsolutePath());

			Process soxiProcess = null;
			try {
				soxiProcess = soxiProcessBuilder.start();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				System.exit(1);
			}
			BufferedReader br = new BufferedReader(new InputStreamReader(soxiProcess.getInputStream()));
			String retString = null;
			try {
				retString = br.readLine();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				System.exit(1);
			}
			if (null != retString) {
				GranularDuration = new Float(retString);
				if (0 != GranularDuration) {
					Duration = new Long((long)(GranularDuration + 0.5));  // And round then to long
				}
			}

			if (null == Duration) {
				try {
					AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(file);
					
					AudioFormat format = audioInputStream.getFormat();
					long frames = audioInputStream.getFrameLength();
					GranularDuration = new Float(frames / format.getFrameRate());
					Duration = new Long((long)(GranularDuration + 0.5));  // And round then to long
					
				} catch (IOException e) {
					System.err.println("Unexpected I/O Exception encountered (" + file.getPath() + ").");
					System.err.println();
					
					e.printStackTrace();
				} catch (UnsupportedAudioFileException e) {
					System.err.println("Encountered unsupported audio file (" + file.getPath() + ").");
					System.err.println();
					
					e.printStackTrace();
				}
				
			}
			
			System.out.println(file.getPath() + ": " + Duration + " (" + GranularDuration + ") seconds in length");
			
		}
	}
}
