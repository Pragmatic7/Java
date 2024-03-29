//Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
//PDX-License-Identifier: MIT-0 (For details, see https://github.com/awsdocs/amazon-rekognition-developer-guide/blob/master/LICENSE-SAMPLECODE.)

import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.FaceMatch;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.S3Object;
import com.amazonaws.services.rekognition.model.SearchFacesByImageRequest;
import com.amazonaws.services.rekognition.model.SearchFacesByImageResult;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;


public class SearchFaceMatchingImageCollection {
    public static final String collectionId = "MyCollectionTest";
    public static final String bucket = "temp2-aws-rek";
    public static final String photo = "GroupPhotos_202210305680001749.JPG";//"Individuals_Todd J.JPG";//201910981300000022C.JPG	//201910981300000013C
      
    public static void main(String[] args) throws Exception {

       AmazonRekognition rekognitionClient = AmazonRekognitionClientBuilder.defaultClient();
        
      ObjectMapper objectMapper = new ObjectMapper();
      
       // Get an image object from S3 bucket.
      Image image=new Image()
              .withS3Object(new S3Object()
                      .withBucket(bucket)
                      .withName(photo));
      
      // Search collection for faces similar to the largest face in the image.
      SearchFacesByImageRequest searchFacesByImageRequest = new SearchFacesByImageRequest()
              .withCollectionId(collectionId)
              .withImage(image)
              .withFaceMatchThreshold(99F);
//              .withMaxFaces(2); // this will only retrun up to 2 matches!
           
       SearchFacesByImageResult searchFacesByImageResult = 
               rekognitionClient.searchFacesByImage(searchFacesByImageRequest);

       System.out.println("Faces matching largest face in image from" + photo);
      List < FaceMatch > faceImageMatches = searchFacesByImageResult.getFaceMatches();
      if (faceImageMatches == null || faceImageMatches.isEmpty()) {
    	  System.out.println("No matching faces found!");
      }else{
          for (FaceMatch face: faceImageMatches) {
              System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(face));
             System.out.println();
          }
      }
   }
}


      