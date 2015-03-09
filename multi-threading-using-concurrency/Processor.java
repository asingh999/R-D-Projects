package test.multi;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;


public class Processor{
	/*
	 * 
-Dhcp.host=myhost
-Dhcp.port=myport		
-Dhcp.protocol=myprotocol
-Dhcp.password=myPassword
-Dhcp.username=myUserName	
	 */
	
	public static void main(String[] args) throws IOException {
		System.out.println("Starting message processing.....");

		Processor processor = new Processor();
		List<FutureTask<String>> futureTasks = processor.buildFutureCallableMessages();
		ExecutorService executor = Executors.newFixedThreadPool(10);

		int i=1;
		for (FutureTask<String> futureTask:futureTasks) {
			System.out.println("Executing " + i + " out of " + futureTasks.size());
			executor.execute(futureTask);
			i++;
		}
		for (FutureTask<String> futureTask:futureTasks) {
			while (true) {
				if(futureTask.isDone()){
					System.out.println("Done");
					//shut down executor service
					//executor.shutdown();
					return;
				}
			}
		}
		System.out.println("Done message processing " + futureTasks.size() + " message");
		System.exit(0);
	
	}

	ClientHostConfig ivlsClientConfig = ClientHostConfig.getClientHostConfig();

	public List<FutureTask<String>> buildFutureCallableMessages() {
		List<FutureTask<String>> futureTasks = new ArrayList<FutureTask<String>>();

		System.out.println("Starting processing .....");
		RestClient birc = new RestClient();
		String baseDirectories = "C:\\delete\\TestData\\SykesData\\NICECalls";
		String startTriggerPath = "C:\\delete\\TestData\\SykesData\\NICECalls\\InboundCalls\\HDD7\\DAY14_APR5_2012\\PM\11220201_APR30_MAY07_2008_CD415";
		getAllFilesFromDir( Paths.get(baseDirectories));
		LinkedList<String> subFolders = getListOfSubDirectories(baseDirectories);
		subFolders = addStartTriggerPathAsFirstItemAndSetStartTrigger(subFolders, startTriggerPath);
		int i = 1;
		for(String subFolder: subFolders) {
			LinkedList<Path> filePaths = filePaths = getAllFilesFromDir(Paths.get(subFolder));
			for (Path file:filePaths) {
				System.out.println(String.format("Sending file  %s for post ", file));		
				//CallableRestClient circ = new CallableRestClient(file.getFileName().toString(), ivlsClientConfig.getHost(), ivlsClientConfig.getUserName(), ivlsClientConfig.getPassword());
				CallableRestClient circ = new CallableRestClient(file.toString(), null, null, null);
				FutureTask<String> futureTask = new FutureTask<String>(circ);
			
				futureTasks.add(futureTask);
			}

		}

		System.out.println("Total task to process: " + futureTasks.size());
		return futureTasks;
	}

	boolean isDeependStartTrigerSetOnTop = false;
	
	/**
	 * Checks for exact match or partial match.
	 *  If found remove the exact match or a level able partial match and add the respective items on the top of the list
	 */
	private LinkedList<String> addStartTriggerPathAsFirstItemAndSetStartTrigger(LinkedList<String> list, String mFolderStartPath) {
		if(mFolderStartPath!=null) {
			String startPathTrigger = mFolderStartPath;
			File deepestStartTriggerItem = null;
			if(list.contains(mFolderStartPath)) { // Exact match of start trigger path
				deepestStartTriggerItem = new File(mFolderStartPath);
				list.remove(mFolderStartPath);
				list.addFirst(mFolderStartPath);			
				isDeependStartTrigerSetOnTop = true;
			} else { // Partial match of start trigger path		
				String folderSep = startPathTrigger.indexOf("/")!=-1?"/":"\\"; /*Detect folder path separator*/
				String bestPossibleMatchTriggerPath =  startPathTrigger;
				File bestPossibleMatchTriggerDir = new File(bestPossibleMatchTriggerPath);
				if(deepestStartTriggerItem==null && bestPossibleMatchTriggerDir.exists()) {
					deepestStartTriggerItem = bestPossibleMatchTriggerDir;
				}
				while (bestPossibleMatchTriggerPath.indexOf(folderSep) !=-1){
					bestPossibleMatchTriggerPath =  bestPossibleMatchTriggerPath.substring(0, bestPossibleMatchTriggerPath.lastIndexOf(folderSep));
					bestPossibleMatchTriggerDir = new File(bestPossibleMatchTriggerPath);
					if(deepestStartTriggerItem==null && bestPossibleMatchTriggerDir.exists()) {
						deepestStartTriggerItem = bestPossibleMatchTriggerDir;
					}
					if(list.contains(new File(bestPossibleMatchTriggerPath))) {
						list.remove(bestPossibleMatchTriggerPath);
						list.addFirst(bestPossibleMatchTriggerPath);
						isDeependStartTrigerSetOnTop = true;
					}
				}
			}
		}
		
		return list;
	}	
	
	public LinkedList <String> getListOfSubDirectories(String baseDirectories) {
		 LinkedList<String> list = new  LinkedList<String>();
		
		File dir = new File(baseDirectories);
		  
		File[] subDirs = dir.listFiles(new FileFilter() {
		    public boolean accept(File pathname) {
		        return pathname.isDirectory();
		    }
		});
		  
		for (File subDir : subDirs) {
			list.add(subDir.getAbsolutePath());
		}
		return list;
	}
	
	private LinkedList<String> getFileNames(LinkedList<String> fileNames, Path dir){
	    try {
	        DirectoryStream<Path> stream = Files.newDirectoryStream(dir);
	        for (Path path : stream) {
	            if(path.toFile().isDirectory())getFileNames(fileNames, path);
	            else {
	                fileNames.add(path.toAbsolutePath().toString());
	                System.out.println(path.getFileName());
	            }
	        }
	        stream.close();
	    }catch(IOException e){
	        e.printStackTrace();
	    }
	    return fileNames;
	} 

	private LinkedList<Path> getAllFilesFromDir(Path path) {
	    Deque<Path> stack = new ArrayDeque<Path>();
	    final LinkedList<Path> files = new LinkedList<>();

	    stack.push(path);

	    while (!stack.isEmpty()) {
	        try {
	        DirectoryStream<Path> stream = Files.newDirectoryStream(stack.pop());
	        for (Path entry : stream) {
	            if (Files.isDirectory(entry)) {
	                stack.push(entry);
	            }
	            else {
	                files.add(entry);
	            }
	        }
	
				stream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    }
	    System.out.println(files.toString());
	    return files;
	}
}
