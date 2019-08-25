import java.awt.Color;
import java.awt.Point;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Optional;

import marvin.image.MarvinImage;
import marvin.io.MarvinImageIO;
import marvin.util.MarvinAttributes;
import org.marvinproject.image.transform.rotate.Rotate;

import static marvinplugins.MarvinPluginCollection.moravec;
import static marvinplugins.MarvinPluginCollection.thresholding;

public class Scanner {
    // We need to adjust the angle because of the detected corners not being mirrored
    private static final double ANGLE_ADJUSTMENT = 0.7;

    public Scanner() {
        // Load image
        MarvinImage image = MarvinImageIO.loadImage(getImagePath().get() + "input.jpg");

        // Found corners for rotation analysis
        int[][] cornernessMap = moravec(image.clone(), image, 5, 500);
        Point[] boundaries = boundaries(cornernessMap);

        // image = showCorners(image, boundaries, 12);

        // Print rotation angle
        double angle = (Math.atan2((boundaries[1].y * -1) - (boundaries[0].y * -1), boundaries[1].x - boundaries[0].x) * 180 / Math.PI);
        angle = angle >= 0 ? angle : angle + 360;
        System.out.println("Rotation angle:" + angle);



        Rotate rotate = new Rotate();
        rotate.load();
        MarvinAttributes attributes = rotate.getAttributes();
        final int adjustedAngle = (int)Math.round(angle - ANGLE_ADJUSTMENT);
        if (adjustedAngle != 0) {
            attributes.set("rotate", "Other");
            attributes.set("RotateAngle", adjustedAngle);
            MarvinImage cloned = image.clone();
            rotate.process(cloned, image);
        }

        // Convert to black & white
        thresholding(image.clone(), image, 190);

        MarvinImage test = image.clone();

        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (image.getIntColor(x, y) != -1) {
                    //System.out.println(x + "," + y + ": " + image.getIntColor(x, y));
                    //test.fillRect(x, y, 1, 1, Color.red);
                }
            }
        }

        // Appearance / Foam
        fillBox(10, 3, 527, 270, test);
        // Appearance / Color
        fillBox(10, 4, 527, 270 + 3*35, test);
        // Appearance / Clarity
        fillBox(10, 3, 527, 270 + 7*35, test);

        MarvinImageIO.saveImage(test, getImagePath().get() + "output.jpg");
    }

    private void fillBox(int boxCount, int rows, int startX, int startY, MarvinImage image) {
        int x = startX;
        int y = startY;

        for (int j = 0; j < boxCount; j++) {
            int [] colors = new int[rows];
            for (int i = 0; i < rows; i++) {
                // Go through the pixels inside the box
                int count = 0;
                int colorSum = 0;
                for (int k = x; k < x + 50; k++) {
                    for (int l = y; l < y + 28; l++) {
                        final int color = image.getIntColor(k, l);
                        if (color != -1) colorSum += color;
                        count++;
                    }
                }

                if ((colorSum / count) != 0) {
                    colors[i] = Math.abs(colorSum / count);
                    //image.fillRect(x, y, 50, 28, Color.red);
                }
                y += 35;
            }

            int maxIndex = 0;
            int maxColor = 0;
            for (int n = 0; n < colors.length; n++) {
                if (colors[n] > maxColor) {
                    maxColor = colors[n];
                    maxIndex = n;
                }
            }

            image.fillRect(x, startY + (maxIndex * 35), 50, 28, Color.red);

            y = startY;
            x += 61;
        }
    }

    private Point[] boundaries(int[][] cornernessMap) {
        Point upLeft = new Point(-1, -1);
        Point upRight = new Point(-1, -1);
        Point bottomLeft = new Point(-1, -1);
        Point bottomRight = new Point(-1, -1);
        double ulDistance = 9999, blDistance = 9999, urDistance = 9999, brDistance = 9999;
        double tempDistance = -1;

        for (int x = 0; x < cornernessMap.length; x++) {
            for (int y = 0; y < cornernessMap[0].length; y++) {
                if (cornernessMap[x][y] > 0) {
                    if ((tempDistance = Point.distance(x, y, 0, 0)) < ulDistance) {
                        upLeft.x = x;
                        upLeft.y = y;
                        ulDistance = tempDistance;
                    }
                    if ((tempDistance = Point.distance(x, y, cornernessMap.length, 0)) < urDistance) {
                        upRight.x = x;
                        upRight.y = y;
                        urDistance = tempDistance;
                    }
                    if ((tempDistance = Point.distance(x, y, 0, cornernessMap[0].length)) < blDistance) {
                        bottomLeft.x = x;
                        bottomLeft.y = y;
                        blDistance = tempDistance;
                    }
                    if ((tempDistance = Point.distance(x, y, cornernessMap.length, cornernessMap[0].length)) < brDistance) {
                        bottomRight.x = x;
                        bottomRight.y = y;
                        brDistance = tempDistance;
                    }
                }
            }
        }
        return new Point[]{upLeft, upRight, bottomRight, bottomLeft};
    }

    private MarvinImage showCorners(MarvinImage image, Point[] points, int rectSize) {
        MarvinImage ret = image.clone();
        for (Point p : points) {
            ret.fillRect(p.x - (rectSize / 2), p.y - (rectSize / 2), rectSize, rectSize, Color.red);
        }
        return ret;
    }

    public static void main(String[] args) {
        new Scanner();
    }

    private Optional<String> getImagePath() {
        try {
            URL resource = Scanner.class.getResource(File.separator + "input.jpg");
            return Optional.of(Paths.get(resource.toURI()).toFile().getParentFile().getAbsolutePath() + File.separator);
        } catch (URISyntaxException e) {
            System.err.println(e);
            return Optional.empty();
        }
    }
}