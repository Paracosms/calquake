package io.github.paracosms.calquake.data;

import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.Vs30Sample;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Loads, validates, and queries the compact California Vs30 raster grid
 * from {@code /data/geodata/california_vs30.bin}.
 */
public final class CaliforniaVs30Grid {

    public static final String DEFAULT_BIN_RESOURCE = "/data/geodata/california_vs30.bin";
    public static final String DATASET_ID = "usgs-global-vs30-mosaic-2025";
    public static final int EXPECTED_MAGIC = 0x43565333; // 'CVS3'
    public static final int EXPECTED_VERSION = 1;

    private static volatile CaliforniaVs30Grid defaultInstance;

    private final int version;
    private final int width;
    private final int height;
    private final double west;
    private final double north;
    private final double lonStep;
    private final double latStep;
    private final float nodataSentinel;
    private final float[] medianVs30;
    private final float[] lnSigma;
    private final String sha256Hex;

    public CaliforniaVs30Grid(
            int version, int width, int height,
            double west, double north, double lonStep, double latStep,
            float nodataSentinel, float[] medianVs30, float[] lnSigma, String sha256Hex
    ) {
        this.version = version;
        this.width = width;
        this.height = height;
        this.west = west;
        this.north = north;
        this.lonStep = lonStep;
        this.latStep = latStep;
        this.nodataSentinel = nodataSentinel;
        this.medianVs30 = Objects.requireNonNull(medianVs30, "medianVs30 cannot be null");
        this.lnSigma = Objects.requireNonNull(lnSigma, "lnSigma cannot be null");
        this.sha256Hex = Objects.requireNonNull(sha256Hex, "sha256Hex cannot be null");

        int expectedCells = width * height;
        if (medianVs30.length != expectedCells || lnSigma.length != expectedCells) {
            throw new IllegalArgumentException(String.format(
                    "Band length mismatch: expected %d cells, got median=%d, sd=%d",
                    expectedCells, medianVs30.length, lnSigma.length));
        }
    }

    public static CaliforniaVs30Grid loadDefault() {
        CaliforniaVs30Grid instance = defaultInstance;
        if (instance == null) {
            synchronized (CaliforniaVs30Grid.class) {
                instance = defaultInstance;
                if (instance == null) {
                    defaultInstance = instance = loadFromResource(DEFAULT_BIN_RESOURCE);
                }
            }
        }
        return instance;
    }

    public static CaliforniaVs30Grid loadFromResource(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath cannot be null");
        try (InputStream stream = CaliforniaVs30Grid.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalArgumentException("Vs30 binary resource not found: " + resourcePath);
            }
            return loadFromStream(stream);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to load Vs30 grid: " + resourcePath, e);
        }
    }

    public static CaliforniaVs30Grid loadFromStream(InputStream rawStream) throws IOException {
        Objects.requireNonNull(rawStream, "rawStream cannot be null");

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }

        byte[] allBytes;
        try (BufferedInputStream bis = new BufferedInputStream(rawStream)) {
            allBytes = bis.readAllBytes();
        }
        digest.update(allBytes);
        String sha256 = HexFormat.of().formatHex(digest.digest());

        if (allBytes.length < 52) {
            throw new IllegalArgumentException("Vs30 grid file too small for header: " + allBytes.length + " bytes");
        }

        ByteBuffer bb = ByteBuffer.wrap(allBytes).order(ByteOrder.BIG_ENDIAN);
        int magic = bb.getInt();
        if (magic != EXPECTED_MAGIC) {
            throw new IllegalArgumentException(String.format("Invalid magic 0x%08X (expected 0x%08X 'CVS3')", magic, EXPECTED_MAGIC));
        }

        int version = bb.getInt();
        if (version != EXPECTED_VERSION) {
            throw new IllegalArgumentException(String.format("Unsupported version %d (expected %d)", version, EXPECTED_VERSION));
        }

        int width = bb.getInt();
        int height = bb.getInt();
        double west = bb.getDouble();
        double north = bb.getDouble();
        double lonStep = bb.getDouble();
        double latStep = bb.getDouble();
        float nodata = bb.getFloat();

        int totalCells = width * height;
        int expectedFileSize = 52 + (totalCells * 4) + (totalCells * 4);
        if (allBytes.length != expectedFileSize) {
            throw new IllegalArgumentException(String.format(
                    "Vs30 file size mismatch: expected %d bytes for %dx%d grid, got %d",
                    expectedFileSize, width, height, allBytes.length));
        }

        float[] median = new float[totalCells];
        for (int i = 0; i < totalCells; i++) {
            median[i] = bb.getFloat();
        }

        float[] sd = new float[totalCells];
        for (int i = 0; i < totalCells; i++) {
            sd[i] = bb.getFloat();
        }

        return new CaliforniaVs30Grid(
                version, width, height, west, north, lonStep, latStep, nodata, median, sd, sha256
        );
    }

    /**
     * Deterministic nearest-cell Vs30 lookup for a geographic coordinate.
     *
     * @param point coordinate to query
     * @return sample containing median Vs30 and uncertainty, or empty if out of bounds / NoData
     */
    public Optional<Vs30Sample> sample(GeoPoint point) {
        Objects.requireNonNull(point, "point cannot be null");
        double lon = point.longitude();
        double lat = point.latitude();

        int col = (int) Math.round((lon - west) / lonStep);
        int row = (int) Math.round((north - lat) / latStep);

        if (col < 0 || col >= width || row < 0 || row >= height) {
            return Optional.empty();
        }

        int idx = row * width + col;
        float medianVal = medianVs30[idx];

        if (Float.isNaN(medianVal) || medianVal == nodataSentinel || medianVal <= 0.0f) {
            return Optional.empty();
        }

        float sdVal = lnSigma[idx];
        OptionalDouble sdOpt = (Float.isNaN(sdVal) || sdVal == nodataSentinel || sdVal <= 0.0f)
                ? OptionalDouble.empty()
                : OptionalDouble.of(sdVal);

        return Optional.of(new Vs30Sample(medianVal, sdOpt, col, row, DATASET_ID));
    }

    public int version() { return version; }
    public int width() { return width; }
    public int height() { return height; }
    public double west() { return west; }
    public double east() { return west + width * lonStep; }
    public double north() { return north; }
    public double south() { return north - height * latStep; }
    public double lonStep() { return lonStep; }
    public double latStep() { return latStep; }
    public float nodataSentinel() { return nodataSentinel; }
    public String sha256() { return sha256Hex; }
    public String datasetId() { return DATASET_ID; }

    public float medianAt(int col, int row) {
        if (col < 0 || col >= width || row < 0 || row >= height) {
            return nodataSentinel;
        }
        return medianVs30[row * width + col];
    }
}
