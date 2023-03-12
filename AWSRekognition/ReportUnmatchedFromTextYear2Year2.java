import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

public class ReportUnmatchedFromTextYear2Year2
{    
	public static final String textFile = "ResultsYear2YearVanBuren.txt";
	public static final String localDirecotryPath = "C:/Users/hrezaeilouyeh/Desktop/SubjectMatchingResults/FallAndSpring/VanBuren/";
	public static final String outputTextFileSpring = "MissingSpringVanBuren.txt";
	public static final String outputTextFileFall = "MissingFallVanBuren.txt";

	public static void main(String[] args) throws Exception 
	{
		long startTime = System.nanoTime();

		// First list all files in Spring dir
		ArrayList<String> listSpringDir = new ArrayList<String>();
		String dirPathSpring = localDirecotryPath + "Spring";
		File[] filesSpring = new File(dirPathSpring).listFiles();
		//If this pathname does not denote a directory, then listFiles() returns null. 
		for (File file : filesSpring) {
			if (file.isFile()) {
				listSpringDir.add(file.getName());
			}
		}
		// sort list alphabetically
		Collections.sort(listSpringDir);


		// Then list all files in Fall dir
		ArrayList<String> listFallDir = new ArrayList<String>();
		String dirPathFall = localDirecotryPath + "Fall";
		File[] filesFall = new File(dirPathFall).listFiles();
		//If this pathname does not denote a directory, then listFiles() returns null. 
		for (File file : filesFall) {
			if (file.isFile()) {
				listFallDir.add(file.getName());
			}
		}
		// sort list alphabetically
		Collections.sort(listFallDir);

		// Now read back the text file and create existing photos Lists
		ArrayList<String> listFallExisting = new ArrayList<String>();
		ArrayList<String> listSpringExisting = new ArrayList<String>();

		Scanner scan = new Scanner(new File(textFile));
		scan.nextLine();
		while(scan.hasNext()){
			String curLine = scan.nextLine();
			String[] splitted = curLine.split("\t");

			String springPhoto = splitted[0];
			springPhoto = springPhoto.replaceAll("\\s+", "");
			listSpringExisting.add(springPhoto);

			String fallPhoto = splitted[1];
			fallPhoto = fallPhoto.replaceAll("\\s+", "");
			listFallExisting.add(fallPhoto);
		}
		scan.close();

		// Now find missing and extra in both sets
		// First Spring
		Set<String> missingSpring = new HashSet<String>(listSpringDir);
		missingSpring.removeAll(listSpringExisting);
		System.out.println("missing Spring:" + missingSpring);
		List<String> missingSpringSorted = missingSpring.stream().collect(Collectors.toList());
		Collections.sort(missingSpringSorted);

		Set<String> extraSpring = new HashSet<String>(listSpringExisting);
		extraSpring.removeAll(listSpringDir);
		System.out.println("extra Spring:" + extraSpring);

		// Then Fall
		Set<String> missingFall = new HashSet<String>(listFallDir);
		missingFall.removeAll(listFallExisting);
		System.out.println("missing Fall:" + missingFall);
		List<String> missingFallSorted = missingFall.stream().collect(Collectors.toList());
		Collections.sort(missingFallSorted);

		Set<String> extraFall = new HashSet<String>(listFallExisting);
		extraFall.removeAll(listFallDir);
		System.out.println("extra Fall:" + extraFall);

		// Now write to text files
		// Spring
		File resultsFileSpring = new File(outputTextFileSpring);
		try {
			Files.deleteIfExists(resultsFileSpring.toPath());
		} catch (IOException e) {
			System.err.println("Caught IOException: " + e.getMessage());
		}
		Path resultsPathSpring = Paths.get(outputTextFileSpring);

		for (String element : missingSpringSorted) {
			appendStringToFile(element, resultsPathSpring);
		}

		// Fall
		File resultsFileFall = new File(outputTextFileFall);
		try {
			Files.deleteIfExists(resultsFileFall.toPath());
		} catch (IOException e) {
			System.err.println("Caught IOException: " + e.getMessage());
		}
		Path resultsPathFall = Paths.get(outputTextFileFall);

		for (String element : missingFallSorted) {
			appendStringToFile(element, resultsPathFall);
		}

		System.out.println("ALL PROCESSES FINISHED!");
		long stopTime = System.nanoTime();
		double totalTime = (stopTime - startTime) / 1e9;
		System.out.println("\nreading text file and finding missing photos " + totalTime + " Seconds to run");
	}// main

	public static void appendStringToFile(String message, Path file) throws IOException  {
		try {
			final Path path = file;
			Files.write(file, Arrays.asList(message), StandardCharsets.UTF_8,
					Files.exists(path) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE);
		} catch (final IOException e) {
			System.err.println("Caught IOException: " + e.getMessage());
		}
	}

}// class

