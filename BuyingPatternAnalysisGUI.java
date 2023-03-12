package ImageLearn;

import java.awt.EventQueue;
import java.awt.FlowLayout;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import org.apache.commons.io.FileUtils;

import com.lifetouch.lti.camera.metadata.ImageMetadataException;
import com.lifetouch.lti.camera.metadata.ImageMetadataTag;
import com.lifetouch.lti.camera.metadata.VegaImageMetadataExtractor;
import com.lifetouch.lti.camera.metadata.VegaImageMetadataExtractor.Requirements;
import com.lifetouch.lti.image.ipp.ifs.IppTranspose.IppTransposeType;
//import com.lifetouch.lti.vega.utils.ImageUtils;
//import com.lifetouch.lti.util.ImageUtils;

import ImageLearn.FaceFeatureForPose;
import f4s.FaceFeatureDetector.FeatureIndex;
import f4s.FaceFeatureDetector.Finder;

import java.awt.Font;
import java.awt.Image;
import java.awt.Color;
import java.awt.SystemColor;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.awt.event.ActionEvent;
import javax.swing.JLabel;
import javax.swing.JTextArea;

public class PortraitAnalyzer {

	static int faceConfidenceThreshold = 50;
	private String myField;
	static List<String> imageList = new ArrayList<String>();
	// Pose thresholds
	private final static double thresholdFullLengthHigh = 0.06;
	private final static double thresholdThreeQuarterLow = 0.06;
	private final static double thresholdThreeQuarterHigh = 0.10;
	private final static double thresholdHalfLengthLow = 0.10;
	private final static double thresholdHalfLengthHigh = 0.14;
	private final static double thresholdHeadAndShouldersLow = 0.14;
	private final static double thresholdHeadAndShouldersHigh = 0.22;
	private final static double thresholdCloseUpLow = 0.22;
	private JFrame frame;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					PortraitAnalyzer window = new PortraitAnalyzer();
					window.frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public PortraitAnalyzer() {
		initialize();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frame = new JFrame();
		frame.getContentPane().setBackground(SystemColor.menu);
		frame.setBounds(100, 100, 556, 389);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setLayout(null);

		JButton btnNewButton = new JButton("Browse Image Directory");
		btnNewButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JFileChooser f = new JFileChooser();
				f.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); 
				f.showSaveDialog(null);
				File selectedImageFile = f.getSelectedFile();
				String selectedImageDirectory = selectedImageFile.getPath();
				System.out.println("Selected image directory:" + selectedImageDirectory);
				setMyField(selectedImageDirectory);
			}
		});
		btnNewButton.setFont(new Font("Tahoma", Font.BOLD, 11));
		btnNewButton.setBounds(35, 130, 175, 80);
		frame.getContentPane().add(btnNewButton);

		JButton btnNewButton_1 = new JButton("Run");
		btnNewButton_1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {

				long startTime = System.nanoTime();

				List<String> listOfImages = new ArrayList<String>();
				String imageDirecotory = getMyField();
				if (imageDirecotory == null)
				{
					PortraitAnalyzer.infoBox("Please select directory of images before run!"
							+ " Exiting the program now...", "Image direcotry NOT selected!");
					System.exit(0);
				}
				System.out.println("Passed image directory:" + imageDirecotory);

				// First check if text files exist, delete them
				String faceTextPath = imageDirecotory + "/Results/Faces/PredictionResults.txt";
				File file1 = new File(faceTextPath);
				try {
					Files.deleteIfExists(file1.toPath());
				} catch (IOException e2) {
					e2.printStackTrace();
				}
				String poseTextPath = imageDirecotory + "/Results/PoseResults.txt";
				File file2 = new File(poseTextPath);
				try {
					Files.deleteIfExists(file2.toPath());
				} catch (IOException e2) {
					e2.printStackTrace();
				}
				String BKGTextPath = imageDirecotory + "/Results/BackgroundColorResults.txt";
				File file3 = new File(BKGTextPath);
				try {
					Files.deleteIfExists(file3.toPath());
				} catch (IOException e2) {
					e2.printStackTrace();
				}
				String combinedTextPath = imageDirecotory + "/Results/CombinedResults.txt";
				File file4 = new File(combinedTextPath);
				try {
					Files.deleteIfExists(file4.toPath());
				} catch (IOException e2) {
					e2.printStackTrace();
				}

				String resultsPath = imageDirecotory + "/Results";				
				try {
					FileUtils.deleteDirectory(new File(resultsPath));
				} catch (IOException e2) {
					e2.printStackTrace();
				}

				File dir = new File(imageDirecotory);
				String[] extensions = new String[] { "jpg" , "JPG" };
				try {
					System.out.println("Getting all .jpg and .JPG files in " + dir.getCanonicalPath()
					+ " including those in subdirectories");
				} catch (IOException e2) {
					e2.printStackTrace();
				}
				List<File> files = (List<File>) FileUtils.listFiles(dir, extensions, true);
				Collections.sort(files);
				int totalNumber = files.size();
				System.out.println("Total number of images in directory: " + totalNumber);


				/////////////////////////////////////////////////////////////////////
				//---------------- First perform preprcoessing --------------------//
				try {
					listOfImages = preprcoessing(imageDirecotory);
				} catch (ImageMetadataException e1) {
					e1.printStackTrace();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				System.out.println("Preprocessing finished successfully.");

				////////////////////////////////////////////////////////////////////////
				//------------------ Now do facial expression recognition ------------//
				System.out.println("Now doing facial expression recognition.");
				processFacialExpression(imageDirecotory);
				System.out.println("Finished facial expression recognition.");

				////////////////////////////////////////////////////////////////////////
				//------------------ Then do BKG detection ------------//
				System.out.println("Now doing BKG detection.");
				processBKGdetection(imageDirecotory);
				System.out.println("Finished BKG detection.");

				////////////////////////////////////////////////////////////////////////
				//------------------ Then do CapGown Detection ------------//
				System.out.println("Now doing CapGown Detection.");
				processCapGownDetection(imageDirecotory);
				System.out.println("Finished CapGown Detection.");

				////////////////////////////////////////////////////////////////////////
				//------------------ Then do Gender ID ------------//
				System.out.println("Now doing Gender ID.");
				processGenderID(imageDirecotory);
				System.out.println("Finished Gender ID.");

				/////////////////////////////////////////////////////////////////////////
				//------------------- Then do pose estimation ----------------------//
				System.out.println("Now doing pose estimation.");
				try {
					prcoessPoseEstimation(listOfImages, imageDirecotory);
				} catch (ImageMetadataException e1) {
					e1.printStackTrace();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				System.out.println("Pose estimation finished.");

				//-------------------- Finally create the final results text file ----------------//
				System.out.println("Now creating the final results text file.");
				try {
					createFinalTextResults(imageDirecotory, files);
				} catch (FileNotFoundException e1) {
					e1.printStackTrace();
				} catch (IOException e1) {
					e1.printStackTrace();
				}

				long stopTime = System.nanoTime();
				System.out.println("\nProcessing all images took " + (stopTime - startTime) / 1e9 + " Seconds to run");

				System.out.println("\n ALL PROCESSES FINISHED SUCCESSFULLY!");
				
				Toolkit.getDefaultToolkit().beep();
				
				JOptionPane.showMessageDialog(null, "All processes finished successfully! You can look at the Results now." );
			}
		});
		btnNewButton_1.setFont(new Font("Tahoma", Font.BOLD, 11));
		btnNewButton_1.setBounds(360, 130, 153, 80);
		frame.getContentPane().add(btnNewButton_1);

		JButton btnResults = new JButton("Results");
		btnResults.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {

				// if results text file exists, open it. Otherwise, let user know to run first!
				String imageDirecotory = getMyField();
				if (imageDirecotory == null)
				{
					PortraitAnalyzer.infoBox("Please select directory of images, then run, then look at results! Exiting the program now...", "Image direcotry NOT selected!");
					System.exit(0);
				}
				String resultsTextPath = imageDirecotory + "/Results/CombinedResults.txt";
				File f = new File(resultsTextPath);
				if(f.exists() && !f.isDirectory()) {
					ProcessBuilder pb = new ProcessBuilder("Notepad.exe", resultsTextPath);
					try {
						pb.start();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}else{
					PortraitAnalyzer.infoBox("Please select directory of images, then run, then look at results! Exiting the program now...", "Image direcotry NOT selected!");
					System.exit(0);
				}

				String path = System.getProperty("user.dir");
				String BKGimagePath = path + "/Other/BKGsNumbered.JPG";
				BufferedImage img = null;
				try {
					img = ImageIO.read(new File(BKGimagePath));
				} catch (IOException e1) {
					PortraitAnalyzer.infoBox("BKGsNumbered.JPG does not exist! Exiting the program now...", "BKGsNumbered.JPG does not exist!");
				}
				JLabel lbl = new JLabel();
				lbl.setSize(600, 900);
				Image dimg = img.getScaledInstance(lbl.getWidth(), lbl.getHeight(),
						Image.SCALE_SMOOTH);

				ImageIcon icon = new ImageIcon(dimg);
				JFrame frame = new JFrame();
				frame.setLayout(new FlowLayout());
				frame.setSize(600, 900);
				lbl.setIcon(icon);
				frame.add(lbl);
				frame.setVisible(true);
				//			    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			}
		});
		btnResults.setFont(new Font("Tahoma", Font.BOLD, 11));
		btnResults.setBounds(202, 249, 153, 68);
		frame.getContentPane().add(btnResults);

		JButton btnHelp = new JButton("Help");
		btnHelp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {

				JOptionPane.showMessageDialog(null, "1- This program has been tested to"
						+ " run on Windows 10 64 bit. Other OS are not currently "
						+ "supported.\n2- You need to have JRE/JDK installed on "
						+ "your system to run this program.\n3- Make sure you follow this recipe: "
						+ "First Browse to the directory of images, Then Run, and finally "
						+ "look at Results.\n4- Make sure you have admin rights in the "
						+ "directory of images. This program needs to copy some files there. "
						+ "\nDo NOT run this program on an image "
						+ "directory located on a CD/DVD. Copy them to your local "
						+ "hard drive and Run.\n5- If the program was unresponsive, "
						+ "close the program and reopen.\n6- After you run the program, "
						+ "you can find the debug information under Results subdirectory"
						+ " under your image directory.");

			}
		});
		btnHelp.setFont(new Font("Tahoma", Font.BOLD, 11));
		btnHelp.setBounds(35, 28, 89, 23);
		frame.getContentPane().add(btnHelp);

		JButton btnAbout = new JButton("About");
		btnAbout.setFont(new Font("Tahoma", Font.BOLD, 11));
		btnAbout.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {

				JOptionPane.showMessageDialog(null, "This program analyses the images in a directory"
						+ " for pose, expression, background, gender, and cap and gown. It will report"
						+ " the followings: \n1- Facial expression (full smile, game face, and soft smile)"
						+ " \n2- Pose (full length, 3/4, half length, head and shoulders, and close up)"
						+ " \n3- Background (16 backgrounds). \n4- Gender (Male/Female). "
						+ "\n5- Cap and Gown (Yes/No). \nIt will also reports a summary"
						+ " of statistics for each category at the end of the results text file.");
			}
		});
		btnAbout.setBounds(427, 28, 89, 23);
		frame.getContentPane().add(btnAbout);

		JLabel lblPortraitAnalyzerV = new JLabel("Portrait Analyzer");
		lblPortraitAnalyzerV.setForeground(Color.BLUE);
		lblPortraitAnalyzerV.setFont(new Font("Tahoma", Font.BOLD, 17));
		lblPortraitAnalyzerV.setBounds(202, 50, 195, 32);
		frame.getContentPane().add(lblPortraitAnalyzerV);

		JTextArea textArea = new JTextArea();
		textArea.setFont(new Font("Tahoma", Font.BOLD, 14));
		textArea.setForeground(Color.RED);
		textArea.setText("1");
		textArea.setBounds(120, 97, 12, 22);
		frame.getContentPane().add(textArea);

		JTextArea textArea_1 = new JTextArea();
		textArea_1.setText("2");
		textArea_1.setForeground(Color.RED);
		textArea_1.setFont(new Font("Tahoma", Font.BOLD, 14));
		textArea_1.setBounds(427, 97, 12, 22);
		frame.getContentPane().add(textArea_1);

		JTextArea textArea_2 = new JTextArea();
		textArea_2.setText("3");
		textArea_2.setForeground(Color.RED);
		textArea_2.setFont(new Font("Tahoma", Font.BOLD, 14));
		textArea_2.setBounds(267, 216, 12, 22);
		frame.getContentPane().add(textArea_2);
	}

	public String getMyField()
	{
		//include validation, logic, logging or whatever you like here
		return this.myField;
	}
	public void setMyField(String value)
	{
		//include more logic
		this.myField = value;
	}

	public static void infoBox(String infoMessage, String titleBar)
	{
		JOptionPane.showMessageDialog(null, infoMessage, "InfoBox: " + titleBar, JOptionPane.INFORMATION_MESSAGE);
	}

	public static List<String> preprcoessing(String directoryPath) throws ImageMetadataException, IOException
	{
		String myDirectoryPath = directoryPath;

		try {
			deleteDirecotry(myDirectoryPath + "/Results");
		} catch (IOException e) {
			e.printStackTrace();
		}

		createDirecotry(myDirectoryPath + "/Results");

		createDirecotry(myDirectoryPath + "/Results/Faces");

		File dir = new File(myDirectoryPath);
		String[] extensions = new String[] { "jpg" , "JPG" };
		System.out.println("Getting all .jpg and .JPG files in " + dir.getCanonicalPath()
		+ " including those in subdirectories");
		List<File> files = (List<File>) FileUtils.listFiles(dir, extensions, true);
		Collections.sort(files);
		int num = 0;

		if (files != null) {

			for (File child : files) {
				num = num + 1;
				int total = files.size();
				System.out.println("Pre processing image number:" + num + "\tOut of:" + total);
				String imgPath = child.getAbsolutePath();
//				System.out.println(imgPath);

				// First Autorotate the image to make it portrait
				autoRotate(imgPath);

				// Then check to see if any faces are found
				Finder faceFinder = new Finder();
				String stringLandmarks = faceFinder.DetectLandmarks(imgPath, -1);
				int[] binLandmarks = faceFinder.ParseMPEG4Landmarks(stringLandmarks);
				int faceCount = Finder.GetFaceCount(binLandmarks );
//				System.out.println("Face count = " + faceCount);
				int faceConfidence = 0;
				int faceConfidenceFinal = 0;
				int faceId = 0;

				// If no faces found, rotate 90 degree and try again.
				if(faceCount < 1)
				{
					rotate90(imgPath);
					// Then check to see if any faces are found
					faceFinder = new Finder();
					stringLandmarks = faceFinder.DetectLandmarks(imgPath, -1);
					binLandmarks = faceFinder.ParseMPEG4Landmarks(stringLandmarks);
					faceCount = Finder.GetFaceCount(binLandmarks );
//					System.out.println("Face count = " + faceCount);
					if(faceCount < 1)
					{
						// Rotate back to original
						rotate270(imgPath);						
					}
				}

				// Chose only the high confidence faces
				if(faceCount > 0)
				{
					for( int i = 0; i <  faceCount; ++i )
					{
						faceConfidence = Finder.GetFeatureForFace(binLandmarks, FeatureIndex.ConfidenceFactorFace, i + 1);
//						System.out.println( "Face Confidence : " + faceConfidence);
						if(faceConfidence > faceConfidenceThreshold)
						{
							faceConfidenceFinal = faceConfidence;
//							System.out.println( "Face Confidence Final : " + faceConfidenceFinal);
							faceId = i + 1;
//							System.out.println( "Face ID : " + faceId);
						}
					}
				}

				if(faceCount > 0 && faceConfidenceFinal > faceConfidenceThreshold)
				{
					// Save the list of images that are acceptable for future.
					imageList.add(imgPath);
					// Now crop the faces
					int faceTopLeftX = Finder.GetFeatureForFace(binLandmarks, FeatureIndex.FaceBoxTopLeftX, faceId);
					int faceTopLeftY = Finder.GetFeatureForFace(binLandmarks, FeatureIndex.FaceBoxTopLeftY, faceId);
					int faceTopRightX = Finder.GetFeatureForFace(binLandmarks, FeatureIndex.FaceBoxTopRightX, faceId);
					int faceBottomLeftY = Finder.GetFeatureForFace(binLandmarks, FeatureIndex.FaceBoxBottomLeftY, faceId);
					int cropWidth = Math.abs(faceTopRightX - faceTopLeftX);
					int cropHeight = Math.abs(faceBottomLeftY - faceTopLeftY);

					File imageFile = new File(imgPath);
					String imageNameOnly = imageFile.getName();
					String saveImagePath = myDirectoryPath + "/Results/Faces/" + imageNameOnly;
					File fileToRead = new File(imgPath);
					BufferedImage faceBufferedImage = cropImage(fileToRead, faceTopLeftX, faceTopLeftY, cropWidth, cropHeight);
					try {
						ImageIO.write(faceBufferedImage, "jpg", new File(saveImagePath));
					} catch (IOException e) {
						e.printStackTrace();
					}
				}else{ // if face count < 1 then skip this image.
					System.out.println("Warning! Could not find a face! Continuing to next image.");
					FileUtils.copyFileToDirectory(new File(imgPath), new File(myDirectoryPath + "/Results/NA"));					
				}
			} // end for
		} else { // if directory is null, throw an error!
			System.out.println("ERROR! Not a directory!");
		}
		System.out.println("Finished cropping faces.");
		//		System.out.println("Here is the list of acceptable images:" + imageList);
		System.out.println("Total number of face images:" + imageList.size());
		return imageList;
	}

	private static void createDirecotry(String directoryName) {
		File newDirectory = new File(directoryName);
		// if the directory does not exist, create it
		if (!newDirectory.exists()) {
			System.out.println("Creating directory: " + newDirectory.getName());
			boolean result = false;
			try{
				newDirectory.mkdirs();
				result = true;
			} 
			catch(SecurityException se){
				//handle it
			}        
			if(result) {    
				System.out.println("Direcotory created sucessfully.");  
			}
		}
	}

	public static void deleteDirecotry(String directoryName) throws IOException {
		Path directory = Paths.get(directoryName);
		if (Files.exists(directory)) {
			Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					Files.delete(dir);
					return FileVisitResult.CONTINUE;
				}
			});
		}
		System.out.println("Direcotory deleted sucessfully.");
	}

	//		File newDirectory = new File(directoryName);
	//		// if the directory exists, delete it
	//		if (newDirectory.exists()) {
	//			System.out.println("Recursively deleting directory: " + newDirectory.getName());
	//			boolean result = false;
	//			try{
	//				FileUtils.deleteDirectory(new File("directory"));
	//			} 
	//			catch(SecurityException se){
	//				//handle it
	//			}        
	//			if(result) {    
	//				System.out.println("Direcotory deleted sucessfully.");  
	//			}
	//		}

	// filter to identify images based on their extensions
	static final FilenameFilter IMAGE_FILTER = new FilenameFilter() {
		// array of supported extensions (use a List if you prefer)
		String[] EXTENSIONS = new String[]{
				"jpg", "JPG" // and other formats you need
		};
		public boolean accept(final File dir, final String name) {
			for (String ext : EXTENSIONS) {
				if (name.endsWith("." + ext)) {
					return (true);
				}
			}
			return (false);
		}
	};

	private static void autoRotate(String imagePath) throws IOException, ImageMetadataException {

		File imageFile = new File(imagePath);
		BufferedImage originalImage = ImageIO.read(imageFile);

		int orientation = 0;
		try {
			orientation = (Integer) new VegaImageMetadataExtractor(imageFile).extract(Requirements.REQUIRE_BASICS).get(ImageMetadataTag.ORIENTATION);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		BufferedImage rotatedImg = originalImage;

		switch (orientation) {
		case 0:
			// Normal portrait, continue
			break;
		case 90:
			// Rotate clockwise 270 degrees
			rotatedImg = ImageUtils.transpose(originalImage, IppTransposeType.ROTATE_270);
			break;
		case 180:
			// Rotate clockwise 180 degrees
			rotatedImg = ImageUtils.transpose(originalImage, IppTransposeType.ROTATE_180);
			break;
		case 270:
			// Rotate clockwise 90 degrees
			rotatedImg = ImageUtils.transpose(originalImage, IppTransposeType.ROTATE_90);
			break;    
		default:
			break;
		}       

		try {
			ImageIO.write(rotatedImg, "jpg", new File(imagePath));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void rotate90(String imagePath) throws IOException, ImageMetadataException {

		File imageFile = new File(imagePath);
		BufferedImage originalImage = ImageIO.read(imageFile);

		BufferedImage rotatedImg = originalImage;

		// Normal portrait, continue
		// Rotate clockwise 270 degrees
		//		rotatedImg = ImageUtils.transpose(originalImage, IppTransposeType.ROTATE_270);
		//		// Rotate clockwise 180 degrees
		//		rotatedImg = ImageUtils.transpose(originalImage, IppTransposeType.ROTATE_180);

		// Rotate clockwise 90 degrees
		rotatedImg = ImageUtils.transpose(originalImage, IppTransposeType.ROTATE_90);

		try {
			ImageIO.write(rotatedImg, "jpg", new File(imagePath));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void rotate270(String imagePath) throws IOException, ImageMetadataException {

		File imageFile = new File(imagePath);
		BufferedImage originalImage = ImageIO.read(imageFile);

		BufferedImage rotatedImg = originalImage;

		// Normal portrait, continue
		// Rotate clockwise 270 degrees
		//		rotatedImg = ImageUtils.transpose(originalImage, IppTransposeType.ROTATE_270);
		//		// Rotate clockwise 180 degrees
		//		rotatedImg = ImageUtils.transpose(originalImage, IppTransposeType.ROTATE_180);

		// Rotate clockwise 90 degrees
		rotatedImg = ImageUtils.transpose(originalImage, IppTransposeType.ROTATE_270);

		try {
			ImageIO.write(rotatedImg, "jpg", new File(imagePath));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static BufferedImage cropImage(File filePath, int x, int y, int w, int h){

		try {
			BufferedImage originalImgage = ImageIO.read(filePath);

			BufferedImage subImgage = originalImgage.getSubimage(x, y, w, h);

			return subImgage;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private void processFacialExpression(String imageDirecotory) {
		String str = null;
		String path = System.getProperty("user.dir");
		String faceImagesPath = imageDirecotory + "/Results/Faces";
		String exePath = path + "/Other/models/FacialExpression/pyinstaller/predictBatchFastForWindows.exe";
		String metaPath = path + "/Other/models/FacialExpression/checkpoints/my_model-9909.meta";
		String checkpointsPath = path + "/Other/models/FacialExpression/checkpoints";
		// Now Trying to call the exe file from python code with all dependencies included!
		try {
			ProcessBuilder pb = new ProcessBuilder(exePath, faceImagesPath, metaPath, checkpointsPath);
			Process p = pb.start();
			BufferedReader stdInput = new BufferedReader(new 
					InputStreamReader(p.getInputStream()));
			BufferedReader stdError = new BufferedReader(new 
					InputStreamReader(p.getErrorStream()));
			// read the output from the command
			System.out.println("Here is the standard output of the command:");
			while ((str = stdInput.readLine()) != null) {
				System.out.println(str);
			}
			// read any errors from the attempted command
			System.out.println("Here is the standard error of the command (if any):");
			while ((str = stdError.readLine()) != null) {
				System.out.println(str);
			}
		}
		catch (IOException e1) {
			System.err.println("Exception happened - here's what I know: ");
			e1.printStackTrace();
		}
	}

	private void processBKGdetection(String imageDirecotory) {
		String str = null;
		String path = System.getProperty("user.dir");
		String exePath = path + "/Other/models/BKGdetection/pyinstaller/predictBatchFastForWindows.exe";
		String metaPath = path + "/Other/models/BKGdetection/checkpoints/my_model-9600.meta";
		String checkpointsPath = path + "/Other/models/BKGdetection/checkpoints";
		// Now Trying to call the exe file from python code with all dependencies included!
		try {
			ProcessBuilder pb = new ProcessBuilder(exePath, imageDirecotory, metaPath, checkpointsPath);
			Process p = pb.start();
			BufferedReader stdInput = new BufferedReader(new 
					InputStreamReader(p.getInputStream()));
			BufferedReader stdError = new BufferedReader(new 
					InputStreamReader(p.getErrorStream()));
			// read the output from the command
			System.out.println("Here is the standard output of the command:");
			while ((str = stdInput.readLine()) != null) {
				System.out.println(str);
			}
			// read any errors from the attempted command
			System.out.println("Here is the standard error of the command (if any):");
			while ((str = stdError.readLine()) != null) {
				System.out.println(str);
			}
		}
		catch (IOException e1) {
			System.err.println("Exception happened - here's what I know: ");
			e1.printStackTrace();
		}
	}

	private void processCapGownDetection(String imageDirecotory) {
		String str = null;
		String path = System.getProperty("user.dir");
		String exePath = path + "/Other/models/CapAndGown/pyinstaller/predictBatchFastForWindows.exe";
		String metaPath = path + "/Other/models/CapAndGown/checkpoints/my_model-9500.meta";
		String checkpointsPath = path + "/Other/models/CapAndGown/checkpoints";
		// Now Trying to call the exe file from python code with all dependencies included!
		try {
			ProcessBuilder pb = new ProcessBuilder(exePath, imageDirecotory, metaPath, checkpointsPath);
			Process p = pb.start();
			BufferedReader stdInput = new BufferedReader(new 
					InputStreamReader(p.getInputStream()));
			BufferedReader stdError = new BufferedReader(new 
					InputStreamReader(p.getErrorStream()));
			// read the output from the command
			System.out.println("Here is the standard output of the command:");
			while ((str = stdInput.readLine()) != null) {
				System.out.println(str);
			}
			// read any errors from the attempted command
			System.out.println("Here is the standard error of the command (if any):");
			while ((str = stdError.readLine()) != null) {
				System.out.println(str);
			}
		}
		catch (IOException e1) {
			System.err.println("Exception happened - here's what I know: ");
			e1.printStackTrace();
		}
	}

	private void processGenderID(String imageDirecotory) {
		String str = null;
		String path = System.getProperty("user.dir");
		String exePath = path + "/Other/models/GenderIDNonFaces/pyinstaller/predictBatchFastForWindows.exe";
		String metaPath = path + "/Other/models/GenderIDNonFaces/checkpoints/my_model-9600.meta";
		String checkpointsPath = path + "/Other/models/GenderIDNonFaces/checkpoints";
		// Now Trying to call the exe file from python code with all dependencies included!
		try {
			ProcessBuilder pb = new ProcessBuilder(exePath, imageDirecotory, metaPath, checkpointsPath);
			Process p = pb.start();
			BufferedReader stdInput = new BufferedReader(new 
					InputStreamReader(p.getInputStream()));
			BufferedReader stdError = new BufferedReader(new 
					InputStreamReader(p.getErrorStream()));
			// read the output from the command
			System.out.println("Here is the standard output of the command:");
			while ((str = stdInput.readLine()) != null) {
				System.out.println(str);
			}
			// read any errors from the attempted command
			System.out.println("Here is the standard error of the command (if any):");
			while ((str = stdError.readLine()) != null) {
				System.out.println(str);
			}
		}
		catch (IOException e1) {
			System.err.println("Exception happened - here's what I know: ");
			e1.printStackTrace();
		}
	}

	private void prcoessPoseEstimation(List<String> listOfAcceptableImages, String myDirectoryPath) throws ImageMetadataException, IOException {

		// For testing purposes
		String textFilePathList = myDirectoryPath + "/Results/" + "listOfAcceptableImages.txt";
		Path textFilePathListPath = Paths.get(textFilePathList);
		for(int ii = 0; ii < listOfAcceptableImages.size(); ii++)
		{
			String imgPath = listOfAcceptableImages.get(ii);
//			System.out.println(imgPath);
			appendStringToFile(imgPath, textFilePathListPath);
		}
		// end of For testing purposes

		String textFilePath = myDirectoryPath + "/Results/" + "PoseResults.txt";
		Path results = Paths.get(textFilePath);
		File file = new File(results.toString());
		try {
			Files.deleteIfExists(file.toPath());
		} catch (IOException e) {
			System.err.println("Caught IOException: " + e.getMessage());
		}

		for(int ii = 0; ii < listOfAcceptableImages.size(); ii++)
		{
			String imgPath = listOfAcceptableImages.get(ii);
//			System.out.println(imgPath);
			appendStringToFile(imgPath, results);

			// Then check to see if any faces are found
			Finder faceFinder = new Finder();
			String stringLandmarks = faceFinder.DetectLandmarks(imgPath, -1);
			int[] binLandmarks = faceFinder.ParseMPEG4Landmarks(stringLandmarks);
			int faceCount = Finder.GetFaceCount(binLandmarks );
//			System.out.println("Face count = " + faceCount);
			int faceConfidence = 0;
			int faceConfidenceFinal = 0;
			int faceId = 0;
			// Chose only the high confidence faces
			if(faceCount > 0)
			{
				for( int i = 0; i <  faceCount; ++i )
				{
					faceConfidence = Finder.GetFeatureForFace(binLandmarks, FeatureIndex.ConfidenceFactorFace, i + 1);
//					System.out.println( "Face Confidence : " + faceConfidence);
					if(faceConfidence > faceConfidenceThreshold)
					{
						faceConfidenceFinal = faceConfidence;
//						System.out.println( "Face Confidence Final : " + faceConfidenceFinal);
						faceId = i + 1;
//						System.out.println( "Face ID : " + faceId);
					}
				}
			}

			if(faceCount > 0 && faceConfidenceFinal > faceConfidenceThreshold)
			{
				int chinX = Finder.GetFeatureForFace(binLandmarks, FeatureIndex.ChinX, faceId);
				int chinY = Finder.GetFeatureForFace(binLandmarks, FeatureIndex.ChinY, faceId);
				int eyeLeftX = Finder.GetFeatureForFace(binLandmarks, FeatureIndex.EyeLeftCentreX, faceId);
				int eyeLeftY = Finder.GetFeatureForFace(binLandmarks, FeatureIndex.EyeLeftCentreY, faceId);
				int eyeRightX = Finder.GetFeatureForFace(binLandmarks, FeatureIndex.EyeRightCentreX, faceId);
				int eyeRightY = Finder.GetFeatureForFace(binLandmarks, FeatureIndex.EyeRightCentreY, faceId);
				String pose = "NA";

				if(chinX > 0 && chinY > 0 && eyeLeftX > 0 && eyeLeftY > 0 && 
						eyeRightX > 0 && eyeRightY > 0)
				{
					FaceFeatureForPose faceFeatureForPose = findFaceFeatures(imgPath, faceCount, chinX, chinY, 
							eyeLeftX, eyeLeftY, eyeRightX, eyeRightY);
					String orientation = findOrientation(imgPath);
//					System.out.println("Orientation" + orientation);

					double faceMeasure = 0;
					if(orientation.equals("horizontal")){
						faceMeasure = faceFeatureForPose.eyeToChinToImageWidthRatio;
					}else if(orientation.equals("vertical")){
						faceMeasure = faceFeatureForPose.eyeToChinToImageHeightRatio;
					}

					if(faceMeasure<thresholdFullLengthHigh){
						pose = "FullLength";
						System.out.println("Pose:" + pose);
						appendStringToFile(pose, results);
					}else if(faceMeasure>=thresholdThreeQuarterLow && faceMeasure<thresholdThreeQuarterHigh){
						pose = "ThreeQuarterLength";
						System.out.println("Pose:" + pose);
						appendStringToFile(pose, results);
					}else if(faceMeasure>=thresholdHalfLengthLow && faceMeasure<thresholdHalfLengthHigh){
						pose = "HalfLength";
						System.out.println("Pose:" + pose);
						appendStringToFile(pose, results);
					}else if(faceMeasure>=thresholdHeadAndShouldersLow && faceMeasure<thresholdHeadAndShouldersHigh){
						pose = "HeadAndShoulders";
						System.out.println("Pose:" + pose);
						appendStringToFile(pose, results);
					}else if(faceMeasure>=thresholdCloseUpLow){
						pose = "CloseUp";
						System.out.println("Pose:" + pose);
						appendStringToFile(pose, results);
					}
				}else{ // if chin x,y < 0 or eye x,y < 0, then skip this image.
					System.out.println("ERROR! Could not find the Chin or Eye! Continuing to next image.");
					pose = "NA";
					appendStringToFile(pose, results);
				}
			}
		}
	}

	public FaceFeatureForPose findFaceFeatures(String imagePath,
			int faceCount, int chinX, int chinY, int eyeLeftX, int eyeLeftY, int eyeRightX, 
			int eyeRightY) throws ImageMetadataException, IOException
	{
		BufferedImage bimg 		= ImageIO.read(new File(imagePath));
		int imageWidth          = bimg.getWidth();
		int imageHeight         = bimg.getHeight();

		FaceFeatureForPose returnedResults = new FaceFeatureForPose();
		returnedResults.eyeToChinToImageHeightRatio = 0;
		returnedResults.eyeToChinToImageWidthRatio = 0;

		if(faceCount > 0)
		{
			int eyePointMiddleX = (int) (eyeLeftX + eyeRightX)/2;
			int eyePointMiddleY = (int) (eyeLeftY + eyeRightY)/2;
			double eyeToChinDistance = Math.sqrt((eyePointMiddleX - chinX)*(eyePointMiddleX - chinX) +
					(eyePointMiddleY - chinY)*(eyePointMiddleY - chinY));

			double eyeToChinToImageHeightRatio = eyeToChinDistance / imageHeight;
			//			System.out.printf("Eye to Chin to Image Height = %.2f ", eyeToChinToImageHeightRatio);

			double eyeToChinToImageWidthRatio = eyeToChinDistance / imageWidth;
			//			System.out.printf("Eye to Chin to Image Width = %.2f ", eyeToChinToImageWidthRatio);

			returnedResults.eyeToChinToImageHeightRatio = eyeToChinToImageHeightRatio;
			returnedResults.eyeToChinToImageWidthRatio = eyeToChinToImageWidthRatio;
		}
		return returnedResults;
	}

	public static void appendStringToFile(String message, Path file) throws IOException  {
		try {
			final Path path = file;
			Files.write(file, Arrays.asList(message), StandardCharsets.UTF_8,
					Files.exists(path) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE);
		} catch (final IOException e) {
			System.err.println("Caught IOException: " + e.getMessage());
		}
	}

	private String findOrientation(String imagePath) throws IOException {
		File imageFile = new File(imagePath);
		BufferedImage bufferedImage = ImageIO.read(imageFile);
		int imageWidth  = bufferedImage.getWidth();
		int imageHeight = bufferedImage.getHeight();
		String orientation = "";
		if(imageWidth > imageHeight){
			orientation = "horizontal";
		}else{
			orientation = "vertical";
		}
//		System.out.println("Orientation:" + orientation);

		return orientation;
	}

	public int[] calculateRGB(String imagePath, int faceCount,
			int[] binLandmarks, int eyeRightX, int eyeRightY) throws ImageMetadataException, IOException
	{

		int[] averageRGB = new int[3];

		BufferedImage bufferedImage = ImageIO.read(new File(imagePath));

		int cropWidthStart = (int) (eyeRightX/4);
		int cropWidthEnd = (int) (eyeRightX*3/4);
		int cropHeightStart = (int) (eyeRightY/4);
		int cropHeightEnd = (int) (eyeRightY*3/4);

		//		int cropWidthStart = 5;
		//		int cropWidthEnd = bufferedImage.getWidth() - 5;
		//		int cropHeightStart = 5;
		//		int cropHeightEnd = bufferedImage.getHeight()/10;

		int cropWidth = cropWidthEnd - cropWidthStart + 1;
		int cropHeight = cropHeightEnd - cropHeightStart + 1;
		//        System.out.printf("cropWidthStart = %d, cropHeightStart=%d, cropWidth=%d, cropHeight=%d",
		//        		cropWidthStart, cropHeightStart, cropWidth, cropHeight); 



		WritableRaster rasterimg = bufferedImage.getRaster();
		int[] iArray = null;
		int[] imagePixels = rasterimg.getPixels(cropHeightStart - 1, cropWidthStart - 1, cropHeight, cropWidth, iArray);
		int imagePixelsR[][] = new int[cropWidth][cropHeight];
		int imagePixelsG[][] = new int[cropWidth][cropHeight];
		int imagePixelsB[][] = new int[cropWidth][cropHeight];
		double redValue = 0; // using double so we don't go over max integer value
		double greenValue = 0;
		double blueValue = 0;
		for (int i = 0; i < cropWidth; i++) {
			for (int j = 0; j < cropHeight; j++) {
				imagePixelsR[i][j] = imagePixels[3 * i * cropHeight + 3 * j];
				imagePixelsG[i][j] = imagePixels[3 * i * cropHeight + 3 * j + 1];
				imagePixelsB[i][j] = imagePixels[3 * i * cropHeight + 3 * j + 2];
				redValue = redValue + imagePixelsR[i][j];
				greenValue = greenValue + imagePixelsG[i][j];
				blueValue = blueValue + imagePixelsB[i][j];
			}
		}
		// Finally calculate the RGB average linear values and return it
		int redAverage = (int) (redValue / (cropHeight * cropWidth));
		//        System.out.printf("Average Red = %d ", redAverage); 
		int greenAverage = (int) (greenValue / (cropHeight * cropWidth));
		//        System.out.printf("Average Green = %d ", greenAverage); 
		int blueAverage = (int) (blueValue / (cropHeight * cropWidth));
		//        System.out.printf("Average Blue = %d ", blueAverage); 

		averageRGB[0] = redAverage;
		averageRGB[1] = greenAverage;
		averageRGB[2] = blueAverage;
		return averageRGB;
	}

	private static void createFinalTextResults(String myDirectoryPath, List<File> files) throws IOException {

		System.out.println("Starting to create the Final Text file.");
		long startTime = System.nanoTime();

		String faceResultsPath = myDirectoryPath + "/Results/Faces/ExpressionPredictionResults.txt";
		String poseResultsPath = myDirectoryPath + "/Results/PoseResults.txt";
		String bKGResultsPath = myDirectoryPath + "/BKGPredictionResults.txt";
		String capGownResultsPath = myDirectoryPath + "/CapGownPredictionResults.txt";
		String genderIDResultsPath = myDirectoryPath + "/GenderIDPredictionResults.txt";
		String textFilePath = myDirectoryPath + "/Results/" + "CombinedResults.txt";
		Path directoryface = Paths.get(faceResultsPath);
		Path directorypose = Paths.get(poseResultsPath);
		Path directoryBKG = Paths.get(bKGResultsPath);
		Path directoryCapGown = Paths.get(capGownResultsPath);
		Path directoryGenderID = Paths.get(genderIDResultsPath);
		Path results = Paths.get(textFilePath);

		if (!Files.exists(directoryface)) {
			System.err.println("Face results text does not exist! exiting now...");
			JOptionPane.showMessageDialog(null, "Face results text does not exist! exiting now..." );
			System.exit(0);;
		}
		if (!Files.exists(directorypose)) {
			System.err.println("Pose results text does not exist! exiting now...");
			JOptionPane.showMessageDialog(null, "Pose results text does not exist! exiting now..." );
			System.exit(0);;
		}
		if (!Files.exists(directoryBKG)) {
			System.err.println("BKG results text does not exist! exiting now...");
			JOptionPane.showMessageDialog(null, "BKG results text does not exist! exiting now..." );
			System.exit(0);;
		}
		if (!Files.exists(directoryCapGown)) {
			System.err.println("Cap and Gown results text does not exist! exiting now...");
			JOptionPane.showMessageDialog(null, "Cap and Gown results text does not exist! exiting now..." );
			System.exit(0);;
		}
		if (!Files.exists(directoryGenderID)) {
			System.err.println("Gender ID results text does not exist! exiting now...");
			JOptionPane.showMessageDialog(null, "Gender ID results text does not exist! exiting now..." );
			System.exit(0);;
		}
		File file = new File(results.toString());
		try {
			Files.deleteIfExists(file.toPath());
		} catch (IOException e) {
			System.err.println("Caught IOException: " + e.getMessage());
		}

		//		File dir = new File(myDirectoryPath);
		//		String[] extensions = new String[] { "jpg" , "JPG" };
		//		System.out.println("Getting all .jpg and .JPG files in " + dir.getCanonicalPath()
		//		+ " including those in subdirectories");
		//		List<File> files = (List<File>) FileUtils.listFiles(dir, extensions, true);
		//		Collections.sort(files);
		int totalNumber = files.size();
		System.out.println("Total number of images in directory: " + totalNumber);

		// Add the header to text file
		String header1 = padRight("Image name", 30);
		String header2 = padRight("Facial expression", 20);
		String header3 = padRight("Pose", 20);
		String header4 = padRight("Background", 20);
		String header5 = padRight("Gender", 20);
		String header6 = padRight("Cap and Gown", 20);
		String header = header1 + "\t" + header2 + "\t" + header3 + "\t" + header4 + "\t" + header5 + "\t" + header6;
		appendStringToFile(header, results);

		int numFullSmile = 0; int numGameFace = 0; int numSoftSmile = 0;
		int numBKG1 = 0; int numBKG2 = 0; int numBKG3 = 0; int numBKG4 = 0; int numBKG5 = 0; int numBKG6 = 0;
		int numBKG7 = 0; int numBKG8 = 0; int numBKG9 = 0; int numBKG10 = 0; int numBKG11 = 0; int numBKG12 = 0;
		int numBKG13 = 0; int numBKG14 = 0; int numBKG15 = 0; int numBKG16 = 0;
		int numFullLength = 0; int numThreeQuarter = 0; int numHalfLength = 0; int numHeadAndShoulder = 0; int numCloseUp = 0;
		int numMale = 0; int numFemale = 0;
		int numCapGown = 0; int numNoCapGown = 0;
		String facialExpression = "NA"; String background = "NA"; String pose = "NA"; String gender = "NA"; String capGown = "NA";

		//Reading file
		int markSize = 100000000;
		BufferedReader br1 = new BufferedReader(new FileReader( new File(poseResultsPath)));
		br1.mark(markSize); 
		BufferedReader br2 = new BufferedReader(new FileReader( new File(bKGResultsPath)));
		br2.mark(markSize); 
		BufferedReader br3 = new BufferedReader(new FileReader( new File(capGownResultsPath)));
		br3.mark(markSize); 
		BufferedReader br4 = new BufferedReader(new FileReader( new File(genderIDResultsPath)));
		br4.mark(markSize); 
		BufferedReader br5 = new BufferedReader(new FileReader( new File(faceResultsPath)));
		br5.mark(markSize); 

		for (int i = 0; i < totalNumber; i++) {
			System.out.println("Searching for image number " + (i + 1) + " Out of Total " + totalNumber);
			String imageName = files.get(i).getName();
			//			System.out.println(imageName);

			// First Pose
			pose = "NA";
			String line;
			if (br1.readLine() == null) { 
				//end of file reached or the variable was just set up as null
				br1.close();
				br1 = new BufferedReader(new FileReader( new File(poseResultsPath)));
				br1.mark(markSize); 
			}
			br1.reset();
			while((line = br1.readLine())!= null){
				if(line.contains(imageName)){
					pose = br1.readLine();
					if(pose.equals("FullLength"))
					{
						numFullLength = numFullLength + 1;
						break;
					}else if(pose.equals("ThreeQuarterLength"))
					{
						numThreeQuarter = numThreeQuarter + 1;
						break;
					}else if(pose.equals("HalfLength"))
					{
						numHalfLength = numHalfLength + 1;
						break;
					}else if(pose.equals("HeadAndShoulders"))
					{
						numHeadAndShoulder = numHeadAndShoulder + 1;
						break;
					}else if(pose.equals("CloseUp"))
					{
						numCloseUp = numCloseUp + 1;
						break;
					}
					break;
				}
			}

			// Then search for image name inside BKG results text 
			background = "NA";
			if (br2.readLine() == null) { 
				//end of file reached or the variable was just set up as null
				br2.close();
				br2 = new BufferedReader(new FileReader( new File(bKGResultsPath)));
				br2.mark(markSize); 
			}
			br2.reset();
			while((line = br2.readLine())!= null){
				if(line.contains(imageName)){
					String probs = br2.readLine();
					String[] allProbs = probs.split("\t", -1);
					double prob1 = Double.parseDouble(allProbs[0]);
					double prob2 = Double.parseDouble(allProbs[1]);
					double prob3 = Double.parseDouble(allProbs[2]);
					double prob4 = Double.parseDouble(allProbs[3]);
					double prob5 = Double.parseDouble(allProbs[4]);
					double prob6 = Double.parseDouble(allProbs[5]);
					double prob7 = Double.parseDouble(allProbs[6]);
					double prob8 = Double.parseDouble(allProbs[7]);
					double prob9 = Double.parseDouble(allProbs[8]);
					double prob10 = Double.parseDouble(allProbs[9]);
					double prob11 = Double.parseDouble(allProbs[10]);
					double prob12 = Double.parseDouble(allProbs[11]);
					double prob13 = Double.parseDouble(allProbs[12]);
					double prob14 = Double.parseDouble(allProbs[13]);
					double prob15 = Double.parseDouble(allProbs[14]);
					double prob16 = Double.parseDouble(allProbs[15]);
					if(prob1 > prob2 && prob1 > prob3 && prob1 > prob4 && prob1 > prob5 && prob1 > prob6
							&& prob1 > prob7 && prob1 > prob8 && prob1 > prob9 && prob1 > prob10
							&& prob1 > prob11 && prob1 > prob12 && prob1 > prob13 && prob1 > prob14
							&& prob1 > prob15 && prob1 > prob16)
					{
						background = "BKG1";
						numBKG1 = numBKG1 + 1;
						break;
					}else if(prob2 > prob1 && prob2 > prob3 && prob2 > prob4 && prob2 > prob5 && prob2 > prob6
							&& prob2 > prob7 && prob2 > prob8 && prob2 > prob9 && prob2 > prob10
							&& prob2 > prob11 && prob2 > prob12 && prob2 > prob13 && prob2 > prob14
							&& prob2 > prob15 && prob2 > prob16)
					{
						background = "BKG2";
						numBKG2 = numBKG2 + 1;
						break;
					}else if(prob3 > prob1 && prob3 > prob2 && prob3 > prob4 && prob3 > prob5 && prob3 > prob6
							&& prob3 > prob7 && prob3 > prob8 && prob3 > prob9 && prob3 > prob10
							&& prob3 > prob11 && prob3 > prob12 && prob3 > prob13 && prob3 > prob14
							&& prob3 > prob15 && prob3 > prob16)
					{
						background = "BKG3";
						numBKG3 = numBKG3 + 1;
						break;
					}else if(prob4 > prob1 && prob4 > prob2 && prob4 > prob3 && prob4 > prob5 && prob4 > prob6
							&& prob4 > prob7 && prob4 > prob8 && prob4 > prob9 && prob4 > prob10
							&& prob4 > prob11 && prob4 > prob12 && prob4 > prob13 && prob4 > prob14
							&& prob4 > prob15 && prob4 > prob16)
					{
						background = "BKG4";
						numBKG4 = numBKG4 + 1;
						break;
					}else if(prob5 > prob1 && prob5 > prob2 && prob5 > prob3 && prob5 > prob4 && prob5 > prob6
							&& prob5 > prob7 && prob5 > prob8 && prob5 > prob9 && prob5 > prob10
							&& prob5 > prob11 && prob5 > prob12 && prob5 > prob13 && prob5 > prob14
							&& prob5 > prob15 && prob5 > prob16)
					{
						background = "BKG5";
						numBKG5 = numBKG5 + 1;
						break;
					}else if(prob6 > prob1 && prob6 > prob2 && prob6 > prob3 && prob6 > prob4 && prob6 > prob5
							&& prob6 > prob7 && prob6 > prob8 && prob6 > prob9 && prob6 > prob10
							&& prob6 > prob11 && prob6 > prob12 && prob6 > prob13 && prob6 > prob14
							&& prob6 > prob15 && prob6 > prob16)
					{
						background = "BKG6";
						numBKG6 = numBKG6 + 1;
						break;
					}else if(prob7 > prob2 && prob7 > prob3 && prob7 > prob4 && prob7 > prob5 && prob7 > prob6
							&& prob7 > prob1 && prob7 > prob8 && prob7 > prob9 && prob7 > prob10
							&& prob7 > prob11 && prob7 > prob12 && prob7 > prob13 && prob7 > prob14
							&& prob7 > prob15 && prob7 > prob16)
					{
						background = "BKG7";
						numBKG7 = numBKG7 + 1;
						break;
					}else if(prob8 > prob2 && prob8 > prob3 && prob8 > prob4 && prob8 > prob5 && prob8 > prob6
							&& prob8 > prob7 && prob8 > prob1 && prob8 > prob9 && prob8 > prob10
							&& prob8 > prob11 && prob8 > prob12 && prob8 > prob13 && prob8 > prob14
							&& prob8 > prob15 && prob8 > prob16)
					{
						background = "BKG8";
						numBKG8 = numBKG8 + 1;
						break;
					}else if(prob9 > prob2 && prob9 > prob3 && prob9 > prob4 && prob9 > prob5 && prob9 > prob6
							&& prob9 > prob7 && prob9 > prob8 && prob9 > prob1 && prob9 > prob10
							&& prob9 > prob11 && prob9 > prob12 && prob9 > prob13 && prob9 > prob14
							&& prob9 > prob15 && prob9 > prob16)
					{
						background = "BKG9";
						numBKG9 = numBKG9 + 1;
						break;
					}else if(prob10 > prob2 && prob10 > prob3 && prob10 > prob4 && prob10 > prob5 && prob10 > prob6
							&& prob10 > prob7 && prob10 > prob8 && prob10 > prob9 && prob10 > prob1
							&& prob10 > prob11 && prob10 > prob12 && prob10 > prob13 && prob10 > prob14
							&& prob10 > prob15 && prob10 > prob16)
					{
						background = "BKG10";
						numBKG10 = numBKG10 + 1;
						break;
					}else if(prob11 > prob2 && prob11 > prob3 && prob11 > prob4 && prob11 > prob5 && prob11 > prob6
							&& prob11 > prob7 && prob11 > prob8 && prob11 > prob9 && prob11 > prob10
							&& prob11 > prob1 && prob11 > prob12 && prob11 > prob13 && prob11 > prob14
							&& prob11 > prob15 && prob11 > prob16)
					{
						background = "BKG11";
						numBKG11 = numBKG11 + 1;
						break;
					}else if(prob12 > prob2 && prob12 > prob3 && prob12 > prob4 && prob12 > prob5 && prob12 > prob6
							&& prob12 > prob7 && prob12 > prob8 && prob12 > prob9 && prob12 > prob10
							&& prob12 > prob11 && prob12 > prob1 && prob12 > prob13 && prob12 > prob14
							&& prob12 > prob15 && prob12 > prob16)
					{
						background = "BKG12";
						numBKG12 = numBKG12 + 1;
						break;
					}else if(prob13 > prob2 && prob13 > prob3 && prob13 > prob4 && prob13 > prob5 && prob13 > prob6
							&& prob13 > prob7 && prob13 > prob8 && prob13 > prob9 && prob13 > prob10
							&& prob13 > prob11 && prob13 > prob12 && prob13 > prob1 && prob13 > prob14
							&& prob13 > prob15 && prob13 > prob16)
					{
						background = "BKG13";
						numBKG13 = numBKG13 + 1;
						break;
					}else if(prob14 > prob2 && prob14 > prob3 && prob14 > prob4 && prob14 > prob5 && prob14 > prob6
							&& prob14 > prob7 && prob14 > prob8 && prob14 > prob9 && prob14 > prob10
							&& prob14 > prob11 && prob14 > prob12 && prob14 > prob13 && prob14 > prob1
							&& prob14 > prob15 && prob14 > prob16)
					{
						background = "BKG14";
						numBKG14 = numBKG14 + 1;
						break;
					}else if(prob15 > prob2 && prob15 > prob3 && prob15 > prob4 && prob15 > prob5 && prob15 > prob6
							&& prob15 > prob7 && prob15 > prob8 && prob15 > prob9 && prob15 > prob10
							&& prob15 > prob11 && prob15 > prob12 && prob15 > prob13 && prob15 > prob14
							&& prob15 > prob1 && prob15 > prob16)
					{
						background = "BKG15";
						numBKG15 = numBKG15 + 1;
						break;
					}else if(prob16 > prob2 && prob16 > prob3 && prob16 > prob4 && prob16 > prob5 && prob16 > prob6
							&& prob16 > prob7 && prob16 > prob8 && prob16 > prob9 && prob16 > prob10
							&& prob16 > prob11 && prob16 > prob12 && prob16 > prob13 && prob16 > prob14
							&& prob16 > prob15 && prob16 > prob1)
					{
						background = "BKG16";
						numBKG16 = numBKG16 + 1;
						break;
					}
					break;
				}
			}

			// Then search for image name inside Cap and Gown results text 
			capGown = "NA";
			if (br3.readLine() == null) { 
				//end of file reached or the variable was just set up as null
				br3.close();
				br3 = new BufferedReader(new FileReader( new File(capGownResultsPath)));
				br3.mark(markSize); 
			}
			br3.reset();
			while((line = br3.readLine())!= null){
				if(line.contains(imageName)){
					String capAndGown = br3.readLine();
					String[] columnDetail = capAndGown.split("\t", -1);
					double prob1 = Double.parseDouble(columnDetail[0]);
					double prob2 = Double.parseDouble(columnDetail[1]);
					if(prob1 > prob2)
					{
						capGown = "Yes";
						numCapGown = numCapGown + 1;
						break;
					}else if(prob2 > prob1)
					{
						capGown = "No";
						numNoCapGown = numNoCapGown + 1;
						break;
					}
					break;
				}
			}

			// Then search for image name inside Gender ID results text 
			gender = "NA";
			if (br4.readLine() == null) { 
				//end of file reached or the variable was just set up as null
				br4.close();
				br4 =  new BufferedReader(new FileReader( new File(genderIDResultsPath)));
				br4.mark(markSize); 
			}
			br4.reset();
			while((line = br4.readLine())!= null){
				if(line.contains(imageName)){
					String genderID = br4.readLine();
					String[] columnDetail = genderID.split("\t", -1);
					double prob1 = Double.parseDouble(columnDetail[0]);
					double prob2 = Double.parseDouble(columnDetail[1]);
					if(prob1 > prob2)
					{
						gender = "Female";
						numFemale = numFemale + 1;
						break;
					}else if(prob2 > prob1)
					{
						gender = "Male";
						numMale = numMale + 1;
						break;
					}
					break;
				}
			}

			// Finally search for image name inside Face results text 
			facialExpression = "NA";
			if (br5.readLine() == null) { 
				//end of file reached or the variable was just set up as null
				br5.close();
				br5 = new BufferedReader(new FileReader( new File(faceResultsPath)));
				br5.mark(markSize); 
			}
			br5.reset();
			while((line = br5.readLine())!= null){
				if(line.contains(imageName)){
					String expressions = br5.readLine();
					String[] columnDetail = expressions.split("\t", -1);
					double prob1 = Double.parseDouble(columnDetail[0]);
					double prob2 = Double.parseDouble(columnDetail[1]);
					double prob3 = Double.parseDouble(columnDetail[2]);
					if(prob1 > prob2 && prob1 > prob3)
					{
						facialExpression = "FullSmile";
						numFullSmile = numFullSmile + 1;
						break;
					}else if(prob2 > prob1 && prob2 > prob3)
					{
						facialExpression = "GameFace";
						numGameFace = numGameFace + 1;
						break;
					}else if(prob3 > prob1 && prob3 > prob2)
					{
						facialExpression = "SoftSmile";
						numSoftSmile = numSoftSmile + 1;
						break;
					}
					break;
				}
			}

			// Combine all
			String text1 = padRight(imageName, 30);
			String text2 = padRight(facialExpression, 20);
			String text3 = padRight(pose, 20);
			String text4 = padRight(background, 20);
			String text5 = padRight(gender, 20);
			String text6 = padRight(capGown, 20);
			String textToWrite = text1 + "\t" + text2 + "\t" + text3 + "\t" + text4 + "\t" + text5 + "\t" + text6;
			// Now write to final results text file
			appendStringToFile(textToWrite, results);
		}
		br1.close();
		br2.close();
		br3.close();
		br4.close();
		br5.close();

		int    totalNumExpressions = numFullSmile + numGameFace + numSoftSmile;
		double percentFullSmile = round((double) numFullSmile / (totalNumExpressions), 2); percentFullSmile = round (percentFullSmile*100, 2);
		double percentGameFace = round((double) numGameFace / (totalNumExpressions), 2); percentGameFace = round (percentGameFace*100, 2);
		double percentSoftSmile = round((double) numSoftSmile / (totalNumExpressions), 2); percentSoftSmile = round (percentSoftSmile*100, 2);

		int    numTotalBKG = numBKG1 + numBKG2 + numBKG3 + numBKG4 + numBKG5 
				+ numBKG6 + numBKG7 + numBKG8 + numBKG9 + numBKG10 + numBKG11 
				+ numBKG12 + numBKG13 + numBKG14 + numBKG15 + numBKG16;
		double percentBKG1 = round((double) numBKG1 / numTotalBKG, 2); double percentBKG2 = round((double) numBKG2 / numTotalBKG, 2);
		double percentBKG3 = round((double) numBKG3 / numTotalBKG, 2); double percentBKG4 = round((double) numBKG4 / numTotalBKG, 2);
		double percentBKG5 = round((double) numBKG5 / numTotalBKG, 2); double percentBKG6 = round((double) numBKG6 / numTotalBKG, 2);
		double percentBKG7 = round((double) numBKG7 / numTotalBKG, 2); double percentBKG8 = round((double) numBKG8 / numTotalBKG, 2);
		double percentBKG9 = round((double) numBKG9 / numTotalBKG, 2); double percentBKG10 = round((double) numBKG10 / numTotalBKG, 2);
		double percentBKG11 = round((double) numBKG11 / numTotalBKG, 2); double percentBKG12 = round((double) numBKG12 / numTotalBKG, 2);
		double percentBKG13 = round((double) numBKG13 / numTotalBKG, 2); double percentBKG14 = round((double) numBKG14 / numTotalBKG, 2);
		double percentBKG15 = round((double) numBKG15 / numTotalBKG, 2); double percentBKG16 = round((double) numBKG16 / numTotalBKG, 2);
		percentBKG1 = round (percentBKG1*100, 2); percentBKG2 = round (percentBKG2*100, 2); percentBKG3 = round (percentBKG3*100, 2);
		percentBKG4 = round (percentBKG4*100, 2); percentBKG5 = round (percentBKG5*100, 2); percentBKG6 = round (percentBKG6*100, 2);
		percentBKG7 = round (percentBKG7*100, 2); percentBKG8 = round (percentBKG8*100, 2); percentBKG9 = round (percentBKG9*100, 2);
		percentBKG10 = round (percentBKG10*100, 2); percentBKG11 = round (percentBKG11*100, 2); percentBKG12 = round (percentBKG12*100, 2);
		percentBKG13 = round (percentBKG13*100, 2); percentBKG14 = round (percentBKG14*100, 2); percentBKG15 = round (percentBKG15*100, 2);
		percentBKG16 = round (percentBKG16*100, 2);


		int    totalNumPose = numFullLength + numThreeQuarter + numHalfLength + numHeadAndShoulder + numCloseUp;
		double percentFullLength = round((double) numFullLength / (totalNumPose), 2); percentFullLength = round (percentFullLength*100, 2);
		double percentThreeQuarter = round((double) numThreeQuarter / (totalNumPose), 2); percentThreeQuarter = round (percentThreeQuarter*100, 2);
		double percentHalfLength = round((double) numHalfLength / (totalNumPose), 2); percentHalfLength = round (percentHalfLength*100, 2);
		double percentHeadAndShoulder = round((double) numHeadAndShoulder / (totalNumPose), 2); percentHeadAndShoulder = round (percentHeadAndShoulder*100, 2);
		double percentCloseUp = round((double) numCloseUp / (totalNumPose), 2); percentCloseUp = round (percentCloseUp*100, 2);

		int    totalNumGender = numMale + numFemale;
		double percentFemale = round((double) numFemale / (totalNumGender), 2); percentFemale = round (percentFemale*100, 2);
		double percentMale = round((double) numMale / (totalNumGender), 2); percentMale = round (percentMale*100, 2);

		int    totalNumCloth = numCapGown + numNoCapGown;
		double percentCapGown = round((double) numCapGown / (totalNumCloth), 2); percentCapGown = round (percentCapGown*100, 2);
		double percentNoCapGown = round((double) numNoCapGown / (totalNumCloth), 2); percentNoCapGown = round (percentNoCapGown*100, 2);

		String summaryToWrite = "FullSmile:" + percentFullSmile + "%," + numFullSmile + "\t"
				+ "GameFace:" + percentGameFace + "%," + numGameFace + "\t" + "SoftSmile:" 
				+ percentSoftSmile + "%," + numSoftSmile + "\t" + "\n"
				+ "BKG1:" + percentBKG1 + "%," + numBKG1 + "\t" + "BKG2:" + percentBKG2 + "%," + numBKG2 + "\t"
				+ "BKG3:" + percentBKG3 + "%," + numBKG3 + "\t" + "BKG4:" + percentBKG4 + "%," + numBKG4 + "\t"
				+ "BKG5:" + percentBKG5 + "%," + numBKG5 + "\t" + "BKG6:" + percentBKG6 + "%," + numBKG6 + "\t" 
				+ "BKG7:" + percentBKG7 + "%," + numBKG7 + "\t" + "BKG8:" + percentBKG8 + "%," + numBKG8 + "\t" 
				+ "BKG9:" + percentBKG9 + "%," + numBKG9 + "\t" + "BKG10:" + percentBKG10 + "%," + numBKG10 + "\t" 
				+ "BKG11:" + percentBKG11 + "%," + numBKG11 + "\t" + "BKG12:" + percentBKG12 + "%," + numBKG12 + "\t" 
				+ "BKG13:" + percentBKG13 + "%," + numBKG13 + "\t" + "BKG14:" + percentBKG14 + "%," + numBKG14 + "\t" 
				+ "BKG15:" + percentBKG15 + "%," + numBKG15 + "\t" + "BKG16:" + percentBKG16 + "%," + numBKG16 + "\t" 
				+ "\n" + "FullLength:" + percentFullLength + "%," + numFullLength + "\t"
				+ "ThreeQuarter:" + percentThreeQuarter + "%," + numThreeQuarter + "\t" 
				+ "HalfLength:" + percentHalfLength + "%," + numHalfLength + "\t"
				+ "HeadAndShoulder:" + percentHeadAndShoulder + "%," + numHeadAndShoulder 
				+ "\t" + "CloseUp:" + percentCloseUp + "%," + numCloseUp + "\t"
				+ "\n" + "Female:" + percentFemale + "%," + numFemale + "\t" 
				+ "Male:" + percentMale + "%," + numMale + "\t"
				+ "\n" + "Cap and Gown:" + percentCapGown + "%," + numCapGown 
				+ "\t" + "NO cap and Gown:" + percentNoCapGown + "%," + numNoCapGown + "\t";
		System.out.println(summaryToWrite);

		// Now write to final results text file
		appendStringToFile("\n", results);
		appendStringToFile("Total Number of Images:", results);
		appendStringToFile(Integer.toString(totalNumber), results);
		appendStringToFile("Summary (in percentage): ", results);
		appendStringToFile(summaryToWrite, results);

		long endTime   = System.nanoTime();
		long totalTime = endTime - startTime;
		System.out.println("Total time in seconds for creating final text file = " + totalTime/1e9);
	}

	public static String padRight(String s, int n) {
		return String.format("%1$-" + n + "s", s);  
	}

	public static String padLeft(String s, int n) {
		return String.format("%1$" + n + "s", s);  
	}

	public static double round(double value, int places) {
		if (places < 0) throw new IllegalArgumentException();

		BigDecimal bd = new BigDecimal(value);
		bd = bd.setScale(places, RoundingMode.HALF_UP);
		return (double) bd.doubleValue();
	}
}
