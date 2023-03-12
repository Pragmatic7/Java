
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

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

public class DemoYear2YearText {
	public static final String bucket_name = "bb";
	public static final String collection_name = "cc";
	public static final String textFile = "Resultsy2y.txt";
	public static final float similarityThreshold = 99;

	public static void main(String[] args) throws Exception {
		
		long startTime = System.nanoTime();

		AmazonRekognition rekognitionClient = AmazonRekognitionClientBuilder.defaultClient();

		// STEP 1
		deleteCollection(collection_name, rekognitionClient);
		createCollection(collection_name, rekognitionClient);

		System.out.format("Objects in S3 bucket %s:\n", bucket_name);
		final AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1).build();
		ListObjectsV2Result result = s3.listObjectsV2(bucket_name);
		List<S3ObjectSummary> objects = result.getObjectSummaries();

		// STEP 2
		addFacesFallPhotos(rekognitionClient, objects);

		// STEP 3
		// first create the result text file and send it to each call
		File resultsFile = new File(textFile);
		try {
			Files.deleteIfExists(resultsFile.toPath());
		} catch (IOException e) {
			System.err.println("Caught IOException: " + e.getMessage());
		}
		Path results = Paths.get(textFile);

		// Add the header to text file
		String header1 = padRight("Spring Photo", 40);
		String header2 = padRight("Fall Photo", 30);
		String header3 = padRight("Bounding Box Left", 30);
		String header4 = padRight("Bounding Box Top", 30);
		String header5 = padRight("Similarity", 30);
		String header = header1 + "\t" + header2 + "\t" + header3 + "\t" + header4 + "\t" + header5;
		appendStringToFile(header, results);

		searchFacesSpringPhotos(rekognitionClient, objects, results);

		long stopTime = System.nanoTime();
		double timeBatchRun = (stopTime - startTime) / 1e9;
		System.out.println("\nBatch prcoessing group photos took " + timeBatchRun + " Seconds to run");
		System.out.format("ALL PROCESSES FINISHED!");

	}// main 

	public static void deleteCollection(String collectionID, AmazonRekognition rekognitionClient) {
		System.out.println("Deleting collections");

		DeleteCollectionRequest request = new DeleteCollectionRequest()
				.withCollectionId(collectionID);
		DeleteCollectionResult deleteCollectionResult = rekognitionClient.deleteCollection(request);        

		System.out.println(collectionID + ": " + deleteCollectionResult.getStatusCode()
		.toString());
	}

	public static void searchFacesSpringPhotos(AmazonRekognition rekognitionClient, List<S3ObjectSummary> objects, Path results) throws IOException {

		for (S3ObjectSummary os : objects) {
			String filePath = os.getKey();
			String[] parsed = filePath.split("_");
			String indicator = parsed[0];
			String filename = "";
			if(parsed.length > 1){
				filename = parsed[1];
			}
			if (indicator.equals("Spring")){
				if(!filename.isEmpty()){
					System.out.println("Found a photo in Spring: " + filename);
					try {
						searchFaceInCollection(rekognitionClient, filePath, results);
					} catch (JsonProcessingException e) {
						e.printStackTrace();
					}
				}
			}
		}// for objects
	}// searchFacesIndividuals

	public static void searchFaceInCollection(AmazonRekognition rekognitionClient, String individualPhoto , Path results) throws IOException {

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
				.withFaceMatchThreshold(70F);
		//				.withMaxFaces(2); // this will only retrun up to 2 matches!

		SearchFacesByImageResult searchFacesByImageResult = 
				rekognitionClient.searchFacesByImage(searchFacesByImageRequest);

		System.out.println("Faces matching largest face in image from: " + individualPhoto);
		List < FaceMatch > faceImageMatches = searchFacesByImageResult.getFaceMatches();
		Float[] coordinates = new Float[2];

		if(!faceImageMatches.isEmpty()) {
			for (FaceMatch face: faceImageMatches) {
				Float similarity = face.getSimilarity();
				if(similarity > similarityThreshold){
					System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(face));
					Face foundFace = face.getFace();
					BoundingBox bb = foundFace.getBoundingBox();
					String imageID = individualPhoto;//foundFace.getExternalImageId();
//					String nameJPG = imageID;
//					String[] nameJPGparsed = nameJPG.split("\\.");
//					String subjectName = nameJPGparsed[0];
					coordinates[0] = bb.getLeft();
					coordinates[1] = bb.getTop();
					String groupPhoto = foundFace.getExternalImageId();
					
					// Write to text file
					String header1 = padRight(imageID, 30);
					String header2 = padRight(groupPhoto, 40);
					String header3 = padRight(String.valueOf(coordinates[0]), 30);
					String header4 = padRight(String.valueOf(coordinates[1]), 30);
					String header5 = padRight(String.valueOf(similarity), 30);

					String textToWrite = header1 + "\t" + header2 + "\t" + header3 + "\t" + header4 + "\t" + header5;
					// Now write to final results text file
					appendStringToFile(textToWrite, results);
				}//if
			}// for
		}// if

	}// searchFaceInCollection

	public static void addFacesFallPhotos(AmazonRekognition rekognitionClient, List<S3ObjectSummary> objects) {
		for (S3ObjectSummary os : objects) {

			String filePath = os.getKey();
			String[] parsed = filePath.split("_");
			String indicator = parsed[0];
			String filename = "";
			if(parsed.length > 1){
				filename = parsed[1];
			}
			if (indicator.equals("Fall")){
				System.out.println("Found a photo in Fall photos: " + filename);
				addFallFaces(rekognitionClient, filePath, bucket_name, collection_name);
			}
		}// for objects
	}// addFacesGroupPhotos

	public static void createCollection(String nameOfCollection, AmazonRekognition rekognitionClient) {
		String collectionId = nameOfCollection;
		System.out.println("Creating collection: " + collectionId );

		CreateCollectionRequest request = new CreateCollectionRequest().withCollectionId(collectionId);

		CreateCollectionResult createCollectionResult = rekognitionClient.createCollection(request); 
		System.out.println("CollectionArn : " +createCollectionResult.getCollectionArn());
		System.out.println("Status code : " +createCollectionResult.getStatusCode().toString());
	} //createCollection

	public static void addFallFaces(AmazonRekognition rekognitionClient, String photo, String bucket, String collectionId) {
		Image image = new Image()
				//        		.withBytes(bytes);
				.withS3Object(new S3Object()
						.withBucket(bucket)
						.withName(photo));

		IndexFacesRequest indexFacesRequest = new IndexFacesRequest()
				.withImage(image)
				.withQualityFilter(QualityFilter.AUTO)
				.withMaxFaces(1)
				.withCollectionId(collectionId)
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


