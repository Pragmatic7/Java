package imagelearn;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.jpeg.JpegDirectory;
import com.lifetouch.lti.crypto.exception.CipherException;

public class MirrorlessCalibration {

	private static final double RADIUS_THRESHOLD = 0.8;//0.8

	DetectedCirclesSpecs detectThreeRedCircles(String imagePath) throws ExceptionCalibration, MetadataException, ImageProcessingException, IOException, ExceptionExif, InvalidKeyException, CipherException {
		try {
			// Instead of the usual RGB color space we are going to use the HSV
			// space to identify a particular color using a single value, the hue,
			// instead of three values. In OpenCV H has values from 0 to 179,
			// S and V from 0 to 255.
			// The red color, in OpenCV, has the hue values approximately in the
			// range of 0 to 10 and 160 to 180.
			System.out.println("Mirrorless Calibration started.");

			// Read and auto rotate image 
			Mat bgrImageNormalized = readAutoRotateMat(imagePath);

			// Median filtering for noise removal
			Imgproc.medianBlur(bgrImageNormalized, bgrImageNormalized, 3);

			// Convert input image to HSV
			Mat hsvImage = new Mat();
			Imgproc.cvtColor(bgrImageNormalized, hsvImage, Imgproc.COLOR_BGR2HSV);

			bgrImageNormalized.release();

			// Threshold the HSV image, keep only the red pixels
			Mat lowerRedHueRange = new Mat();
			Mat upperRedHueRange = new Mat();
			Scalar threshold1 = new Scalar(0, 100, 50);//0, 100, 100 // changed V to 50 to find circles on darker images
			Scalar threshold2 = new Scalar(10, 255, 255);//10, 255, 255
			Core.inRange(hsvImage, threshold1, threshold2, lowerRedHueRange);
			Scalar threshold3 = new Scalar(160, 100, 50);//160, 100, 100 // changed V to 50 to find circles on darker images
			Scalar threshold4 = new Scalar(179, 255, 255);//179, 255, 255
			Core.inRange(hsvImage, threshold3, threshold4, upperRedHueRange);

			hsvImage.release();

			// Combine the above two images
			Mat redHueImage = new Mat();
			Core.addWeighted(lowerRedHueRange, 1.0, upperRedHueRange, 1.0, 0.0, redHueImage);

			lowerRedHueRange.release(); upperRedHueRange.release();
			Imgproc.GaussianBlur(redHueImage, redHueImage, new Size(9, 9), 2, 2);

			// Use the Hough transform to detect circles in the combined threshold image 
			// dp = 1: The inverse ratio of resolution
			// min_dist = src_gray.rows/8: Minimum distance between detected centers 
			// param_1 = 200: Upper threshold for the internal Canny edge detector 
			// param_2 = 100*: Threshold for center detection.
			// min_radius = 40: Minimum radio to be detected. If unknown, put zero as default. 
			// max_radius = 0: Maximum radius to be detected. If unknown, put zero as default.
			Mat circles = new Mat();
			Imgproc.HoughCircles(redHueImage, circles, Imgproc.CV_HOUGH_GRADIENT, 1, 
					((double) redHueImage.width()) / 8, 100, 20, 150, 500);
			int numberOfCirclesFound = (int) (circles.size().height * circles.size().width);
			//			System.out.println(String.format("Number of circles detected: %d", numberOfCirclesFound));

			redHueImage.release();

			// First check to see if less than 3 circles were found.
			ExceptionNumbers numberClass = new ExceptionNumbers();

			if (numberOfCirclesFound < 3) {
				int errorCode = numberClass.ExceptionCalibrationNumber1;
				String message = errorCode + "," + "Not enough circles found. Image is too dark. Check lighting and setup.";
				throw new ExceptionCalibration(message);
			}

			int[] radiusArray = new int[numberOfCirclesFound];
			int[][] centerPointsArray = new int[numberOfCirclesFound][2];
			for (int i = 0; i < circles.size().height; i++) {
				for (int j = 0; j < circles.size().width; j++) {
					Point center = new Point(Math.round(circles.get(i, j)[0]), Math.round(circles.get(i, j)[1]));
					//					System.out.println(String.format("Center of the circle detected: %.2f ; %.2f", center.x, center.y));
					//					int radius = (int) Math.round(circles.get(i, j)[2]);
					//					System.out.println(String.format("Radius of the circle detected: %d", radius));
					centerPointsArray[(i + 1) * j][0] = (int) center.x;
					centerPointsArray[(i + 1) * j][1] = (int) center.y;
					radiusArray[(i + 1) * j] = (int) Math.round(circles.get(i, j)[2]);
				}
			}

			if (numberOfCirclesFound > 3) {
				//				System.out.println("More than 3 circles found! Choosing the first 3 only!");
				// choosing only the first 3 circles centers and radius
				radiusArray = Arrays.copyOfRange(radiusArray, 0, 3);
				int[][] tempCenters = centerPointsArray;
				centerPointsArray = new int [3][2];
				for (int i = 0; i < 3; i++){
					for(int j = 0; j < 2; j++){
						centerPointsArray[i][j] = tempCenters[i][j];
					}
				}
			}

			circles.release();

			// Check to see if the circle radiuses are close
			// First sort the radiuses descending
			int[] radiusArraySorted = radiusArray;
			Arrays.sort(radiusArraySorted);
			ArrayUtils.reverse(radiusArraySorted);
			if ((double) radiusArraySorted[2] / radiusArraySorted[1] > RADIUS_THRESHOLD && (double) radiusArraySorted[2] / radiusArraySorted[1] <= 1
					&& (double) radiusArraySorted[2] / radiusArraySorted[0] > RADIUS_THRESHOLD && (double) radiusArraySorted[2] / radiusArraySorted[0] <= 1) {
				//				System.out.println(String.format("The circle radiuses match correctly. Continuing..."));
			} else {
				int errorCode = numberClass.ExceptionCalibrationNumber2;
				String message = errorCode + "," + "Circles radiuses don't match. Check lighting and setup.";
				throw new ExceptionCalibration(message);
			}

			int[] centerBottomLeft = new int[2];
			int[] centerBottomRight = new int[2];
			int[] centerTopRight = new int[2];

			if (centerPointsArray[2][1] < centerPointsArray[0][1] && centerPointsArray[2][1] < centerPointsArray[1][1]) {
				centerTopRight = centerPointsArray[2];
				if (centerPointsArray[0][0] < centerPointsArray[1][0] && centerPointsArray[0][0] < centerPointsArray[2][0]) {
					centerBottomLeft = centerPointsArray[0];
					centerBottomRight = centerPointsArray[1];
				} else if (centerPointsArray[1][0] < centerPointsArray[0][0] && centerPointsArray[1][0] < centerPointsArray[2][0]) {
					centerBottomLeft = centerPointsArray[1];
					centerBottomRight = centerPointsArray[0];
				}
			}

			if (centerPointsArray[1][1] < centerPointsArray[0][1] && centerPointsArray[1][1] < centerPointsArray[2][1]) {
				centerTopRight = centerPointsArray[1];
				if (centerPointsArray[0][0] < centerPointsArray[1][0] && centerPointsArray[0][0] < centerPointsArray[2][0]) {
					centerBottomLeft = centerPointsArray[0];
					centerBottomRight = centerPointsArray[2];
				} else if (centerPointsArray[2][0] < centerPointsArray[0][0] && centerPointsArray[2][0] < centerPointsArray[1][0]) {
					centerBottomLeft = centerPointsArray[2];
					centerBottomRight = centerPointsArray[0];
				}
			}

			if (centerPointsArray[0][1] < centerPointsArray[1][1] && centerPointsArray[0][1] < centerPointsArray[2][1]) {
				centerTopRight = centerPointsArray[0];
				if (centerPointsArray[1][0] < centerPointsArray[0][0] && centerPointsArray[1][0] < centerPointsArray[2][0]) {
					centerBottomLeft = centerPointsArray[1];
					centerBottomRight = centerPointsArray[2];
				} else if (centerPointsArray[2][0] < centerPointsArray[0][0] && centerPointsArray[2][0] < centerPointsArray[1][0]) {
					centerBottomLeft = centerPointsArray[2];
					centerBottomRight = centerPointsArray[1];
				}
			}

			// Return the detected circle centers and radiuses
			DetectedCirclesSpecs returnedCircleSpecs = new DetectedCirclesSpecs();
			int[] initialRadiusArray = { 0, 0, 0 };
			returnedCircleSpecs.setRadiusArray(initialRadiusArray);
			int[][] initialCenterPointsArray = { { 0, 0 }, { 0, 0 }, { 0, 0 } };
			returnedCircleSpecs.setCenterPointsArray(initialCenterPointsArray);
			returnedCircleSpecs.radiusArray = radiusArray;
			returnedCircleSpecs.centerPointsArray[0] = centerTopRight;
			returnedCircleSpecs.centerPointsArray[1] = centerBottomRight;
			returnedCircleSpecs.centerPointsArray[2] = centerBottomLeft;
			return returnedCircleSpecs;
		} catch (CvException e) {
			ExceptionNumbers numberClass = new ExceptionNumbers();
			int errorCode = numberClass.ExceptionCalibrationNumber3;
			String message = errorCode + "," + "OpenCV error in detectThreeRedCircles in calibration.";
			throw new ExceptionCalibration(message);
		}
	}

	private int[] cropBackground(DetectedCirclesSpecs returnedThreeCircleSpecs) {

		// Choose the background area above the top right circle on the gray card
		int cropWidthStart = (int) (returnedThreeCircleSpecs.centerPointsArray[0][1] - 4 * returnedThreeCircleSpecs.radiusArray[0]);
		int cropWidthEnd = (int) (returnedThreeCircleSpecs.centerPointsArray[0][1] - 2 * returnedThreeCircleSpecs.radiusArray[0]);
		int cropHeightStart = (returnedThreeCircleSpecs.centerPointsArray[2][0]);
		int cropHeightEnd = (returnedThreeCircleSpecs.centerPointsArray[1][0]);
		int cropHeight = cropHeightEnd - cropHeightStart + 1;
		int cropWidth = cropWidthEnd - cropWidthStart + 1;
		int[] cropSpecs = new int[4];
		cropSpecs[0] = cropHeightStart;
		cropSpecs[1] = cropWidthStart;
		cropSpecs[2] = cropHeight;
		cropSpecs[3] = cropWidth;
		return cropSpecs;
	}

	private int[] cropSubject(DetectedCirclesSpecs returnedThreeCircleSpecs) {

		// Choose the subject gray card crop area between the circles on the gray card
		int cropWidthStart = (int) (returnedThreeCircleSpecs.centerPointsArray[0][1] + 2 * returnedThreeCircleSpecs.radiusArray[0]);
		int cropWidthEnd = (int) (returnedThreeCircleSpecs.centerPointsArray[1][1] - 2 * returnedThreeCircleSpecs.radiusArray[1]);
		int cropHeightStart = (returnedThreeCircleSpecs.centerPointsArray[2][0]);
		int cropHeightEnd = (returnedThreeCircleSpecs.centerPointsArray[1][0]);
		int cropHeight = cropHeightEnd - cropHeightStart + 1;
		int cropWidth = cropWidthEnd - cropWidthStart + 1;
		int[] cropSpecs = new int[4];
		cropSpecs[0] = cropHeightStart;
		cropSpecs[1] = cropWidthStart;
		cropSpecs[2] = cropHeight;
		cropSpecs[3] = cropWidth;
		return cropSpecs;
	}

	private int[] measureExposureValues(String ImagePath, String lutPath, int[] cropSpecs) throws ExceptionLut, ExceptionCalibration, MetadataException, ImageProcessingException, IOException, ExceptionExif, InvalidKeyException, CipherException {

		// First read the LUT file and save the RGB values in lists
		BufferedReader br = null;
		List<Integer> listR = new ArrayList<Integer>();
		List<Integer> listG = new ArrayList<Integer>();
		List<Integer> listB = new ArrayList<Integer>();
		try {
			br = new BufferedReader(new FileReader(lutPath));
			String sCurrentLine;
			while ((sCurrentLine = br.readLine()) != null) {
				String[] tmp = sCurrentLine.split("\t");
				if (tmp.length > 2) {
					int redValue = Integer.parseInt(tmp[0]);
					int greenValue = Integer.parseInt(tmp[1]);
					int blueValue = Integer.parseInt(tmp[2]);
					listR.add(redValue);
					listG.add(greenValue);
					listB.add(blueValue);
				}// end if
			}// end while
			br.close();
		} catch (IOException e) {
			throw new ExceptionLut("Error reading LUT", e);
		}

		int cropHeightStart = cropSpecs[0];
		int cropWidthStart = cropSpecs[1];
		int cropHeight = cropSpecs[2];
		int cropWidth = cropSpecs[3];

		// Then read the image pixels inside the crop area and apply linearization
		// Read and autorotate image 
		Mat bgrImageNormalized = readAutoRotateMat(ImagePath);
		BufferedImage bufferedImage = matToBufferedImage(bgrImageNormalized);

		bgrImageNormalized.release();

		// Check to see if the crop is inside the image, otherwise throw an error
		if ((cropHeightStart - 1 < 0) || (cropWidthStart - 1 < 0) ||
				(cropHeightStart - 1 + cropHeight > bufferedImage.getWidth()) ||
				(cropWidthStart - 1 + cropWidth > bufferedImage.getHeight())) {
			ExceptionNumbers numberClass = new ExceptionNumbers();
			int errorCode = numberClass.ExceptionCalibrationNumber4;
			String message = errorCode + "," + "Cropping area out of bound. Make sure you align the yellow"
					+ " rectangle with the Gray card and take another image.";
			throw new ExceptionCalibration(message);
		}

		WritableRaster rasterimg = bufferedImage.getRaster();
		int[] iArray = null;
		int[] imagePixels = rasterimg.getPixels(cropHeightStart - 1, cropWidthStart - 1, cropHeight, cropWidth, iArray);
		int imagePixelsR[][] = new int[cropWidth][cropHeight];
		int imagePixelsG[][] = new int[cropWidth][cropHeight];
		int imagePixelsB[][] = new int[cropWidth][cropHeight];
		double rLin = 0; // using double so we don't go over max integer value
		double gLin = 0;
		double bLin = 0;
		for (int i = 0; i < cropWidth; i++) {
			for (int j = 0; j < cropHeight; j++) {
				imagePixelsR[i][j] = imagePixels[3 * i * cropHeight + 3 * j];
				imagePixelsG[i][j] = imagePixels[3 * i * cropHeight + 3 * j + 1];
				imagePixelsB[i][j] = imagePixels[3 * i * cropHeight + 3 * j + 2];
				rLin = rLin + listR.get(imagePixelsR[i][j]);
				gLin = gLin + listG.get(imagePixelsG[i][j]);
				bLin = bLin + listB.get(imagePixelsB[i][j]);
			}
		}
		// Finally calculate the RGB average linear values and return it
		int rLinAverage = (int) (rLin / (cropHeight * cropWidth));
		int gLinAverage = (int) (gLin / (cropHeight * cropWidth));
		int bLinAverage = (int) (bLin / (cropHeight * cropWidth));
		int[] averageRGBLinear = new int[3];
		averageRGBLinear[0] = rLinAverage;
		averageRGBLinear[1] = gLinAverage;
		averageRGBLinear[2] = bLinAverage;
		return averageRGBLinear;
	}

	public CalibrationResults calibrate(String frame1Path, String frame2Path, String frame3Path,
			String lutPath, int[] upperLimitSubj, int[] lowerLimitSubj, int[] upperLimitBKG,
			int[] lowerLimitBKG) throws ExceptionLut, ExceptionCalibration, MetadataException, ImageProcessingException, IOException, ExceptionExif, InvalidKeyException, CipherException {

		// Call the Red circle detection method
		DetectedCirclesSpecs ReturnedThreeCircleSpecs = detectThreeRedCircles(frame2Path);

		// Call the crop subject method
		int[] subjectCropSpecs = cropSubject(ReturnedThreeCircleSpecs);

		// Call the crop background method
		int[] backgroundCropSpecs = cropBackground(ReturnedThreeCircleSpecs);

		// Call the measure exposure method for subject gray card area for frame 2
		int[] subjectRGBFrame2 = measureExposureValues(frame2Path, lutPath, subjectCropSpecs);
		//		System.out.println(String.format("Frame 2 Subject RGB values: %d ; %d; %d",
		//				subjectRGBFrame2[0], subjectRGBFrame2[1], subjectRGBFrame2[2]));

		// Call the measure exposure method for background area frame 2
		int[] backgroundRGBFrame2 = measureExposureValues(frame2Path, lutPath, backgroundCropSpecs);
		//		System.out.println(String.format("Frame 2 Background RGB values: %d ; %d; %d",
		//				backgroundRGBFrame2[0], backgroundRGBFrame2[1], backgroundRGBFrame2[2]));

		// Now for Frame 1
		// Call the measure exposure method for subject gray card area for frame 1
		int[] subjectRGBFrame1 = measureExposureValues(frame1Path, lutPath, subjectCropSpecs);
		//		System.out.println(String.format("Frame 1 Subject RGB values: %d ; %d; %d", 
		//				subjectRGBFrame1[0], subjectRGBFrame1[1], subjectRGBFrame1[2]));

		// Call the measure exposure method for background area frame 1
		int[] backgroundRGBFrame1 = measureExposureValues(frame1Path, lutPath, backgroundCropSpecs);
		//		System.out.println(String.format("Frame 1 Background RGB values: %d ; %d; %d",
		//				backgroundRGBFrame1[0], backgroundRGBFrame1[1], backgroundRGBFrame1[2]));

		// Then for Frame 3
		// Call the measure exposure method for subject gray card area for frame 3
		int[] subjectRGBFrame3 = measureExposureValues(frame3Path, lutPath, subjectCropSpecs);
		//		System.out.println(String.format("Frame 3 Subject RGB values: %d ; %d; %d", 
		//				subjectRGBFrame3[0], subjectRGBFrame3[1], subjectRGBFrame3[2]));

		// Call the measure exposure method for background area frame 3
		int[] backgroundRGBFrame3 = measureExposureValues(frame3Path, lutPath, backgroundCropSpecs);
		//		System.out.println(String.format("Frame 3 Background RGB values: %d ; %d; %d", 
		//				backgroundRGBFrame3[0], backgroundRGBFrame3[1], backgroundRGBFrame3[2]));

		// Now check the exposure values, see if they are within acceptable range
		ExceptionNumbers numberClass = new ExceptionNumbers();

		// First Frame 1
		if (backgroundRGBFrame1[0] < upperLimitBKG[0] && backgroundRGBFrame1[1] < upperLimitBKG[1] &&
				backgroundRGBFrame1[2] < upperLimitBKG[2]
						&& backgroundRGBFrame1[0] > lowerLimitBKG[0] && backgroundRGBFrame1[1] > lowerLimitBKG[1]
								&& backgroundRGBFrame1[2] > lowerLimitBKG[2]) {
		} else if (backgroundRGBFrame1[0] < lowerLimitBKG[0] || backgroundRGBFrame1[1] < lowerLimitBKG[1]
				|| backgroundRGBFrame1[2] < lowerLimitBKG[2]) {
			int errorCode = numberClass.ExceptionCalibrationNumber5;
			String message = errorCode + "," + String.format("%d,%d,%d", 
					backgroundRGBFrame1[0],backgroundRGBFrame1[1],backgroundRGBFrame1[2]);
			throw new ExceptionCalibration(message);
		} else if (backgroundRGBFrame1[0] > upperLimitBKG[0] || backgroundRGBFrame1[1] > upperLimitBKG[1]
				|| backgroundRGBFrame1[2] > upperLimitBKG[2]) {
			int errorCode = numberClass.ExceptionCalibrationNumber6;
			String message =  errorCode + "," + String.format("%d,%d,%d", 
					backgroundRGBFrame1[0],backgroundRGBFrame1[1],backgroundRGBFrame1[2]);
			throw new ExceptionCalibration(message);
		}

		// Then Frame 2
		if (subjectRGBFrame2[0] < upperLimitSubj[0] && subjectRGBFrame2[1] < upperLimitSubj[1] &&
				subjectRGBFrame2[2] < upperLimitSubj[2]
						&& subjectRGBFrame2[0] > lowerLimitSubj[0] && subjectRGBFrame2[1] > lowerLimitSubj[1] &&
						subjectRGBFrame2[2] > lowerLimitSubj[2]) {
		} else if(subjectRGBFrame2[0] < lowerLimitSubj[0] || subjectRGBFrame2[1] < lowerLimitSubj[1] ||
				subjectRGBFrame2[2] < lowerLimitSubj[2]){
			int errorCode = numberClass.ExceptionCalibrationNumber7;
			String message = errorCode + "," + String.format("%d,%d,%d", 
					subjectRGBFrame2[0],subjectRGBFrame2[1],subjectRGBFrame2[2]);
			throw new ExceptionCalibration(message);
		} else if(subjectRGBFrame2[0] > upperLimitSubj[0] || subjectRGBFrame2[1] > upperLimitSubj[1] ||
				subjectRGBFrame2[2] > upperLimitSubj[2]){
			int errorCode = numberClass.ExceptionCalibrationNumber8;
			String message = errorCode + "," + String.format("%d,%d,%d", 
					subjectRGBFrame2[0],subjectRGBFrame2[1],subjectRGBFrame2[2]);
			throw new ExceptionCalibration(message);
		}

		// Finally Frame 3
		if (backgroundRGBFrame3[0] < upperLimitBKG[0] && backgroundRGBFrame3[1] < upperLimitBKG[1] &&
				backgroundRGBFrame3[2] < upperLimitBKG[2]
						&& backgroundRGBFrame3[0] > lowerLimitBKG[0] && backgroundRGBFrame3[1] > lowerLimitBKG[1] &&
						backgroundRGBFrame3[2] > lowerLimitBKG[2]) {
		} else if(backgroundRGBFrame3[0] < lowerLimitBKG[0] || backgroundRGBFrame3[1] < lowerLimitBKG[1] ||
				backgroundRGBFrame3[2] < lowerLimitBKG[2]){
			int errorCode = numberClass.ExceptionCalibrationNumber9;
			String message = errorCode + "," + String.format("%d,%d,%d", 
					backgroundRGBFrame3[0],backgroundRGBFrame3[1],backgroundRGBFrame3[2]);
			throw new ExceptionCalibration(message);
		} else if(backgroundRGBFrame3[0] > upperLimitBKG[0] || backgroundRGBFrame3[1] > upperLimitBKG[1] ||
				backgroundRGBFrame3[2] > upperLimitBKG[2]){
			int errorCode = numberClass.ExceptionCalibrationNumber10;
			String message = errorCode + "," + String.format("%d,%d,%d", 
					backgroundRGBFrame3[0],backgroundRGBFrame3[1],backgroundRGBFrame3[2]);
			throw new ExceptionCalibration(message);
		}

		System.out.println(String.format("Calibration finished."));

		CalibrationResults results = new CalibrationResults();
		results.frame1BackgroundRGB = backgroundRGBFrame1;
		results.frame2BackgroundRGB = backgroundRGBFrame2;
		results.frame3BackgroundRGB = backgroundRGBFrame3;
		results.frame1SubjectRGB = subjectRGBFrame1;
		results.frame2SubjectRGB = subjectRGBFrame2;
		results.frame3SubjectRGB = subjectRGBFrame3;
		return results;
	}

	public static Mat readAutoRotateMat(String imagePath) throws IOException, MetadataException, ImageProcessingException, ExceptionExif, InvalidKeyException, CipherException {

		String inPath = imagePath;
		String outPath = "";
		String withoutExt = FilenameUtils.removeExtension(imagePath);
		if (imagePath.toLowerCase().endsWith(".crdat2")){
			outPath = withoutExt;			
		}else{
			outPath = inPath;			
		}
		EncryptionImageLearn.decryptFileIfNecessary(inPath,outPath);

		Mat imageMat = Highgui.imread(outPath);
		File input = new File(outPath);
		// read meta data and find orientation
		ImageInformation imginfo = readImageInformation(input);
		int cameraOrientation = imginfo.orientation;
		//		System.out.println(String.format("Image Orientation is: %d", cameraOrientation));
		switch (cameraOrientation) {
		case 1:
			// Normal portrait, continue
			//			System.out.println("Normal portrait, continue");
			break;
		case 8:
			// Rotate clockwise 270 degrees
			Core.transpose(imageMat.t(), imageMat);
			Core.flip(imageMat.t(), imageMat, 0);
			//			System.out.println("Rotated clockwise 270 degrees");
			break;
		case 3:
			// Rotate clockwise 180 degrees
			Core.flip(imageMat.t(), imageMat, -1);
			//			System.out.println("Rotated clockwise 180 degrees");
			break;
		case 6:
			// Rotate clockwise 90 degrees
			Core.transpose(imageMat.t(), imageMat);
			Core.flip(imageMat.t(), imageMat, 1);
			//			System.out.println("Rotated clockwise 90 degrees");
			break;
		default:
			break;
		}
		// Delete decrypted file
		if (imagePath.toLowerCase().endsWith(".crdat2")){
			outPath = withoutExt;
			File file = new File(outPath);
			file.delete();
		}
		return imageMat;
	}

	public static short[] getPixels(int imageWidth, int imageHeight, WritableRaster rasterimg) {
		int[] intArray = rasterimg.getPixels(0, 0, imageWidth, imageHeight, (int[]) null);
		short[] shortArray = new short[intArray.length];
		for (int i = 0; i < intArray.length; i++) {
			shortArray[i] = (short) intArray[i];
		}
		return shortArray;
	}

	public static ImageInformation readImageInformation(File imageFile)  throws IOException,
	MetadataException, ImageProcessingException, ExceptionExif {
		Metadata metadata = ImageMetadataReader.readMetadata(imageFile);
		Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
		JpegDirectory jpegDirectory = metadata.getFirstDirectoryOfType(JpegDirectory.class);

		if (directory==null){
			throw new ExceptionExif("Can not extarct exif metadata information.");
		}

		int orientation = 1;
		try {
			orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
		} catch (MetadataException me) {
			throw new ExceptionExif("Can not extarct exif metadata information.");
		}
		int width = jpegDirectory.getImageWidth();
		int height = jpegDirectory.getImageHeight();

		return new ImageInformation(orientation, width, height);
	}

	/**  
	 * Converts/writes a Mat into a BufferedImage.  
	 *  
	 * @param matrix Mat of type CV_8UC3 or CV_8UC1  
	 * @return BufferedImage of type TYPE_3BYTE_BGR or TYPE_BYTE_GRAY  
	 */  
	public static BufferedImage matToBufferedImage(Mat matrix)
	{
		int cols = matrix.cols();  
		int rows = matrix.rows();  
		int elemSize = (int)matrix.elemSize();  
		byte[] data = new byte[cols * rows * elemSize];  
		int type;  
		matrix.get(0, 0, data);

		switch (matrix.channels()) {  
		case 1:  
			type = BufferedImage.TYPE_BYTE_GRAY;  
			break;  
		case 3:  
			type = BufferedImage.TYPE_3BYTE_BGR;  
			// bgr to rgb  
			byte b;  
			for(int i = 0; i < data.length; i = i + 3) {  
				b = data[i];  
				data[i] = data[i+2];  
				data[i+2] = b;  
			}  
			break;  
		default:  
			return null;  
		}  

		BufferedImage bimg = new BufferedImage(cols, rows, type);
		bimg.getRaster().setDataElements(0, 0, cols, rows, data);
		return bimg;  
	}   

	public static void main(String[] args) throws IOException, ExceptionLut, ExceptionCalibration, MetadataException, ImageProcessingException, ExceptionNativeLoad, ExceptionExif, InvalidKeyException, CipherException {

		System.out.println(String.format("Starting main method..."));

		// Initialize first
		Initialize init = new Initialize();
		String rootPath = "C:/Program Files/Lifetouch/ImageLearn";
		init.loadNativeLibraries(rootPath);

		// Input arguments
		String frame1Path = "E:/202003500010000158A.jpg.crdat2";
		String frame2Path = "E:/202003500010000158B.jpg.crdat2";
		String frame3Path = "E:/202003500010000158C.jpg.crdat2";
		String lutPath = "E:/LUTS/8to12lin08.lut";
		int[] subjectUpperLimit = {1267, 1267, 1267};// {1816, 1816, 1816};
		int[] subjectLowerLimit = {377, 377, 377};//{642, 642, 642};
		int[] backgroundUpperLimit = {2664, 2664, 2664};
		int[] backgroundLowerLimit = {666, 666, 666};

		new MirrorlessCalibration().calibrate(frame1Path, frame2Path, frame3Path, lutPath,
				subjectUpperLimit, subjectLowerLimit, backgroundUpperLimit, backgroundLowerLimit);
	}// end of main
}// end of calib class



