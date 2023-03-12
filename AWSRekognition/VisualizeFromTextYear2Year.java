import java.awt.*;
import java.io.*;
import java.util.Scanner;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public class VisualizeFromTextYear2Year
{    
	public static final String textFile = "ResultsYear2YearVanBuren.txt";
	public static final String localDirecotryPath = "C:/Users/hrezaeilouyeh/Desktop/SubjectMatchingResults/FallAndSpring/VanBuren/";

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

			String springPhoto = splitted[0];
			springPhoto = springPhoto.replaceAll("\\s+", "");
			String outspringPhoto = localDirecotryPath + "Spring/" + springPhoto;

			String fallPhoto = splitted[1];
			fallPhoto = fallPhoto.replaceAll("\\s+", "");
			String outfallPhoto = localDirecotryPath + "Fall/" + fallPhoto;

			String coordinatesLeft = splitted[2];
			coordinatesLeft = coordinatesLeft.replaceAll("\\s+", "");
			coordinates[0] = Float.parseFloat(coordinatesLeft);

			String coordinatesTop = splitted[3];
			coordinatesTop = coordinatesTop.replaceAll("\\s+", "");
			coordinates[1] = Float.parseFloat(coordinatesTop);

			System.out.println("Started visualizing:" + fallPhoto + "\t and: \t " + springPhoto);
			
			BufferedImage Output = joinBufferedImage(outfallPhoto, outspringPhoto);
			//write the image
			String outputPath = localDirecotryPath + "Visualized/" + fallPhoto + springPhoto;
			ImageIO.write(Output, "jpg", new File(outputPath));
			
//			visualizeOnGroupPhotoFloat(outGroupPhoto, outGroupPhoto, coordinates, subjectName);
			System.out.println("Finished visualizing:" + fallPhoto + "\t and: \t " + springPhoto);

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

	public static BufferedImage joinBufferedImage(String fallPhoto, String springPhoto) throws IOException {
		
//		springPhoto = springPhoto + ".JPG";
		BufferedImage img1 = ImageIO.read(new File(fallPhoto));
		BufferedImage img2 = ImageIO.read(new File(springPhoto));
		
		int offset = 2;
		int width = img1.getWidth() + img2.getWidth() + offset;
		int height = Math.max(img1.getHeight(), img2.getHeight()) + offset;
		BufferedImage newImage = new BufferedImage(width, height,
				BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = newImage.createGraphics();
		Color oldColor = g2.getColor();
		g2.setPaint(Color.BLACK);
		g2.fillRect(0, 0, width, height);
		g2.setColor(oldColor);
		g2.drawImage(img1, null, 0, 0);
		g2.drawImage(img2, null, img1.getWidth() + offset, 0);
		g2.dispose();
		return newImage;
	}

}// class

