package com.vg.apng;

import static java.nio.ByteBuffer.wrap;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import javax.imageio.ImageIO;

/**
 * Represent a grayscale image.
 */
public class Gray {

    private final int width;
    private final int height;
    private final ByteBuffer data;
    // Defaults to 1s
    private int delayms = 1000;

    /**
     * Create a new Gray from info and data.
     * @param width the image width
     * @param height the image height
     * @param data a buffer containing the pixel data
     * @param delay the delay to put between this image and the next
     */
    public Gray(int width, int height, ByteBuffer data, int delay) {
        if (width * height > data.capacity()) {
            throw new IllegalArgumentException();
        }
        this.width = width;
        this.height = height;
        this.data = (ByteBuffer) data.duplicate().clear();
        this.setDelay(delay);
    }

    /**
     * Create a new Gray from info and data.
     * @param width the image width
     * @param height the image height
     * @param data a buffer containing the pixel data
     * @param delay the delay to put between this image and the next
     */
    public Gray(int width, int height, byte[] data, int delay) {
        this(width, height, wrap(data), delay);
    }

    public Gray(int width, int height) {
        this(width, height, APNG.DELAY_1S);
    }

    public Gray(int width, int height, ByteBuffer bb) {
        this(width, height, bb, APNG.DELAY_1S);
    }

    public Gray(int width, int height, byte[] data) {
        this(width, height, data, APNG.DELAY_1S);
    }

    /**
     * Create a new Gray from its info
     * @param width the image width
     * @param height the image height
     * @param delay the delay to put between this image and the next
     */
    public Gray(int width, int height, int delay) {
        this(width, height, ByteBuffer.allocate(width * height), delay);
    }
	
	public int getDelay() {
        return delayms;
    }

    public void setDelay(int delay) {
        this.delayms = delay;
    }
    
    public byte getPixel(int x, int y) {
        return data.get(y * width + x);
    }
    
    public void putPixel(int x, int y, int pix) {
        data.put(y * width + x, (byte) pix);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Rectangle getBounds() {
        return new Rectangle(0, 0, width, height);
    }

    public ByteBuffer getData() {
        ByteBuffer d = data.duplicate();
        d.clear();
        return d;
    }

    public Dimension getDimension() {
        return new Dimension(width, height);
    }

    public Gray equalizeHist1() {
        int hist_sz = 256;
        int hist[] = new int[hist_sz];
        Dimension size = getDimension();

        ByteBuffer sptr = getData();
        for (int y = 0; y < size.height; y++) {
            for (int x = 0; x < size.width; x++)
                hist[sptr.get() & 0xff]++;
        }

        float scale = 255.f / (size.width * size.height);
        int sum = 0;
        byte lut[] = new byte[hist_sz];

        for (int i = 0; i < hist_sz; i++) {
            sum += hist[i];
            int val = Math.round(sum * scale);
            lut[i] = (byte) val;
        }

        lut[0] = 0;
        Gray dst = new Gray(size.width, size.height);
        ByteBuffer dptr = dst.getData();
        sptr = getData();
        for (int y = 0; y < size.height; y++) {
            for (int x = 0; x < size.width; x++)
                dptr.put(lut[sptr.get() & 0xff]);
        }
        return dst;

    }

    public Gray equalizeHist() {
        int hist_sz = 256;
        int hist[] = new int[hist_sz];
        Dimension size = getDimension();

        ByteBuffer srcData = getData();
        for (int y = 0; y < size.height; y++) {
            for (int x = 0; x < size.width; x++)
                hist[srcData.get() & 0xff]++;
        }

        int maxCount = size.width * size.height * 2 / 100;
        int black = 0;
        int counter = 0;

        // count up to maxCount (2%) values from the black side of the histogram to find the black point
        while ((counter < maxCount) && (black < 256)) {
            counter += hist[black];
            black++;
        }

        int white = 255;
        counter = 0;

        // count up to maxCount (2%) values from the white side of the histogram to find the white point
        while ((counter < maxCount) && (white > 0)) {
            counter += hist[white];
            white--;
        }
        byte[] lut = new byte[256];
        int range = white - black;
        float mult = 255f / range;
        for (int i = 0; i < lut.length; i++) {
            int val = i - black;
            val = Math.max(0, val);
            val *= mult;
            val = Math.min(255, val);
            lut[i] = (byte) val;
        }

        Gray dst = new Gray(size.width, size.height);
        srcData = getData();
        ByteBuffer dstData = dst.getData();
        while (srcData.hasRemaining()) {
            int p = srcData.get() & 0xff;
            dstData.put(lut[p]);
        }
        return dst;

    }

    public Gray scale(int w, int h) {
        return scaleBilinear(w, h);
    }

    public Gray scaleNN(int w, int h) {
        if (width == w && height == h)
            return this;
        ByteBuffer data = getData();
        ByteBuffer dest = ByteBuffer.allocate(w * h);
        float rw = (float) (1f / ((double) w / this.width));
        float rh = (float) (1f / ((double) h / this.height));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int xx = (int) (x * rw);
                int yy = (int) (y * rh);
                byte b = data.get(yy * width + xx);
                dest.put(b);
            }
        }
        return new Gray(w, h, dest);

    }

    public Gray scale(int w) {
        if (width == w)
            return this;
        double ratio = (double) w / this.width;
        int h = (int) Math.round(height * ratio);
        h &= ~1; // make h even
        return scaleBilinear(w, h);
    }

    /**
     * Bilinear resize grayscale image. Target dimension is w * h. Dimension cannot be null
     * 
     * @param w
     *            New width.
     * @param h
     *            New height.
     * @return the resized image.
     */
    public Gray scaleBilinear(int w, int h) {
        ByteBuffer srcPix = getData();
        Gray dst = new Gray(w, h);
        ByteBuffer dstPix = dst.getData();
        float x_ratio = ((float) (width - 1)) / w;
        float y_ratio = ((float) (height - 1)) / h;
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                int x = (int) (x_ratio * j);
                int y = (int) (y_ratio * i);
                float x_diff = (x_ratio * j) - x;
                float y_diff = (y_ratio * i) - y;
                int index = y * width + x;

                // range is 0 to 255 thus bitwise AND with 0xff
                int A = srcPix.get(index) & 0xff;
                int B = srcPix.get(index + 1) & 0xff;
                int C = srcPix.get(index + width) & 0xff;
                int D = srcPix.get(index + width + 1) & 0xff;

                // Y = A(1-w)(1-h) + B(w)(1-h) + C(h)(1-w) + Dwh
                int gray = (int) (A * (1 - x_diff) * (1 - y_diff) + B * (x_diff) * (1 - y_diff) + C * (y_diff)
                        * (1 - x_diff) + D * (x_diff * y_diff));

                dstPix.put((byte) gray);
            }
        }
        return dst;
    }

    public Gray getSubimage(Rectangle r) {
        if (r.equals(getBounds()))
            return this;
        return getSubimage(r.x, r.y, r.width, r.height, ByteBuffer.allocate(r.width * r.height));
    }

    public Gray getSubimage(int x, int y, int width, int height, ByteBuffer dest) {
        if (dest.capacity() < width * height) {
            throw new IllegalArgumentException("buffer capacity (" + (dest.capacity()) + ") < requested image size ("
                    + (width * height) + ")");
        }
        ByteBuffer data = getData();

        if (x == 0 && y == 0 && width == this.width && height == this.height) {
            return this;
        }
        if (x + width > this.width || y + height > this.height) {
            throw new IllegalArgumentException("image: " + this.width + "x" + this.height + " subimage: " + x + ":" + y
                    + " " + width + "x" + height);
        }
        int offset = y * this.width + x;
        for (int i = 0; i < height; i++) {
            data.limit(offset + width);
            data.position(offset);
            dest.put(data);
            offset += this.width;
        }
        return new Gray(width, height, dest);
    }

    /**
     * Extract a Gray from a BufferedImage by wrapping its underlying data.
     * @param b the BufferedImage to wrap
     * @return a grayscale image
     */
    public static Gray fromBufferedImage(BufferedImage b) {
        BufferedImage g = b;
        if (b.getType() != BufferedImage.TYPE_BYTE_GRAY) {
            g = new BufferedImage(b.getWidth(), b.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            g.getGraphics().drawImage(b, 0, 0, b.getWidth(), b.getHeight(), null);
        }
        byte[] data = ((DataBufferByte) g.getRaster().getDataBuffer()).getData();
        return new Gray(b.getWidth(), b.getHeight(), wrap(data));
    }

    /**
     * Convert this grayscale image to a BufferedImage.
     * @return the converted BufferedImage.
     */
    public BufferedImage toBufferedImage() {
        BufferedImage b = new BufferedImage(this.getWidth(), this.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        byte[] data = ((DataBufferByte) b.getRaster().getDataBuffer()).getData();
        this.getData().get(data);
        return b;
    }

    public static void writeBMP(Gray gray, String path) throws IOException {
        ImageIO.write(gray.toBufferedImage(), "bmp", new File(path));
    }

    public void writeBMP(String path) throws IOException {
        writeBMP(new File(path));
    }

    public static Gray read(String path) throws IOException {
        return read(new File(path));
    }

    public static String readLine(ByteBuffer in) {
        StringBuilder sb = new StringBuilder();
        int b;
        while (in.hasRemaining()) {
            b = in.get() & 0xff;
            if (b != '\n')
                sb.append((char) b);
            else
                break;
        }
        return sb.toString();
    }
    public static Gray read(File imageFile) throws IOException {
        return fromBufferedImage(ImageIO.read(imageFile));
    }

    public void writeBMP(File file) throws IOException {
        ImageIO.write(this.toBufferedImage(), "bmp", file);
    }

    public void writePNG(File file) throws IOException {
        ImageIO.write(this.toBufferedImage(), "png", file);
    }

}
