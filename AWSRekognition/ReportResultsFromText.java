import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class ReportResultsFromText
{    
	public static final String textFile = "Results11731422.txt";

	public static void main(String[] args) throws Exception 
	{
		long startTime = System.nanoTime();

		// read the text file and create existing photos Lists
		ArrayList<String> listIndividual = new ArrayList<String>();
		ArrayList<String> listGroup = new ArrayList<String>();

		Scanner scan = new Scanner(new File(textFile));
		scan.nextLine();
		while(scan.hasNext()){
			String currentLine = scan.nextLine();
			String[] splitted = currentLine.split("\t");

			String individualPhoto = splitted[0];
			individualPhoto = individualPhoto.replaceAll("\\s+", "");
			listIndividual.add(individualPhoto);

			String groupPhoto = splitted[1];
			groupPhoto = groupPhoto.replaceAll("\\s+", "");
			listGroup.add(groupPhoto);
		}
		scan.close();

		// Now get unique names
		// First individual
		List<String> UniqueNumbers = listIndividual.stream().distinct().collect(
				Collectors.toList());
		System.out.println("Total number of Unique individuals = " + UniqueNumbers.size());

		// then group
		List<String> UniqueNumbersGroup = listGroup.stream().distinct().collect(
				Collectors.toList());
		System.out.println("Total number of Unique groups = " + UniqueNumbersGroup.size());

		//		// iterate through List
		//		for (int i = 0; i < UniqueNumbers.size(); ++i) {
		//			System.out.println(UniqueNumbers.get(i));
		//		}

		System.out.println("ALL PROCESSES FINISHED!");
		long stopTime = System.nanoTime();
		double totalTime = (stopTime - startTime) / 1e9;
		System.out.println("\nreading text file and finding missing photos " + totalTime + " Seconds to run");
	}// main
}// class

