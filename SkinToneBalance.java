import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import com.lifetouch.lti.color.FrameByFrameLevelModf;
import com.lifetouch.lti.vega.utils.ImageUtils;
import f4s.FaceFeatureDetector.FeatureIndex;
import f4s.FaceFeatureDetector.Finder;
import org.w3c.dom.Element;


public class SkinToneBalance {

	public static int[] FindFaceFeats(String ImagePath)
    {
    	int[] xy= new int [4];
        Finder dt = new Finder();
        String s_lm;
        s_lm = dt.DetectLandmarks(ImagePath, -1);
        int[] ilms = dt.ParseMPEG4Landmarks(s_lm);
        int faceCount = Finder.GetFaceCount(ilms );
        System.out.print( "Face count: "); System.out.println( faceCount  );
        if (faceCount==0){
        	System.out.print( "No face found! Selecting a default frame by frame table.");
        	xy[0]=0;xy[1]=0;xy[2]=0;xy[3]=0;
        	}else{
        		xy[0]= Finder.GetFeatureForFace(ilms, FeatureIndex.EyeLeftCentreX,1);
                xy[1]= Finder.GetFeatureForFace(ilms, FeatureIndex.EyeLeftCentreY,1);
                xy[2]= Finder.GetFeatureForFace(ilms, FeatureIndex.EyeRightCentreX,1);
                xy[3]= Finder.GetFeatureForFace(ilms, FeatureIndex.EyeRightCentreY,1);
        	}
        dt=null; ilms=null;
        return xy;
    }

    public static int[] GetDiffuseHighlight(String ImagePath, int[] xy, String LUTPath) throws IOException{
		
    	int i=0; int j=0;
    	
    	BufferedImage bufferedImage = null;
    	try {
    		bufferedImage = ImageIO.read(new File(ImagePath));
    	} catch (IOException e) {
    	}
    	
		//RenderedImage image = ImageUtils.loadImageFile(ImagePath);
		//BufferedImage bufferedImage = IppImageUtils.getBufferedImageForRenderedImage(image);
		WritableRaster rasterimg = bufferedImage.getRaster();
		int imgheight = rasterimg.getHeight();
		int imgwidth = rasterimg.getWidth();
		int[] iArray = null;
		int[] ImgPixels = rasterimg.getPixels(0, 0, imgwidth, imgheight, iArray);
		int ImgPixelsR[][] = new int[imgheight][imgwidth]; 
		int ImgPixelsG[][] = new int[imgheight][imgwidth]; 
		int ImgPixelsB[][] = new int[imgheight][imgwidth];
		for (i = 0; i < imgheight; i++) {
			for (j = 0; j < imgwidth; j++) {
				ImgPixelsR[i][j] = ImgPixels[3*i*imgwidth + 3*j];
				ImgPixelsG[i][j] = ImgPixels[3*i*imgwidth + 3*j+1];
				ImgPixelsB[i][j] = ImgPixels[3*i*imgwidth + 3*j+2];
			}
		}
    	BufferedReader br = null;
    	br = new BufferedReader(new FileReader(LUTPath));
		String sCurrentLine;
		List<Integer> listR = new ArrayList<Integer>();
		List<Integer> listG = new ArrayList<Integer>();
		List<Integer> listB = new ArrayList<Integer>();
		while ((sCurrentLine = br.readLine()) != null) {
			String[] tmp = sCurrentLine.split("\t");
			if (tmp.length>2)
			{
			int Rval = Integer.parseInt(tmp[0]);
			int Gval = Integer.parseInt(tmp[1]);
			int Bval = Integer.parseInt(tmp[2]);
			listR.add(Rval);
			listG.add(Gval);
			listB.add(Bval);
			}
		}
		br.close();
		// Assuming the points are endpoints of a diameter of a circle, determine
		// the center and radius:
		double faceCenterX = (xy[0] + xy[2]) / 2.0;
		double faceCenterY = (xy[1] + xy[3]) / 2.0;
		double faceRadius = Math.sqrt((xy[0]-faceCenterX)*(xy[0]-faceCenterX) + (xy[1]-faceCenterY)*(xy[1]-faceCenterY));
	    System.out.println("\nFace Center X = \n" + faceCenterX);
	    System.out.println("\nFace Center Y = \n" + faceCenterY);
	    System.out.println("\nFace Radius = \n" + faceRadius);
		int w = imgwidth;
		int h = imgheight;
		// Do a luminance histogram on pixels inside the circle.
		// First, find the pixel rectangle containing the circle:
		int imin = (int) (faceCenterX - faceRadius);
		if (imin < 0) imin = 0;
		int imax = (int) (faceCenterX + faceRadius);
		if (imax > w) imax = w;
		int jmin = (int) (faceCenterY - faceRadius);
		if (jmin < 0) jmin = 0;
		int jmax = (int) (faceCenterY + faceRadius);
		if (jmax > h) jmax = h;
		int wBox = imax - imin + 1;
		int hBox = jmax - jmin + 1;
		double rsquare = faceRadius * faceRadius;
		// Loop over all pixels in the circle to get luminance values and
		// build a luminance histogram. We assume that the linearization
		// table and therefore luminance have a 12-bit range, making the
		// luminance histogram size 4096.
		double[] luminance = new double[wBox * hBox];
		int[] lumHist = new int[4096];
		for (i = 0; i<4096; i++) {
			lumHist[i] = 0;
		}
		int pixelCount = 0;
		for (j=jmin; j<jmax; j++) {
			for (i=imin; i<imax; i++) {
				double dsquare = (i-faceCenterX)*(i-faceCenterX) + (j-faceCenterY)*(j-faceCenterY);
				if (dsquare <= rsquare) {
					int rLin = listR.get(ImgPixelsR[j][i]);
					int gLin = listG.get(ImgPixelsG[j][i]);
					int bLin = listB.get(ImgPixelsB[j][i]);
					luminance[(j-jmin)*wBox + (i-imin)] = 0.222*rLin + 0.707*gLin + 0.071*bLin;
					lumHist[(int)luminance[(j-jmin)*wBox + (i-imin)]]++;
					pixelCount++;
				}
			}
		}
		// Now we determine the diffuse highlight range of luminances by using the
		// histogram to find the 50th and 80th percentile levels.
		i=0;
		int tempCount = lumHist[0];
		while (tempCount < 0.5*pixelCount && i<4095) {
			i++;
			tempCount = tempCount + lumHist[i];
		}
		double minLuminance = (double)i;

		while (tempCount < 0.8*pixelCount && i<4095) {
			i++;
			tempCount += lumHist[i];
		}
		double maxLuminance = (double)i;
		lumHist = null;
		// For pixels in the circle having luminance in the selected range, we measure chroma
		// and make a 2D histogram. Sticking with the bin size in the original algorithm, we
		// scale chroma to the 0-50 range for histogramming.
		int[] chromaHist = new int[51 * 51];
		for (i=0; i<51*51; i++) {
			chromaHist[i] = 0;
		}
		tempCount = 0;
		for (j = jmin; j<jmax; j++) {
			for (i=imin; i<imax; i++) {
				double dsquare = (i-faceCenterX)*(i-faceCenterX) + (j-faceCenterY)*(j-faceCenterY);
				if (dsquare <= rsquare) {
					double lum = luminance[(j-jmin)*wBox + (i-imin)];
					if (lum >= minLuminance && lum <= maxLuminance) {
						int rLin = listR.get(ImgPixelsR[j][i]);
						int gLin = listG.get(ImgPixelsG[j][i]);
						int bLin = listB.get(ImgPixelsB[j][i]);
						double chromaR = (double)(rLin) / (double)(bLin + gLin + rLin);// Casting to make sure the result of division will be double!
						double chromaG = (double)(gLin) / (double)(bLin + gLin + rLin);// Casting to make sure the result of division will be double!
						chromaHist[51*(int)(50.0*chromaR) + (int)(50.0*chromaG)]++;
						tempCount++;
					}
				}
			}
		}
		luminance = null;
		// Find the peak of the histogram;
		int peak = 0;
		int iPeak = 0;
		for (i=0; i<51*51; i++) {
			if (chromaHist[i] > peak) {
				peak = chromaHist[i];
				iPeak = i;
			}
		}
		chromaHist = null;
	// Reconstitute the chroma values at the peak:
		double cRFace = (iPeak/51) / 50.0;
		double cGFace = (iPeak%51) / 50.0;
	// Having found what we're willing to believe is a pair of skin chroma
	// values, we now want to find all points within a reasonable range of
	// those values.
		int[] faceMap = new int[wBox * hBox];
	// We'll also be doing a histogram of luminance values over just the
	// skin-matching points.
		int[] histogram = new int[256];
		for (i=0; i<256; i++) {
			histogram[i] = 0;
		}
		double tolerance = 0.01;
		int whiteCount;
		int blackCount;
		do {
			whiteCount = 0;
			blackCount = 0;
			for (j=jmin; j<jmax; j++) {
				for (i=imin; i<imax; i++) {
					double dsquare = (i-faceCenterX)*(i-faceCenterX) + (j-faceCenterY)*(j-faceCenterY);
					if (dsquare <= rsquare) {
						int rLin = listR.get(ImgPixelsR[j][i]);
						int gLin = listG.get(ImgPixelsG[j][i]);
						int bLin = listB.get(ImgPixelsB[j][i]);
						double chromaR = (double)(rLin) / (double)(bLin + gLin + rLin);
						double chromaG = (double)(gLin) / (double)(bLin + gLin + rLin);
						if (Math.abs(chromaR-cRFace)<=tolerance && Math.abs(chromaG-cGFace)<=tolerance) {
							faceMap[(j-jmin)*wBox + (i-imin)] = 255;
							whiteCount++;
							int bin = (int)(0.222*rLin/16.0 + 0.707*gLin/16.0 + 0.071*bLin/16.0);
							histogram[bin]++;
						} else {
							faceMap[(j-jmin)*wBox + (i-imin)] = 0;
							blackCount++;
						}
					}
				}
			}
			tolerance += 0.005;
		} while (whiteCount<=blackCount && tolerance<1.0);
		if (tolerance > 0.99) {
			faceMap = null;
		}
	// Integrating to get "area" under the luminance histogram - no, the
	// histogram is also not cleared inside the outer loop.
		int area = 0;
		for (i=0; i<256; i++) {
			area +=histogram[i];
		}
		tempCount = 0;
		boolean[] levelFlag = new boolean[256];
	// The levelFlag array is TRUE for all luminance histogram bins in
	// the diffuse highlight range;
		for (j=0; j<256; j++) {
			levelFlag[j] = false;
			for (i=0; i<histogram[j]; i++) {
				if (tempCount >= 0.50*area && tempCount <= 0.80*area) {
					levelFlag[j] = true;
				}
				tempCount++;
			}
		}
		histogram = null;
		// Finally, the last pass through the image. This time we look at the points
		// that we decided were in the skin-tone area, take the ones in the diffuse
		// highlight range, and average the RGB components.
			double avgR = 0.0; double avgG = 0.0; double avgB = 0.0;
			tempCount = 0;
			for (j=jmin; j<jmax; j++) {
				for (i=imin; i<imax; i++) {
					double dsquare = (i-faceCenterX)*(i-faceCenterX) + (j-faceCenterY)*(j-faceCenterY);
					if (dsquare <= rsquare) {
						if (faceMap[(j-jmin)*wBox + (i-imin)]>0) {
							int rLin = listR.get(ImgPixelsR[j][i]);
							int gLin = listG.get(ImgPixelsG[j][i]);
							int bLin = listB.get(ImgPixelsB[j][i]);
							double grayLevel = 0.222*rLin/16.0 + 0.707*gLin/16.0 + 0.071*bLin/16.0;
							if (levelFlag[(int)grayLevel]) {
								avgR += (double)rLin / 16.0;
								avgG += (double)gLin / 16.0;
								avgB += (double)bLin / 16.0;
								tempCount++;
							}
						}
					}
				}
			}
			faceMap = null;
			if (tempCount>0) {
				avgR /= tempCount;
				avgG /= tempCount;
				avgB /= tempCount;
			}
			System.out.format("\nDiffuses values for R, G, and B are: %f \t %f \t %f\n",avgR,avgG,avgB);
			ImgPixels=null;ImgPixelsR=null;ImgPixelsG=null;ImgPixelsB=null;listR=null;listG=null;listB=null;
			br = null;luminance =null;lumHist =null;chromaHist =null;faceMap =null;histogram =null;levelFlag=null;
			return new int[] {(int) Math.round(avgR),(int) Math.round(avgG),(int) Math.round(avgB)};
    }
    
    public static int FrameByFrameConv(int[] rgbSubjct, int[] rgbRef)
    {
    	FrameByFrameLevelModf testLevel = FrameByFrameLevelModf.selectBySkinLevels(rgbSubjct, rgbRef);
		int convertedgray = testLevel.getLevelInt();
		return convertedgray;
    }
    
    public static String FbyFLUTname(int[] rgbSubjct, int[] rgbRef)
    {
    	FrameByFrameLevelModf testLevel = FrameByFrameLevelModf.selectBySkinLevels(rgbSubjct, rgbRef);
		String FbyFLUT = testLevel.getName();
		return FbyFLUT;
    }
    
    public static void BuildandApplyFinalLUT(String LUT812, String LUTFbyF, String LUT128,String inImagePath, String outImagePath) throws NumberFormatException, IOException
    {
    	BufferedReader br1 = null; int i=0; int j=0;
    	br1 = new BufferedReader(new FileReader(LUT812));
		String sCurrentLine;
		List<Integer> listR = new ArrayList<Integer>();
		List<Integer> listG = new ArrayList<Integer>();
		List<Integer> listB = new ArrayList<Integer>();
		while ((sCurrentLine = br1.readLine()) != null) {
			String[] tmp = sCurrentLine.split("\t");
			if (tmp.length>2)
			{
			int Rval = Integer.parseInt(tmp[0]);
			int Gval = Integer.parseInt(tmp[1]);
			int Bval = Integer.parseInt(tmp[2]);
			listR.add(Rval);
			listG.add(Gval);
			listB.add(Bval);
			}
		}
		br1.close();
		
		BufferedReader br2 = null;
    	br2 = new BufferedReader(new FileReader(LUTFbyF));
		String sCurrentLine2;
		List<Integer> listR2 = new ArrayList<Integer>();
		List<Integer> listG2 = new ArrayList<Integer>();
		List<Integer> listB2 = new ArrayList<Integer>();
		while ((sCurrentLine2 = br2.readLine()) != null) {
			String[] tmp2 = sCurrentLine2.split("\t");
			if (tmp2.length>2)
			{
			int Rval2 = Integer.parseInt(tmp2[0]);
			int Gval2 = Integer.parseInt(tmp2[1]);
			int Bval2 = Integer.parseInt(tmp2[2]);
			listR2.add(Rval2);
			listG2.add(Gval2);
			listB2.add(Bval2);
			}
		}
		br2.close();
		
		BufferedReader br3 = null;
    	br3 = new BufferedReader(new FileReader(LUT128));
		String sCurrentLine3;
		List<Integer> listR3 = new ArrayList<Integer>();
		List<Integer> listG3 = new ArrayList<Integer>();
		List<Integer> listB3 = new ArrayList<Integer>();
		while ((sCurrentLine3 = br3.readLine()) != null) {
			String[] tmp3 = sCurrentLine3.split("\t");
			if (tmp3.length>2)
			{
			int Rval3 = Integer.parseInt(tmp3[0]);
			int Gval3 = Integer.parseInt(tmp3[1]);
			int Bval3 = Integer.parseInt(tmp3[2]);
			listR3.add(Rval3);
			listG3.add(Gval3);
			listB3.add(Bval3);
			}
		}
		br3.close();
		
		BufferedImage bufferedImage = null;
    	try {
    		bufferedImage = ImageIO.read(new File(inImagePath));
    	} catch (IOException e) {
    	}
    	
		//RenderedImage imageorignal = ImageUtils.loadImageFile(inImagePath);
		//BufferedImage bufferedImage = IppImageUtils.getBufferedImageForRenderedImage(imageorignal);
		WritableRaster rasterimg = bufferedImage.getRaster();
		int imgheight = rasterimg.getHeight();
		int imgwidth = rasterimg.getWidth();
		int[] iArray = null;
		int[] ImgPixels = rasterimg.getPixels(0, 0, imgwidth, imgheight, iArray);
		BufferedImage img = new BufferedImage(imgwidth, imgheight, BufferedImage.TYPE_INT_RGB);
		for (i = 0; i < imgheight; i++) {
			for (j = 0; j < imgwidth; j++) {
				int rLin = listR.get(ImgPixels[3*i*imgwidth + 3*j]);
				int gLin = listG.get(ImgPixels[3*i*imgwidth + 3*j+1]);
				int bLin = listB.get(ImgPixels[3*i*imgwidth + 3*j+2]);
				int rlin2 = listR2.get(rLin);
				int glin2 = listG2.get(gLin);
				int blin2 = listB2.get(bLin);
				int r = listR3.get(rlin2);
				int g = listG3.get(glin2);
				int b = listB3.get(blin2);
				int col = (r << 16) | (g << 8) | b;
				img.setRGB(j, i, col);
			}
		}
		ImageUtils.saveToFileBMP(img, outImagePath);
		br1=null;listR=null;listG =null;listB =null;br2=null;listR2=null;listG2 =null;listB2 =null;
		br3=null;listR3=null;listG3 =null;listB3 =null;ImgPixels=null;
    }
    
    private static String getValue(String tag, Element element) {
    	NodeList nodes = element.getElementsByTagName(tag).item(0).getChildNodes();
    	Node node = (Node) nodes.item(0);
    	return node.getNodeValue();
    	}
    
    public static LUTPath GetCamPropertiesfromXML (String CamType, String XMLPath, String InputLUTDir) throws ParserConfigurationException, SAXException, IOException{
    	
    	LUTPath RetrunedCamPropPath = new LUTPath();
	    File cameras = new File(XMLPath);
	    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
	    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
	    org.w3c.dom.Document doc = dBuilder.parse(cameras);
	    doc.getDocumentElement().normalize();
	    NodeList nodes = doc.getElementsByTagName("Camera");
	    for (int i = 0; i < nodes.getLength(); i++) {
	    	Node node = nodes.item(i);
	    	if (node.getNodeType() == Node.ELEMENT_NODE) {
	    	Element element = (Element) node;
	    	String CamName = getValue("CameraCode", element);
	    	if ( CamName.equals(CamType) ){
	    		RetrunedCamPropPath.InputLutPath = InputLUTDir + getValue("InputLutTable", element);
	    		RetrunedCamPropPath.MonitorLutPath = InputLUTDir + getValue("MonitorLutTable", element);
	    		RetrunedCamPropPath.InvMonitorLutPath = InputLUTDir + getValue("InvMonitorLutTable", element);
	    		String RedRef = getValue("SkinReferenceRed", element); int R = Integer.parseInt(RedRef);
	    		String GreenRef = getValue("SkinReferenceRed", element); int G = Integer.parseInt(GreenRef);
	    		String BlueRef = getValue("SkinReferenceRed", element); int B = Integer.parseInt(BlueRef);
	    		RetrunedCamPropPath.RGBRef = new int[]{R, G, B};
	    		break;
	    	}
	    }
	  }
	    cameras = null;
	    return RetrunedCamPropPath;
    }

    
	public static String ProcessSkinTone(String[] args) {
		
		try
		{
	    if(args.length < 3)
	    {
	        System.out.println("ERROR! Not enough Input arguments. Exiting now.");
	        return "false, Not enough Input arguments.";
	    }
	    String InputImagePath = args[0];
	    System.out.format("\n1st arg is : %s\n",args[0]);
        String OutputImagePath = args[1];
		System.out.format("\n2nd arg is : %s\n",args[1]);
        String CameraType = args[2];
		System.out.format("\n3rd arg is : %s\n\n",args[2]);
	    String InputLUTDir = "C:\\SkinToneBalance\\Luts\\";
	    String XMLFilePath = "C:\\SkinToneBalance\\XML\\SkinCameraProperties.xml";
	    LUTPath CamProp = GetCamPropertiesfromXML(CameraType, XMLFilePath, InputLUTDir);
        String LUTPath812 = CamProp.InvMonitorLutPath;
        String LUTPath128 = CamProp.MonitorLutPath;
        int[] RGBRef = CamProp.RGBRef;
            
		int[] Eyexy = SkinToneBalance.FindFaceFeats(InputImagePath);
		// if we could not find any face
		if(Eyexy[0]==0 && Eyexy[1]==0 && Eyexy[2]==0 && Eyexy[3]==0){
			
		    String tablenum = "021";
		    String FbyFLUTpath = InputLUTDir.concat("fbftable").concat(tablenum).concat(".txt");
		    System.out.format("\nF by F LUT name is : %s\n\n",FbyFLUTpath);
	        
		    SkinToneBalance.BuildandApplyFinalLUT(LUTPath812,FbyFLUTpath,LUTPath128,InputImagePath,OutputImagePath);
			
		    RGBRef = null; Eyexy=null;
		}else{
			
			int[] RGBSubjct = SkinToneBalance.GetDiffuseHighlight(InputImagePath, Eyexy, LUTPath812);
	        
		    String FbyFLUT = SkinToneBalance.FbyFLUTname(RGBSubjct,RGBRef);
		    String[] parts = FbyFLUT.split("_");
		    String tablenum = parts[parts.length - 1];
		    String FbyFLUTpath = InputLUTDir.concat("fbftable").concat(tablenum).concat(".txt");
		    System.out.format("\nF by F LUT name is : %s\n\n",FbyFLUTpath);
	        
		    SkinToneBalance.BuildandApplyFinalLUT(LUTPath812,FbyFLUTpath,LUTPath128,InputImagePath,OutputImagePath);
			
		    RGBRef = null; Eyexy=null;RGBSubjct=null;parts=null;
		}

		System.out.println("\nAll done!\n");
		}
		catch(Exception e)
		{
			System.out.println(e.toString());
			String returnString = "false," + e.toString();
			return returnString;
		}
		return "true";
	}
}

class LUTPath{
	String InputLutPath;
	String MonitorLutPath;
	String InvMonitorLutPath;
	int[] RGBRef;
}

