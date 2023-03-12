//Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
//PDX-License-Identifier: MIT-0 (For details, see https://github.com/awsdocs/amazon-rekognition-developer-guide/blob/master/LICENSE-SAMPLECODE.)


import java.util.List;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.CreateCollectionRequest;
import com.amazonaws.services.rekognition.model.CreateCollectionResult;
import com.amazonaws.services.rekognition.model.DeleteCollectionRequest;
import com.amazonaws.services.rekognition.model.DeleteCollectionResult;
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


public class DemoSearchIndividual {
	public static final String bucket_name = "temp2-aws-rek";
	public static final String collection_name = "DemoCollection";

	public static void main(String[] args) throws Exception {

		AmazonRekognition rekognitionClient = AmazonRekognitionClientBuilder.defaultClient();

		// STEP 1
		deleteCollection(collection_name, rekognitionClient);
		createCollection(collection_name, rekognitionClient);

		System.out.format("Objects in S3 bucket %s:\n", bucket_name);
		final AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1).build();
		ListObjectsV2Result result = s3.listObjectsV2(bucket_name);
		List<S3ObjectSummary> objects = result.getObjectSummaries();

		// STEP 2
		addFacesGroupPhotos(rekognitionClient, objects);

		// STEP 3
		searchFacesIndividuals(rekognitionClient, objects);

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

	public static void searchFacesIndividuals(AmazonRekognition rekognitionClient, List<S3ObjectSummary> objects) {

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
						searchFaceInCollection(rekognitionClient, filePath);
					} catch (JsonProcessingException e) {
						e.printStackTrace();
					}
				}
			}
		}// for objects
	}// searchFacesIndividuals

	public static void searchFaceInCollection(AmazonRekognition rekognitionClient, String photo) throws JsonProcessingException {

		ObjectMapper objectMapper = new ObjectMapper();

		// Get an image object from S3 bucket.
		Image image = new Image()
				.withS3Object(new S3Object()
						.withBucket(bucket_name)
						.withName(photo));

		// Search collection for faces similar to the largest face in the image.
		SearchFacesByImageRequest searchFacesByImageRequest = new SearchFacesByImageRequest()
				.withCollectionId(collection_name)
				.withImage(image)
				.withFaceMatchThreshold(70F);
//				.withMaxFaces(2); // this will only retrun up to 2 matches!

		SearchFacesByImageResult searchFacesByImageResult = 
				rekognitionClient.searchFacesByImage(searchFacesByImageRequest);

		System.out.println("Faces matching largest face in image from: " + photo);
		List < FaceMatch > faceImageMatches = searchFacesByImageResult.getFaceMatches();
		for (FaceMatch face: faceImageMatches) {
			System.out.println(objectMapper.writerWithDefaultPrettyPrinter()
					.writeValueAsString(face));
			System.out.println();
		}// for
	}// searchFaceInCollection

	public static void addFacesGroupPhotos(AmazonRekognition rekognitionClient, List<S3ObjectSummary> objects) {
		for (S3ObjectSummary os : objects) {

			String filePath = os.getKey();
			String[] parsed = filePath.split("_");
			String indicator = parsed[0];
			String filename = "";
			if(parsed.length > 1){
				filename = parsed[1];
			}
			if (indicator.equals("GroupPhotos")){
				System.out.println("Found a photo in GroupPhotos: " + filename);
				addGroupFaces(rekognitionClient, filePath, bucket_name, collection_name);
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

	public static void addGroupFaces(AmazonRekognition rekognitionClient, String photo, String bucket, String collectionId) {
		Image image = new Image()
				//        		.withBytes(bytes);
				.withS3Object(new S3Object()
						.withBucket(bucket)
						.withName(photo));

		IndexFacesRequest indexFacesRequest = new IndexFacesRequest()
				.withImage(image)
				.withQualityFilter(QualityFilter.AUTO)
				//                .withMaxFaces(1)
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


}// class demo


