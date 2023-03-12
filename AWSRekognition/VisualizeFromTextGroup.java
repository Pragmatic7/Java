import java.awt.*;
import java.io.*;
import java.util.Scanner;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public class VisualizeFromTextGroup
{    
	public static final String textFile = "Results11731422.txt";
	public static final String localDirecotryPath = "GroupPhotos/";//"GroupPhotos/";

	public static void main(String[] args) throws Exception 
	{
		long startTime = System.nanoTime();
		
		// Now read back the text file and visualize on group photos 1 by 1
		Scanner scan = new Scanner(new File(textFile));
		float[] coordinates = new float[2];
		scan.nextLine();
		while(scan.hasNext()){
			String curLine = scan.nextLine();
			String[] splitted = curLine.split("\t");
			
			String inGroupPhoto = splitted[1];
			inGroupPhoto = inGroupPhoto.replaceAll("\\s+", "");
			
			String outGroupPhoto = localDirecotryPath + inGroupPhoto;
			
			String subjectName = splitted[0];
			subjectName = subjectName.replaceAll("\\s+", "");

			String coordinatesLeft = splitted[2];
			coordinatesLeft = coordinatesLeft.replaceAll("\\s+", "");
			coordinates[0] = Float.parseFloat(coordinatesLeft);

			String coordinatesTop = splitted[3];
			coordinatesTop = coordinatesTop.replaceAll("\\s+", "");
			coordinates[1] = Float.parseFloat(coordinatesTop);

			System.out.println("Started Writing subject name:" + subjectName + "\tOn Group Photo:" + outGroupPhoto);
			visualizeOnGroupPhotoFloat(outGroupPhoto, outGroupPhoto, coordinates, subjectName);
			System.out.println("Finished Writing subject name:" + subjectName + "\tOn Group Photo:" + outGroupPhoto);

		}
		scan.close();

		System.out.println("ALL PROCESSES FINISHED!");
		long stopTime = System.nanoTime();
		double totalTime = (stopTime - startTime) / 1e9;
		System.out.println("\nreading text file and annotating group photos took " + totalTime + " Seconds to run");
	}// main

	public static void visualizeOnGroupPhotoFloat(String inputPhoto, String outputPhoto, float[] location, String text) throws IOException {
		//read the image
		BufferedImage buffImage = ImageIO.read(new File(inputPhoto));
		int height = buffImage.getHeight();
		int width = buffImage.getWidth();
		int left = (int) (width * location[0]);
		int top = (int) (height * location[1]);

		//get the Graphics object
		Graphics g = buffImage.getGraphics();
		//set font
		g.setFont(g.getFont().deriveFont(100f));
		//display the text at the coordinates(x=50, y=150)
		g.drawString(text, left, top);
		g.dispose();
		//write the image
		ImageIO.write(buffImage, "jpg", new File(outputPhoto));
	}

}// class

