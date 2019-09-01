import java.awt.Color;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.ObjIntConsumer;

import marvin.image.MarvinImage;
import marvin.io.MarvinImageIO;
import marvin.util.MarvinAttributes;
import org.marvinproject.image.transform.rotate.Rotate;

import static marvinplugins.MarvinPluginCollection.moravec;

public class Scanner {
    private static final int CELL_WIDTH = 61;
    private static final int CELL_HEIGHT = 35;
    private static final int CELL_WIDTH_LEFT_MARGIN = 10;
    private static final int CELL_WIDTH_RIGHT_MARGIN = 10;
    private static final int CELL_HEIGHT_TOP_MARGIN = 14;
    private static final int CELL_HEIGHT_BOTTOM_MARGIN = 4;

    public Scanner() {
        // Load image
        MarvinImage image = MarvinImageIO.loadImage(getImagePath().get() + "input3.jpg");

        final double angle = getRotationAngle(image);
        rotateImage(image, angle);

        // Re-analyze the corners to get the bottom right corner
        Point[] boundaries = getBoundaries(image);
        image = drawBoundaries(image, boundaries, 12, Color.magenta);

        final Point bottomRightCorner = boundaries[2];
        final int dpiFactor = image.getWidth() / 1239; // int division so we round down
        final int firstCellX = bottomRightCorner.x - 10 * CELL_WIDTH * dpiFactor;
        final int firstCellY = bottomRightCorner.y - 41 * CELL_HEIGHT * dpiFactor;
        final BeerScore[] beerScores = new BeerScore[10];
        for (int i = 1; i <= beerScores.length; i++) {
            beerScores[i - 1] = new BeerScore(i);
        }

        analyzeScoreBlock(beerScores, BeerScore.FOAM_SCORES.length, BeerScore::setFoam, firstCellX, firstCellY, dpiFactor, image);
        analyzeScoreBlock(beerScores, BeerScore.COLOR_SCORES.length, BeerScore::setColor, firstCellX, firstCellY + 3 * CELL_HEIGHT * dpiFactor, dpiFactor, image);
        analyzeScoreBlock(beerScores, BeerScore.CLARITY_SCORES.length, BeerScore::setClarity, firstCellX, firstCellY + 7 * CELL_HEIGHT * dpiFactor, dpiFactor, image);
        analyzeScoreBlock(beerScores, BeerScore.AROMA_SCORES.length, BeerScore::setAroma, firstCellX, firstCellY + 11 * CELL_HEIGHT * dpiFactor, dpiFactor, image);
        analyzeScoreBlock(beerScores, BeerScore.FLAVOR_SCORES.length, BeerScore::setFlavor, firstCellX, firstCellY + 17 * CELL_HEIGHT * dpiFactor, dpiFactor, image);
        analyzeScoreBlock(beerScores, BeerScore.BITTERNESS_SCORES.length, BeerScore::setBitterness, firstCellX, firstCellY + 22 * CELL_HEIGHT * dpiFactor, dpiFactor, image);
        analyzeScoreBlock(beerScores, BeerScore.BODY_SCORES.length, BeerScore::setBody, firstCellX, firstCellY + 28 * CELL_HEIGHT * dpiFactor, dpiFactor, image);
        analyzeScoreBlock(beerScores, BeerScore.CARBONATION_SCORES.length, BeerScore::setCarbonation, firstCellX, firstCellY + 31 * CELL_HEIGHT * dpiFactor, dpiFactor, image);
        analyzeScoreBlock(beerScores, BeerScore.OVERALL_SCORES.length, BeerScore::setOverall, firstCellX, firstCellY + 35 * CELL_HEIGHT * dpiFactor, dpiFactor, image);

        MarvinImageIO.saveImage(image, getImagePath().get() + "output.jpg");

        for (BeerScore beerScore : beerScores) {
            System.out.println(beerScore);
        }
    }

    private static void rotateImage(MarvinImage image, double angle) {
        final int adjustedAngle = (int) Math.round(angle);
        if (adjustedAngle == 0) return;

        Rotate rotate = new Rotate();
        rotate.load();
        MarvinAttributes attributes = rotate.getAttributes();
        attributes.set("rotate", "Other");
        attributes.set("RotateAngle", adjustedAngle);

        MarvinImage cloned = image.clone();
        rotate.process(cloned, image);
        // Color the part outside the rotated document with white
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (image.getIntColor(x, y) >= -1) {
                    image.setIntColor(x, y, Color.white.getRGB());
                }
            }
        }
    }

    private static double getRotationAngle(MarvinImage image) {
        final Point[] boundaries = getBoundaries(image);
        final double angle = (Math.atan2((boundaries[3].y * -1) - (boundaries[2].y * -1), boundaries[3].x - boundaries[2].x) * (180 / Math.PI));
        return -(180 - angle);
    }

    private static Point[] getBoundaries(MarvinImage image) {
        // Find corners for rotation analysis
        int[][] cornernessMap = moravec(image, image, 5, 500);
        return findBoundaries(cornernessMap);
    }

    private static void analyzeScoreBlock(final BeerScore[] beerScores, int rows, ObjIntConsumer<BeerScore> setter, int startX, int startY, int dpiFactor, MarvinImage image) {
        int x = startX;
        int y = startY;

        for (BeerScore beerScore : beerScores) {
            int[] colors = new int[rows];
            for (int i = 0; i < rows; i++) {
                // Go through the pixels inside the box
                int count = 0;
                int colorSum = 0;
                for (int k = x + CELL_WIDTH_LEFT_MARGIN * dpiFactor; k < x + (CELL_WIDTH - CELL_WIDTH_RIGHT_MARGIN) * dpiFactor; k++) {
                    for (int l = y + CELL_HEIGHT_TOP_MARGIN * dpiFactor; l < y + (CELL_HEIGHT - CELL_HEIGHT_BOTTOM_MARGIN) * dpiFactor; l++) {
                        final int color = image.getIntColor(k, l);
                        if (color != -1) colorSum += color;
                        count++;
                        image.setIntColor(k, l, Color.blue.getRGB());
                    }
                }

                if ((colorSum / count) != 0) {
                    colors[i] = Math.abs(colorSum / count);
                }
                y += CELL_HEIGHT * dpiFactor;
            }

            int maxIndex = 0;
            int maxColor = 0;
            for (int n = 0; n < colors.length; n++) {
                if (colors[n] > maxColor) {
                    maxColor = colors[n];
                    maxIndex = n;
                }
            }
            setter.accept(beerScore, maxIndex);

            image.fillRect(
                x + CELL_WIDTH_LEFT_MARGIN * dpiFactor,
                startY + (maxIndex * CELL_HEIGHT * dpiFactor) + CELL_HEIGHT_TOP_MARGIN * dpiFactor,
                (CELL_WIDTH - CELL_WIDTH_LEFT_MARGIN) * dpiFactor - CELL_WIDTH_RIGHT_MARGIN * dpiFactor,
                (CELL_HEIGHT - CELL_HEIGHT_TOP_MARGIN) * dpiFactor - CELL_HEIGHT_BOTTOM_MARGIN * dpiFactor,
                Color.red
            );

            y = startY;
            x += CELL_WIDTH * dpiFactor;
        }
    }

    private static Point[] findBoundaries(int[][] cornernessMap) {
        Point upLeft = new Point(-1, -1);
        Point upRight = new Point(-1, -1);
        Point bottomLeft = new Point(-1, -1);
        Point bottomRight = new Point(-1, -1);
        double ulDistance = 9999, blDistance = 9999, urDistance = 9999, brDistance = 9999;
        double tempDistance = -1;

        for (int x = 0; x < cornernessMap.length; x++) {
            for (int y = 0; y < cornernessMap[0].length; y++) {
                if (cornernessMap[x][y] > 0) {
                    if ((tempDistance = Point2D.distance(x, y, 0, 0)) < ulDistance) {
                        upLeft.x = x;
                        upLeft.y = y;
                        ulDistance = tempDistance;
                    }
                    if ((tempDistance = Point2D.distance(x, y, cornernessMap.length, 0)) < urDistance) {
                        upRight.x = x;
                        upRight.y = y;
                        urDistance = tempDistance;
                    }
                    if ((tempDistance = Point2D.distance(x, y, 0, cornernessMap[0].length)) < blDistance) {
                        bottomLeft.x = x;
                        bottomLeft.y = y;
                        blDistance = tempDistance;
                    }
                    if ((tempDistance = Point2D.distance(x, y, cornernessMap.length, cornernessMap[0].length)) < brDistance) {
                        bottomRight.x = x;
                        bottomRight.y = y;
                        brDistance = tempDistance;
                    }
                }
            }
        }
        return new Point[]{upLeft, upRight, bottomRight, bottomLeft};
    }

    private static MarvinImage drawBoundaries(MarvinImage image, Point[] points, int rectSize, Color color) {
        MarvinImage ret = image.clone();
        for (Point p : points) {
            ret.fillRect(p.x - (rectSize / 2), p.y - (rectSize / 2), rectSize, rectSize, color);
        }
        return ret;
    }

    public static void main(String[] args) {
        new Scanner();
    }

    private static Optional<String> getImagePath() {
        try {
            URL resource = Scanner.class.getResource(File.separator + "input3.jpg");
            return Optional.of(Paths.get(resource.toURI()).toFile().getParentFile().getAbsolutePath() + File.separator);
        } catch (URISyntaxException e) {
            System.err.println(e);
            return Optional.empty();
        }
    }

    private static class BeerScore {
        private static final int[] FOAM_SCORES = {3, 2, 1};
        private static final int[] COLOR_SCORES = {3, 2, 1, 0};
        private static final int[] CLARITY_SCORES = {3, 2, 1};
        private static final int[] AROMA_SCORES = {4, 3, 2, 1, 0};
        private static final int[] FLAVOR_SCORES = {4, 3, 2, 1, 0};
        private static final int[] BITTERNESS_SCORES = {5, 4, 3, 2, 1};
        private static final int[] BODY_SCORES = {3, 2, 1};
        private static final int[] CARBONATION_SCORES = {3, 2, 1};
        private static final int[] OVERALL_SCORES = {8, 6, 4, 2, 0};

        final int beerNumber;
        int foam = 1;
        int color = 0;
        int clarity = 1;
        int aroma = 0;
        int flavor = 0;
        int bitterness = 1;
        int body = 1;
        int carbonation = 1;
        int overall = 0;

        public BeerScore(int beerNumber) {
            this.beerNumber = beerNumber;
        }

        int getTotal() {
            return foam + color + clarity + aroma + flavor + bitterness + body + carbonation + overall;
        }

        void setFoam(int foamIndex) {
            if (foamIndex < 0 || foamIndex > FOAM_SCORES.length - 1) {
                throw new IllegalArgumentException("Invalid foam index: " + foamIndex);
            }
            this.foam = FOAM_SCORES[foamIndex];
        }

        void setColor(int colorIndex) {
            if (colorIndex < 0 || colorIndex > COLOR_SCORES.length - 1) {
                throw new IllegalArgumentException("Invalid color index: " + colorIndex);
            }
            this.color = COLOR_SCORES[colorIndex];
        }

        void setClarity(int clarityIndex) {
            if (clarityIndex < 0 || clarityIndex > CLARITY_SCORES.length - 1) {
                throw new IllegalArgumentException("Invalid clarity index: " + clarityIndex);
            }
            this.clarity = CLARITY_SCORES[clarityIndex];
        }

        void setAroma(int aromaIndex) {
            if (aromaIndex < 0 || aromaIndex > AROMA_SCORES.length - 1) {
                throw new IllegalArgumentException("Invalid aroma index: " + aromaIndex);
            }
            this.aroma = AROMA_SCORES[aromaIndex];
        }

        void setFlavor(int flavorIndex) {
            if (flavorIndex < 0 || flavorIndex > FLAVOR_SCORES.length - 1) {
                throw new IllegalArgumentException("Invalid flavor index: " + flavorIndex);
            }
            this.flavor = FLAVOR_SCORES[flavorIndex];
        }

        void setBitterness(int bitternessIndex) {
            if (bitternessIndex < 0 || bitternessIndex > BITTERNESS_SCORES.length - 1) {
                throw new IllegalArgumentException("Invalid bitterness index: " + bitternessIndex);
            }
            this.bitterness = BITTERNESS_SCORES[bitternessIndex];
        }

        void setBody(int bodyIndex) {
            if (bodyIndex < 0 || bodyIndex > BODY_SCORES.length - 1) {
                throw new IllegalArgumentException("Invalid body index: " + bodyIndex);
            }
            this.body = BODY_SCORES[bodyIndex];
        }

        void setCarbonation(int carbonationIndex) {
            if (carbonationIndex < 0 || carbonationIndex > CARBONATION_SCORES.length - 1) {
                throw new IllegalArgumentException("Invalid carbonation index: " + carbonationIndex);
            }
            this.carbonation = CARBONATION_SCORES[carbonationIndex];
        }

        void setOverall(int overallIndex) {
            if (overallIndex < 0 || overallIndex > OVERALL_SCORES.length - 1) {
                throw new IllegalArgumentException("Invalid overall index: " + overallIndex);
            }
            this.overall = OVERALL_SCORES[overallIndex];
        }

        public String toString() {
            return String.format(
                "Beer number %d, Total: %d (foam: %d, color: %d, clarity: %d, aroma: %d, flavor: %d, bitterness: %d, body: %d, carbonation %d, overall %d)",
                beerNumber,
                getTotal(),
                foam,
                color,
                clarity,
                aroma,
                flavor,
                bitterness,
                body,
                carbonation,
                overall
            );
        }

    }
}