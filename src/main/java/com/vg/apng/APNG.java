package com.vg.apng;

import java.io.File;
import java.io.IOException;

public class APNG {
    public static final int IHDR_SIG = 0x49484452;
    public static final int acTL_SIG = 0x6163544c;
    public static final int IDAT_SIG = 0x49444154;
    public static final int fdAT_SIG = 0x66644154;
    public static final int fcTL_SIG = 0x6663544c;
    public static final int IEND_SIG = 0x49454e44;

    public static final byte[] PNG_SIG = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}; //http://www.w3.org/TR/PNG/#5PNG-file-signature

    public static void write(Gray[] grays, File file) throws IOException {
        new APNGWriter().write(grays, file);
    }

    public static Gray[] read(File file) throws IOException {
        return new APNGReader().read(file);
    }
}