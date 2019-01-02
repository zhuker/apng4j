package com.vg.apng;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * The library main class. Contains static utility methods to read and write Animated PNG.
 *
 * @see <a href="https://developer.mozilla.org/en-US/docs/Mozilla/Tech/APNG">APNG specification</a>
 */
public class APNG {
    public static final int IHDR_SIG = 0x49484452;
    public static final int acTL_SIG = 0x6163544c;
    public static final int IDAT_SIG = 0x49444154;
    public static final int fdAT_SIG = 0x66644154;
    public static final int fcTL_SIG = 0x6663544c;
    public static final int IEND_SIG = 0x49454e44;

    public static final byte[] PNG_SIG = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}; //http://www.w3.org/TR/PNG/#5PNG-file-signature

    // Predefined delay values
    public static final int DELAY_500MS = 500;
    public static final int DELAY_1S = 1000;
    public static final int DELAY_250MS = 250;
    public static final int DELAY_100MS = 100;

    public static final int INFINITE_LOOP = 0;

    /**
     * Write an APNG image to a file. Basically a shortcut to {@link APNGWriter#write(Gray[], File, int)}.
     * @param grays the grayscale images to write
     * @param file the File to write to
     * @param loopCount the number of time to loop the animation (0 means infinite)
     * @throws IOException if the specified File is invalid
     */
    public static void write(Gray[] grays, File file, int loopCount) throws IOException {
        new APNGWriter().write(grays, file, loopCount);
    }

    /**
     * Write an APNG image to a FileOutputStream. Basically a shortcut to {@link APNGWriter#write(Gray[], OutputStream, int)}.
     * @param grays the grayscale images to write
     * @param os the FileOutputStream to write to
     * @param loopCount the number of time to loop the animation (0 means infinite)
     * @throws IOException if the specified FileOutputStream is invalid
     */
    public static void write(Gray[] grays, OutputStream os, int loopCount) throws IOException {
        new APNGWriter().write(grays, os, loopCount);
    }

    /**
     * Read an APNG from a File. Basically a shortcut to {@link APNGReader#read(File)}.
     * @param file the File to read
     * @return an array of grayscale images
     * @throws IOException if the specified File is invalid
     */
    public static Gray[] read(File file) throws IOException {
        return new APNGReader().read(file);
    }
}