
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import javax.imageio.ImageIO;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.BoundingBox;
import com.amazonaws.services.rekognition.model.CreateCollectionRequest;
import com.amazonaws.services.rekognition.model.CreateCollectionResult;
import com.amazonaws.services.rekognition.model.DeleteCollectionRequest;
import com.amazonaws.services.rekognition.model.DeleteCollectionResult;
import com.amazonaws.services.rekognition.model.Face;
import com.amazonaws.services.rekognition.model.FaceMatch;
import com.amazonaws.services.rekognition.model.FaceRecord;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.IndexFacesRequest;
import com.amazonaws.services.rekognition.model.IndexFacesResult;
import com.amazonaws.services.rekognition.model.QualityFilter;
import com.amazonaws.services.rekognition.model.S3Object;
import com.amazonaws.services.rekognition.model.SearchFacesByImageRequest;
import com.amazonaws.services.rekognition.model.SearchFacesByImageResult;
import com.amazonaws.services.rekognition.model.UnindexedFace;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DemoVisualizeGroupBatchProcessing {
	public static final String bucket_name = "tempyearbook1";
	public static final String collection_name = "DemoVisualizeCollectionBatch";
	public static final String localDirecotryPath = "GroupPhotos/";//"GroupPhotosRenamed/";
	public static final String textFile = localDirecotryPath + "Results.txt";

	public static void main(String[] args) throws Exception {

		AmazonRekognition rekognitionClient = AmazonRekognitionClientBuilder.defaultClient();

		System.out.format("Objects in S3 bucket %s:\n", bucket_name);
		final AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1).build();
		ListObjectsV2Result result = s3.listObjectsV2(bucket_name);
		List<S3ObjectSummary> objects = result.getObjectSummaries();

		// begin Batch processing
		// For every group photo add faces then visualzie and write into 1 single result text file

		// first create the result text file and send it to each call
		File resultsFile = new File(textFile);
		try {
			Files.deleteIfExists(resultsFile.toPath());
		} catch (IOException e) {
			System.err.println("Caught IOException: " + e.getMessage());
		}
		Path results = Paths.get(textFile);

		// Add the header to text file
		String header1 = padRight("Group Photo", 40);
		String header2 = padRight("Individual Name", 30);
		String header3 = padRight("Bounding Box Left", 30);
		String header4 = padRight("Bounding Box Top", 30);
		String header = header1 + "\t" + header2 + "\t" + header3 + "\t" + header4;
		appendStringToFile(header, results);

		// Now start looping through group photos
		double timeBatchRun = 0;
		File  directory = new File(localDirecotryPath);
		for (File file : directory.listFiles())
		{
			if(file.getName().toLowerCase().endsWith(".jpg"))
			{
				// STEP 1
				deleteCollection(collection_name, rekognitionClient);

				// STEP 2
				createCollection(collection_name, rekognitionClient);
				
				String filename = file.getName();
				System.out.println(filename);
				long startTime = System.nanoTime();

				// STEP 3
				addGroupFaces(rekognitionClient, filename);

				// STEP 4
				searchFacesIndividualsAndVisualize(rekognitionClient, objects, filename, results);

				long stopTime = System.nanoTime();
				double timeOneRun = (stopTime - startTime) / 1e9;
				timeBatchRun += timeOneRun;
			}// if
		}// for
		
		// Now read back the text file and visualize on group photos 1 by 1
		Scanner scan = new Scanner(new File(textFile));
		int[] coordinates = new int[2];
		scan.nextLine();
	    while(scan.hasNext()){
	        String curLine = scan.nextLine();
	        String[] splitted = curLine.split("\t");
	        String inGroupPhoto = splitted[0];
	        inGroupPhoto = inGroupPhoto.replaceAll("\\s+", "");
	        String outGroupPhoto = localDirecotryPath + inGroupPhoto;
	        String subjectName = splitted[1];
	        subjectName = subjectName.replaceAll("\\s+", "");

	        String coordinatesLeft = splitted[2];
	        coordinatesLeft = coordinatesLeft.replaceAll("\\s+", "");
	        coordinates[0] = Integer.parseInt(coordinatesLeft);

	        String coordinatesTop = splitted[3];
	        coordinatesTop = coordinatesTop.replaceAll("\\s+", "");
	        coordinates[1] = Integer.parseInt(coordinatesTop);

	        visualizeOnGroupPhoto(outGroupPhoto, outGroupPhoto, coordinates, subjectName);

	    }
	    scan.close();
	    
//		visualizeOnGroupPhoto(inGroupPhoto, outGroupPhoto, coordinates, subjectName);
		System.out.println("Finished visualizeOnGroupPhoto.");

		System.out.println("\nBatch prcoessing group photos took " + timeBatchRun + " Seconds to run");

		System.out.format("\nALL PROCESSES FINISHED!");

	}// main 

	public static void deleteCollection(String collectionID, AmazonRekognition rekognitionClient) {
		System.out.println("Deleting collections");

		DeleteCollectionRequest request = new DeleteCollectionRequest()
				.withCollectionId(collectionID);
		DeleteCollectionResult deleteCollectionResult = rekognitionClient.deleteCollection(request);        

		System.out.println(collectionID + ": " + deleteCollectionResult.getStatusCode()
		.toString());
	}

	public static void searchFacesIndividualsAndVisualize(AmazonRekognition rekognitionClient, List<S3ObjectSummary> objects, String inGroupPhoto, Path results) throws IOException {

		String outGroupPhoto = localDirecotryPath + inGroupPhoto;

		for (S3ObjectSummary os : objects) {
			String filePath = os.getKey();
			String[] parsed = filePath.split("_");
			String indicator = parsed[0];
			String filename = "";
			if(parsed.length > 1){
				filename = parsed[1];
			}
			if (indicator.equals("Individuals")){
				if(!filename.isEmpty()){
					System.out.println("Found a photo in Individuals: " + filename);
					try {
						searchFaceInCollection(rekognitionClient, filePath, inGroupPhoto, outGroupPhoto, results);
					} catch (JsonProcessingException e) {
						e.printStackTrace();
					}
				}
			}
		}// for objects
	}// searchFacesIndividualsAndVisualize

	public static void searchFaceInCollection(AmazonRekognition rekognitionClient, String individualPhoto, 
			String inGroupPhoto, String outGroupPhoto, Path results) throws IOException {

		ObjectMapper objectMapper = new ObjectMapper();

		// Get an image object from S3 bucket.
		Image image = new Image()
				.withS3Object(new S3Object()
						.withBucket(bucket_name)
						.withName(individualPhoto));

		// Search collection for faces similar to the largest face in the image.
		SearchFacesByImageRequest searchFacesByImageRequest = new SearchFacesByImageRequest()
				.withCollectionId(collection_name)
				.withImage(image)
				.withFaceMatchThreshold(99F);
		//				.withMaxFaces(2); // this will only retrun up to 2 matches!

		SearchFacesByImageResult searchFacesByImageResult = 
				rekognitionClient.searchFacesByImage(searchFacesByImageRequest);

		System.out.println("Faces matching largest face in image: " + individualPhoto);
		List < FaceMatch > faceImageMatches = searchFacesByImageResult.getFaceMatches();
		if(!faceImageMatches.isEmpty()) {
			for (FaceMatch face: faceImageMatches) {
				System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(face));
				Face foundFace = face.getFace();
				BoundingBox bb = foundFace.getBoundingBox();
				String imageID = individualPhoto;//foundFace.getExternalImageId();

				String[] parsed = imageID.split("_");
				String nameJPG = parsed[1];
				String[] nameJPGparsed = nameJPG.split("\\.");
				String subjectName = nameJPGparsed[0];

				//				Float top = bb.getTop();
				//				Float left = bb.getLeft();
				BufferedImage buffImage = ImageIO.read(new File(outGroupPhoto));
				int height = buffImage.getHeight();
				int width = buffImage.getWidth();
				int left = (int) (width * bb.getLeft());
				int top = (int) (height * bb.getTop());
				int[] coordinates = new int[2];
				coordinates[0] = left;
				coordinates[1] = top;

				// Write to text file
				String header1 = padRight(inGroupPhoto, 40);
				String header2 = padRight(subjectName, 30);
				String header3 = padRight(String.valueOf(coordinates[0]), 30);
				String header4 = padRight(String.valueOf(coordinates[1]), 30);

				String textToWrite = header1 + "\t" + header2 + "\t" + header3 + "\t" + header4;
				// Now write to final results text file
				appendStringToFile(textToWrite, results);

//				visualizeOnGroupPhoto(inGroupPhoto, outGroupPhoto, coordinates, subjectName);
//				System.out.println("Finished visualizeOnGroupPhoto.");

			}// for
		}// if

		//		visualizeOnGroupPhoto(photo);


	}// searchFaceInCollection

	public static void visualizeOnGroupPhoto(String inputPhoto, String outputPhoto, int[] location, String text) throws IOException {
		//read the image
		BufferedImage buffImage = ImageIO.read(new File(inputPhoto));

		//get the Graphics object
		Graphics g = buffImage.getGraphics();
		//set font
		g.setFont(g.getFont().deriveFont(100f));
		//display the text at the coordinates(x=50, y=150)
		g.drawString(text, location[0], location[1]);
		g.dispose();
		//write the image
		ImageIO.write(buffImage, "jpg", new File(outputPhoto));
	}

	public static void createCollection(String nameOfCollection, AmazonRekognition rekognitionClient) {
		String collectionId = nameOfCollection;
		System.out.println("Creating collection: " + collectionId );

		CreateCollectionRequest request = new CreateCollectionRequest().withCollectionId(collectionId);

		CreateCollectionResult createCollectionResult = rekognitionClient.createCollection(request); 
		System.out.println("CollectionArn : " +createCollectionResult.getCollectionArn());
		System.out.println("Status code : " +createCollectionResult.getStatusCode().toString());
	} //createCollection

	public static void addGroupFaces(AmazonRekognition rekognitionClient, String photo) {
		Image image = new Image()
				//        		.withBytes(bytes);
				.withS3Object(new S3Object()
						.withBucket(bucket_name)
						.withName(photo));

		IndexFacesRequest indexFacesRequest = new IndexFacesRequest()
				.withImage(image)
				.withQualityFilter(QualityFilter.AUTO)
				//                .withMaxFaces(1)
				.withCollectionId(collection_name)
				.withExternalImageId(photo)
				.withDetectionAttributes("DEFAULT");

		IndexFacesResult indexFacesResult = rekognitionClient.indexFaces(indexFacesRequest);

		System.out.println("Results for " + photo);
		System.out.println("Faces indexed:");
		List<FaceRecord> faceRecords = indexFacesResult.getFaceRecords();
		for (FaceRecord faceRecord : faceRecords) {
			System.out.println("  Face ID: " + faceRecord.getFace().getFaceId());
			System.out.println("  Location:" + faceRecord.getFaceDetail().getBoundingBox().toString());
		}

		List<UnindexedFace> unindexedFaces = indexFacesResult.getUnindexedFaces();
		System.out.println("Faces not indexed:");
		for (UnindexedFace unindexedFace : unindexedFaces) {
			System.out.println("  Location:" + unindexedFace.getFaceDetail().getBoundingBox().toString());
			System.out.println("  Reasons:");
			for (String reason : unindexedFace.getReasons()) {
				System.out.println("   " + reason);
			}
		}
	}// addGroupFaces

	public static String padRight(String s, int n) {
		return String.format("%1$-" + n + "s", s);  
	}

	public static String padLeft(String s, int n) {
		return String.format("%1$" + n + "s", s);  
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


}// class demo


