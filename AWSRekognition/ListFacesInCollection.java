//Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
//PDX-License-Identifier: MIT-0 (For details, see https://github.com/awsdocs/amazon-rekognition-developer-guide/blob/master/LICENSE-SAMPLECODE.)

import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.Face;
import com.amazonaws.services.rekognition.model.ListFacesRequest;
import com.amazonaws.services.rekognition.model.ListFacesResult;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;



public class ListFacesInCollection {
	public static final String collectionId = "DemoCollection";//MyCollectionTest//DemoCollection

	public static void main(String[] args) throws Exception {

		AmazonRekognition rekognitionClient = AmazonRekognitionClientBuilder.defaultClient();

		ObjectMapper objectMapper = new ObjectMapper();

		ListFacesResult listFacesResult = null;
		System.out.println("Faces in collection " + collectionId);

		String paginationToken = null;
		do {
			if (listFacesResult != null) {
				paginationToken = listFacesResult.getNextToken();
			}

			ListFacesRequest listFacesRequest = new ListFacesRequest()
					.withCollectionId(collectionId)
//					.withMaxResults(1)
					.withNextToken(paginationToken);

			listFacesResult =  rekognitionClient.listFaces(listFacesRequest);
			List < Face > faces = listFacesResult.getFaces();
			int totalnumFaces = faces.size();
			System.out.println("totalnumFaces:" + totalnumFaces);
			int i = 0;
			for (Face face: faces) {
				i = i +1;
				System.out.format("Printing face number %d out of %d " , i , totalnumFaces );
				System.out.println(objectMapper.writerWithDefaultPrettyPrinter()
						.writeValueAsString(face));
			}
			totalnumFaces = faces.size();
			System.out.println("totalnumFaces:" + totalnumFaces);
		} while (listFacesResult != null && listFacesResult.getNextToken() != null);
		System.out.println("ALL PROCESS FINISHED!");
	}

}
