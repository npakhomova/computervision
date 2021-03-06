package processing;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import utils.DeltaE;
import utils.MapUtils;
import utils.ColorAndPercents;
import utils.Shoes;

import static utils.ImageShow.imshow;

import static utils.Colors.*;

public class Cluster {

    File pathForSort = new File("./demo/sort");

    Mat cl = new Mat();
    Mat center = new Mat();
    Mat label = new Mat();
    int k = 5;
    Map<Integer, Integer> counts = new HashMap<>();
    Map<double[], String> nameOfColorByCode = new HashMap<>();
    List<ColorAndPercents> colorByLabel = new ArrayList<>();

    boolean isHorizontal = false;

    ArrayList<String> colorNames;
    ArrayList<ColorCode> colorCodes;

    public ImageProcessingResult segmentation(File file, boolean isBatched, String category) {

        Mat image = Imgcodecs.imread(file.getAbsolutePath());
        Mat imageForSegmentation = image.clone();
        Mat original = image.clone();
        Mat mask = Mask.getMask(image, Shoes.LIST.contains(category));
        Core.bitwise_and(imageForSegmentation, mask, imageForSegmentation);

        Imgproc.medianBlur(imageForSegmentation, imageForSegmentation, 3);
        Imgproc.cvtColor(imageForSegmentation, imageForSegmentation, Imgproc.COLOR_BGR2Lab);

        Mat imageInARow = imageForSegmentation.reshape(1, mask.cols() * mask.rows());

        ArrayList<Integer> nonZeroIndexes = getNonZeroIndexes(imageInARow);

        Mat imagePreparedByMask = getMatPreparedByNonZeroIndexes(imageInARow, nonZeroIndexes);

        cluster(imagePreparedByMask, mask, k, nonZeroIndexes);

        ArrayList<double[]> colors = new ArrayList<>();

        for (int x = 0; x < center.rows(); x++) {
            colors.add(new double[]{center.get(x, 0)[0],
                    center.get(x, 1)[0],
                    center.get(x, 2)[0]});
        }

        double dist;
        double min = Double.MAX_VALUE;
        double[] key = null;
//        double[] howItMatched = null;

        colorNames = new ArrayList<String>();
        colorCodes = new ArrayList<ColorCode>();

        for (int j = 0; j < colors.size(); j++) {
            String colorNameForDescription = null;
            for (String colorName : getRealColorsPalette().keySet()) {
                for (double[] doubles : getRealColorsPalette().get(colorName)) {

                    double[] lab1 = new double[]{
                            colors.get(j)[0] / 2.55,
                            colors.get(j)[1] - 128,
                            colors.get(j)[2] - 128
                    };

                    double[] lab2 = new double[]{
                            doubles[0],
                            doubles[1],
                            doubles[2]
                    };

                    dist = DeltaE.deltaE2000(lab1, lab2);

                    if (min > dist) {
                        min = dist;
                        key = doubles;
                        colorNameForDescription = colorName;
//                    howItMatched = lab1;
                    }
                }
            }
            if (key != null) {
//                System.out.println(key[0] + "," + key[1] + "," + key[2] + ": " + Palette.get(key));
//                System.out.println(howItMatched[0] + "," + howItMatched[1] + "," + howItMatched[2] + ": " + Palette.get(key));
                colorNames.add(colorNameForDescription);
                double[] lab = new double[]{
                        colors.get(j)[0] / 2.55,
                        colors.get(j)[1] - 128,
                        colors.get(j)[2] - 128
                };
                nameOfColorByCode.put(lab, colorNameForDescription);
                colorCodes.add(new ColorCode(colorNameForDescription, colors.get(j)));
                min = Double.MAX_VALUE;
                key = null;
            }
        }

//        for (ColorCode colorCode : colorCodes) {
//            System.out.print("\n" + colorCode.getName() + " ");
//            System.out.print("{" + (int) (colorCode.getCode()[0] / 2.55) + ",  " + (int) (colorCode.getCode()[1] - 128) + ",  " + (int) (colorCode.getCode()[2] - 128) + "}");
//        }
//        System.out.println();


        double sumOfPixels = 0;
        for (Integer integer : counts.values()) {
            sumOfPixels = sumOfPixels + integer;
        }

        LinkedHashMap<String, Integer> nameAndPercents = new LinkedHashMap<String, Integer>();

        for (Integer index : counts.keySet()) {
            String name = colorNames.get(index);
            int percent = (int) (counts.get(index) / sumOfPixels * 100);
            if (nameAndPercents.containsKey(name)) {
//                System.out.println(name + " " + percent + "%" + "(merged) common val:" + (nameAndPercents.get(name) + percent) + "%");
                nameAndPercents.put(name, nameAndPercents.get(name) + percent);
            } else {
                nameAndPercents.put(name, percent);
//                System.out.println(name + " " + percent + "%");
            }
        }

        Map<String, Integer> sortedByPercent = MapUtils.sortByValue(nameAndPercents);
//        for (Map.Entry<String, Integer> colorEntry : sortedByPercent.entrySet()) {
//            System.out.println("Color='" + colorEntry.getKey()  + "' \t\t percent=" + colorEntry.getValue()  + "%");
//        }

        Iterator<Map.Entry<String, Integer>> iterator = sortedByPercent.entrySet().iterator();
        Map.Entry<String, Integer> first = null;
        Map.Entry<String, Integer> second = null;
        Map.Entry<String, Integer> third = null;
        Map.Entry<String, Integer> fourth = null;
        Map.Entry<String, Integer> fifth = null;
        if (iterator.hasNext()) first = iterator.next();
        if (iterator.hasNext()) second = iterator.next();
        if (iterator.hasNext()) third = iterator.next();
        if (iterator.hasNext()) fourth = iterator.next();
        if (iterator.hasNext()) fifth = iterator.next();

        if (first == null) {
            throw new IllegalArgumentException("No colors found");
        }

        sortedByPercent = MapUtils.sortByValue(nameAndPercents);

        iterator = sortedByPercent.entrySet().iterator();
        ArrayList<Map.Entry<String, Integer>> colorArea = new ArrayList<>();

        while ((iterator.hasNext())) colorArea.add(iterator.next());

        if (colorArea.isEmpty())
            throw new IllegalArgumentException("No colors were found");

        int sumWithoutDominantAndWhite = 0;

        for (int i = 1; i < colorArea.size(); i++) {
            if (!colorArea.get(i).getKey().equals("white")) {
                sumWithoutDominantAndWhite = sumWithoutDominantAndWhite + colorArea.get(i).getValue();
            }
        }

        for (Integer index : counts.keySet()) {
            int x = (int) center.get(index, 2)[0];
            int y = (int) center.get(index, 1)[0];
            int z = (int) center.get(index, 0)[0];
            colorByLabel.add(new ColorAndPercents(index, new double[]{x, y, z}, counts.get(index) / sumOfPixels * 100));
        }

        Mat crop = original.submat(Mask.y, Mask.y + Mask.h, Mask.x, Mask.x + Mask.w);

        if (isBatched) {
            if (first.getValue() >= 60 || (first.getValue() >= 55 && !first.getKey().equals("white"))) {
                writeOriginalToPath(original, file, first.getKey());
            } else if (first.getValue() >= 50 && (first.getKey().equals("black") || first.getKey().equals("grey"))) {
                writeOriginalToPath(original, file, first.getKey());
            } else if (first.getValue() >= 50 && !first.getKey().equals("white") && (second != null)) {
                if (second.getKey().equals("white") || second.getValue() <= 20) {
                    writeOriginalToPath(original, file, first.getKey());
                }
            } else if (first.getValue() >= 20 &&
                    second != null && second.getValue() >= 20 &&
                    third != null && third.getValue() >= 20) {
                writeOriginalToPath(original, file, "multi");
            } else if (first.getValue() >= 40 && !first.getKey().equals("white")) {
                if (second != null && second.getValue() >= 10 &&
                        third != null && third.getValue() >= 10 &&
                        fourth != null && fourth.getValue() >= 10 &&
                        fifth != null && fifth.getValue() >= 10) {
                    writeOriginalToPath(original, file, "multi");
                } else if (second != null && second.getValue() >= 20 && !second.getKey().equals("white")) {
                    writeOriginalToPath(original, file, "multi");
                } else {
                    writeOriginalToPath(original, file, first.getKey());
                }
            } else if (first.getValue() >= 40) {
                if (second != null && second.getValue() >= 10 &&
                        third != null && third.getValue() >= 10 &&
                        fourth != null && fourth.getValue() >= 10 &&
                        fifth != null && fifth.getValue() >= 10) {
                    writeOriginalToPath(original, file, "multi");
                } else if (second != null && second.getValue() >= 30) {
                    writeOriginalToPath(original, file, second.getKey());
                } else {
                    writeOriginalToPath(original, file, "multi");
                }
            } else if (first.getValue() >= 30 && !first.getKey().equals("white")) {
                if (second != null && second.getKey().equals("white") && second.getValue() >= 20) {
                    if (third != null && third.getValue() >= 15) {
                        writeOriginalToPath(original, file, "multi");
                    } else {
                        writeOriginalToPath(original, file, first.getKey());
                    }
                }
            } else if (colorByLabel.size() == 5) {
                writeOriginalToPath(original, file, "multi");
            } else {
                writeOriginalToPath(original, file, "multi");
            }
        }

        Imgproc.cvtColor(cl, cl, Imgproc.COLOR_Lab2BGR);

        Mat cropCl = cl.submat(Mask.y, Mask.y + Mask.h, Mask.x, Mask.x + Mask.w);

        cropCl = checkAndResize(cropCl);

        crop = checkAndResize(crop);

        Mat colorExp = Mat.zeros(40, isHorizontal ? cropCl.width() * 2 : cropCl.width(), CvType.CV_8UC3);

        Collections.sort(colorByLabel);

        Iterator it = colorByLabel.iterator();

        int x = 0;
        int yd = colorExp.height();
        int xd = 0;
        while (it.hasNext()) {
            Mat colToBGR = Mat.zeros(1, 1, cl.type());
            ColorAndPercents cp = (ColorAndPercents) it.next();
            double[] labColors = new double[]{cp.getColorCode()[2], cp.getColorCode()[1], cp.getColorCode()[0]};
            Imgproc.rectangle(colToBGR, new Point(0, 0), new Point(1, 1), new Scalar(labColors), -1);
            Imgproc.cvtColor(colToBGR, colToBGR, Imgproc.COLOR_Lab2BGR);
            xd = x + (int) ((colorExp.width() / 100d) * cp.getPercent());
            double[] bgrColors = colToBGR.get(0, 0);
            if (!it.hasNext()) {
                xd = colorExp.width();
            }
            Imgproc.rectangle(colorExp, new Point(x, 0), new Point(xd, yd), new Scalar(bgrColors), -1);
            x = xd;
        }

        Imgproc.cvtColor(imageForSegmentation, imageForSegmentation, Imgproc.COLOR_Lab2BGR);

       // if (!isBatched) imshow(crop, cropCl, colorExp, file.getName(), isHorizontal);

        cl = new Mat();
        center = new Mat();
        label = new Mat();
        counts = new HashMap<Integer, Integer>();
        colorByLabel = new ArrayList<ColorAndPercents>();

//        for (Map.Entry<double[], String> stringEntry : nameOfColorByCode.entrySet()) {
//            System.out.println(stringEntry.getValue() + ": " + stringEntry.getKey()[0] + ", " + stringEntry.getKey()[1] + ", " + stringEntry.getKey()[2] + "\n");
//        }

        ColorCheck.setNameOfColorByCode(nameOfColorByCode);
        ColorCheck.setSortedByPercent(sortedByPercent);

        return new ImageProcessingResult(sortedByPercent, crop, cropCl, colorExp, file.getName());
    }

    private Mat checkAndResize(final Mat imageForResizing) {
        Mat resized = new Mat();
        Rectangle rect = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();

        double rectHeight = rect.getHeight();
        double rectWidth = rect.getWidth();
        double maxHeight = rectHeight * 0.80;
        double maxWidth = rectWidth * 0.90;

        int imWidth = imageForResizing.width();
        int imHeight = imageForResizing.height();

        if (rectWidth - (imWidth * 2) > rectHeight - (imHeight * 2)) {
            isHorizontal = true;
        }

        if ((!isHorizontal && maxHeight < imHeight * 2) ||
                (isHorizontal && maxWidth < imWidth * 2)) {
                if (!isHorizontal) {
                    if (maxHeight < imHeight * 2) {
                        double coefficient = maxHeight / (imHeight * 2);
                        int rWidth = (int) (imWidth * coefficient);
                        int rHeight = (int) (imHeight * coefficient);
                        Imgproc.resize(imageForResizing, resized, new Size(rWidth, rHeight));
                        imHeight = resized.height();
                        imWidth = resized.width();
                    }
                    if (maxWidth < imWidth) {
                        double coefficient = maxWidth / imWidth;
                        int rWidth = (int) (imWidth * coefficient);
                        int rHeight = (int) (imHeight * coefficient);
                        Imgproc.resize(imageForResizing, resized, new Size(rWidth, rHeight));
                    }
                } else {
                    if (maxHeight < imHeight) {
                        double coefficient = maxHeight / imHeight;
                        int rWidth = (int) (imWidth * coefficient);
                        int rHeight = (int) (imHeight * coefficient);
                        Imgproc.resize(imageForResizing, resized, new Size(rWidth, rHeight));
                        imHeight = resized.height();
                        imWidth = resized.width();
                    }
                    if (maxWidth < imWidth * 2) {
                        double coefficient = maxWidth / (imWidth * 2);
                        int rWidth = (int) (imWidth * coefficient);
                        int rHeight = (int) (imHeight * coefficient);
                        Imgproc.resize(imageForResizing, resized, new Size(rWidth, rHeight));
                    }
                }
            return resized;
        } else {
            return imageForResizing;
        }
    }

    public static Mat getMatPreparedByNonZeroIndexes(Mat imageInARow, ArrayList<Integer> nonZeroIndexes) {
        Mat imagePreparedByMask = Mat.zeros(nonZeroIndexes.size(), 3, imageInARow.type());

        for (int j = 0; j < nonZeroIndexes.size(); j++) {
            imagePreparedByMask.put(j, 0, imageInARow.get(nonZeroIndexes.get(j), 0));
            imagePreparedByMask.put(j, 1, imageInARow.get(nonZeroIndexes.get(j), 1));
            imagePreparedByMask.put(j, 2, imageInARow.get(nonZeroIndexes.get(j), 2));
        }
        return imagePreparedByMask;
    }

    public static ArrayList<Integer> getNonZeroIndexes(Mat imageInARow) {
        ArrayList<Integer> nonZeroIndexes = new ArrayList<Integer>();

        for (int j = 0; j < imageInARow.rows(); j++) {
            if (imageInARow.get(j, 0)[0] != 0 ||
                    imageInARow.get(j, 1)[0] - 128 != 0 ||
                    imageInARow.get(j, 2)[0] - 128 != 0) {
                nonZeroIndexes.add(j);
            }
        }
        return nonZeroIndexes;
    }

    private void writeOriginalToPath(Mat original, File file, String nameOfDominant) {
        File path = new File(pathForSort.getAbsolutePath() + File.separator + nameOfDominant);
        if (!path.exists()) path.mkdirs();
        Imgcodecs.imwrite(path + File.separator + file.getName(), original);
    }

    public List<Mat> cluster(Mat cutout, Mat mask, int k, ArrayList<Integer> integers) {
        Mat samples32f = new Mat();
        cutout.convertTo(samples32f, CvType.CV_32F, 1.0 / 255.0);
        if (cutout.height() != 0) {
            Mat labels = new Mat();
            TermCriteria criteria = new TermCriteria(TermCriteria.COUNT, 100, 1);
            Mat centers = new Mat();
            Core.kmeans(samples32f, k, labels, criteria, 1, Core.KMEANS_PP_CENTERS, centers);
            label = labels;
            return showClusters(cutout, mask, labels, centers, integers);
        } else {
            return null;
        }
    }

    private List<Mat> showClusters(Mat cutout, Mat mask, Mat labels, Mat centers, ArrayList<Integer> integers) {
        centers.convertTo(centers, CvType.CV_8UC1, 255.0);
        centers.reshape(3);
        List<Mat> clusters = new ArrayList<Mat>();
        Mat cluster = Mat.zeros(mask.rows(), mask.cols(), mask.type());
        for (int i = 0; i < centers.rows(); i++) {
            clusters.add(Mat.zeros(mask.size(), mask.type()));
            Imgproc.cvtColor(clusters.get(i), clusters.get(i), Imgproc.COLOR_BGR2Lab);
        }
        for (int i = 0; i < centers.rows(); i++) counts.put(i, 0);
        int index = 0;
        int nums = 0;

        for (int y = 0; y < mask.rows(); y++) {
            for (int x = 0; x < mask.cols(); x++) {
                if (nums > integers.get(integers.size() - 1) || nums < integers.get(index)) {
                    int ch1 = 0;
                    int ch2 = 128;
                    int ch3 = 128;
                    for (Mat mat : clusters) {
                        mat.put(y, x, ch1, ch2, ch3);
                        cluster.put(y, x, ch1, ch2, ch3);
                    }
                } else if (nums == integers.get(index)) {
                    int label = (int) labels.get(index, 0)[0];
                    int b = (int) centers.get(label, 0)[0];
                    int g = (int) centers.get(label, 1)[0];
                    int r = (int) centers.get(label, 2)[0];
                    clusters.get(label).put(y, x, b, g, r);
                    cluster.put(y, x, b, g, r);
                    index++;
                    try {
                        counts.put(label, (counts.get(label) + 1));
                    } catch (Exception ex) {
                        System.out.println("Smth happends" + counts);
                    }

                }
                nums++;
            }
        }

        center = centers;
        cl = cluster;

        return clusters;
    }

        private Map<String, Integer> sortedByPercent;
    public static class ImageProcessingResult {

        private Map<String, Integer> sortedByPercent;
        private Mat crop;
        private Mat cropCl;
        private Mat colorExp;
        private String name;
        private Map<double[], String> nameOfColorByCode;

        public ImageProcessingResult(Map<String, Integer> sortedByPercent, Mat crop, Mat cropCl, Mat colorExp, String name) {

            this.sortedByPercent = new HashMap<>();
            for (Map.Entry<String, Integer> entry : sortedByPercent.entrySet()){
                this.sortedByPercent.put(entry.getKey(), entry.getValue());
                this.sortedByPercent.put(entry.getKey().toLowerCase(), entry.getValue());
            }
            this.crop = crop;
            this.cropCl = cropCl;
            this.colorExp = colorExp;
            this.name = name;
        }

        public Map<String, Integer> getSortedByPercent() {
            return sortedByPercent;
        }

        public Mat getCrop() {
            return crop;
        }

        public Mat getCropCl() {
            return cropCl;
        }

        public Mat getColorExp() {
            return colorExp;
        }

        public String getName() {
            return name;
        }
    }

    class ColorCode {

        private String name;
        private double[] code;

        public ColorCode(String name, double[] code) {
            this.code = code;
            this.name = name;
        }

        public double[] getCode() {
            return code;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "ColorCode{" +
                    "name='" + name + '\'' +
                    ", code=" + Arrays.toString(code) +
                    '}';
        }
    }

}
