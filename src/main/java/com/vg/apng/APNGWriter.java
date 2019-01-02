package com.vg.apng;

import static com.vg.apng.APNG.IDAT_SIG;
import static com.vg.apng.APNG.IHDR_SIG;
import static com.vg.apng.APNG.PNG_SIG;
import static com.vg.apng.APNG.acTL_SIG;
import static com.vg.apng.APNG.fcTL_SIG;
import static com.vg.apng.APNG.fdAT_SIG;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Create APNG from grayscale images.
 *
 * @see APNGWriter#write(Gray[], File, int)
 * @see APNGWriter#write(Gray[], OutputStream, int)
 */
class APNGWriter {
    public static final byte ZERO = 0;

    public static final int CHUNK_DELTA =
            4 //chunk len
            + 4 //header
            + 4;//CRC

    public static final int acTL_DATA_LEN = 8;
    public static final int acTL_TOTAL_LEN = acTL_DATA_LEN + CHUNK_DELTA;

    public static final int IHDR_DATA_LEN = 13;
    public static final int IHDR_TOTAL_LEN = IHDR_DATA_LEN + CHUNK_DELTA;

    public static final int fcTL_DATA_LEN = 26;
    public static final int fcTL_TOTAL_LEN = fcTL_DATA_LEN + CHUNK_DELTA;

    private static final byte[] IEND_ARR = new byte[] {
            0,    0,   0,   0,
            'I', 'E', 'N', 'D',
            (byte) 0xae, 0x42,
            0x60, (byte) 0x82 //ae4260820
    };

    /**
     * Write an APNG image to a file.
     * @param grays the grayscale images to write
     * @param file the File to write to
     * @param loopCount the number of time to loop the animation (0 means infinite)
     * @throws IOException if the specified File is invalid
     */
    public void write(Gray[] grays, File file, int loopCount) throws IOException {
        write(grays, new FileOutputStream(file), loopCount);
    }

    /**
     * Write an APNG image to a FileOutputStream.
     * @param grays the grayscale images to write
     * @param os the OutputStream to write to
     * @param loopCount the number of time to loop the animation (0 means infinite)
     * @throws IOException if the specified OutputStream is invalid
     */
    public void write(Gray[] grays, OutputStream os, int loopCount) throws IOException {
        if (grays.length <= 0) {
            throw new RuntimeException("grays[] is empty");
        }

        WritableByteChannel out = Channels.newChannel(os);
        
        try {
            out.write(ByteBuffer.wrap(PNG_SIG));
            out.write(makeIHDRChunk(grays[0].width, grays[0].height));
            out.write(make_acTLChunk(grays.length, loopCount));

            for (int i = 0, seq = 0; i < grays.length; i++) {

                short[] delay = getFractionFromDelay(grays[i].getDelay());

                out.write(makeFCTL(grays[i].width, grays[i].height, seq++, delay[0], delay[1]));
                out.write(makeDAT(seq, i == 0, filterTypeNone(grays[i].width, grays[i].height, grays[i].getData())));

                if (i > 0) seq++;
            }

            out.write(ByteBuffer.wrap(IEND_ARR));
        } finally {
            out.close();
        }
    }
    
    /**
     * Credits to Joop Eggen from Stack Overflow.
     * @param delayms the delay to change into fraction
     * @return an array containing the numerator and the denominator
     * @see <a href="https://stackoverflow.com/a/31586500/8810915">https://stackoverflow.com/a/31586500/8810915</a>
     */
    private static short[] getFractionFromDelay(int delayms) {
        double x = delayms;
        x /= 1000;
        final double eps = 0.000_001;
        int pfound = (int) Math.round(x);
        int qfound = 1;
        double errorfound = Math.abs(x - pfound);
        double error = 1;
        for (int q = 2; q < 100 && error > eps; ++q) {
            int p = (int) (x * q);
            for (int i = 0; i < 2; ++i) { // below and above x
                error = Math.abs(x - ((double) p / q));
                if (error < errorfound) {
                    pfound = p;
                    qfound = q;
                    errorfound = error;
                }
                ++p;
            }
        }
        return new short[] { (short) pfound, (short) qfound };
    }

    private ByteBuffer makeIHDRChunk(int width, int height) { //http://www.w3.org/TR/PNG/#11IHDR
        ByteBuffer bb = ByteBuffer.allocate(IHDR_TOTAL_LEN);
        bb.putInt(IHDR_DATA_LEN);
        bb.putInt(IHDR_SIG);
        bb.putInt(width);
        bb.putInt(height);
        bb.put((byte) 8); // bits per plane
        bb.put(ZERO); //type Greyscale
        bb.put(ZERO); //compression
        bb.put(ZERO); //filter
        bb.put(ZERO); //interlace
        addChunkCRC(bb);
        bb.flip();
        return bb;
    }

    protected ByteBuffer make_acTLChunk(int frameCount, int loopCount) {
        ByteBuffer bb = ByteBuffer.allocate(acTL_TOTAL_LEN);
        bb.putInt(acTL_DATA_LEN);
        bb.putInt(acTL_SIG);
        bb.putInt(frameCount);
        bb.putInt(loopCount); // 0 : infinite
        addChunkCRC(bb);
        bb.flip();
        return bb;
    }

    private void addChunkCRC(ByteBuffer chunkBuffer) {
        if (chunkBuffer.remaining() != 4)           //CRC32 size 4
            throw new IllegalArgumentException();

        int size = chunkBuffer.position() - 4;

        if (size <= 0)
            throw new IllegalArgumentException();

        chunkBuffer.position(4);             //size not covered by CRC
        byte[] bytes = new byte[size];     // CRC covers only this
        chunkBuffer.get(bytes);
        chunkBuffer.putInt(crc(bytes));
    }

    private int crc(byte[] buf) {
        return crc(buf, 0, buf.length);
    }

    private int crc(byte[] buf, int off, int len) {
        CRC32 crc = new CRC32();
        crc.update(buf, off, len);
        return (int) crc.getValue();
    }

    private ByteBuffer makeFCTL(int width, int height, int seqNumber, short delay_num, short delay_den) {
        ByteBuffer bb = ByteBuffer.allocate(fcTL_TOTAL_LEN);

        bb.putInt(fcTL_DATA_LEN);
        bb.putInt(fcTL_SIG);

        bb.putInt(seqNumber);
        bb.putInt(width);
        bb.putInt(height);
        bb.putInt(0);               // x position
        bb.putInt(0);               // y position
        bb.putShort(delay_num);     // fps num
        bb.putShort(delay_den);     // fps den
        bb.put((byte) 1);           //dispose 1:clear, 0: do nothing, 2: revert
        bb.put(ZERO);               //blend   1:blend, 0: overwrite

        addChunkCRC(bb);

        bb.flip();

        return bb;
    }


    private ByteBuffer filterTypeNone(int width, int height, ByteBuffer in) {
        int size = in.remaining();
        ByteBuffer out = ByteBuffer.allocate(size + height);

        for (int lineOffset = 0; lineOffset < size; lineOffset += width) {
            out.put(ZERO); // "none" filter

            in.limit(lineOffset + width);
            in.position(lineOffset);
            out.put(in);
        }

        out.flip();

        return out;
    }

    private ByteBuffer makeDAT(int seqNumber, boolean idat, ByteBuffer buffer) {
        ByteBuffer compressed = compress(buffer, 9);

        int sig = idat ? IDAT_SIG : fdAT_SIG;
        boolean needSeqNum = !idat;

        int size = compressed.remaining();

        if (needSeqNum)
            size +=4;

        ByteBuffer bb = ByteBuffer.allocate(size + CHUNK_DELTA);

        bb.putInt(size);
        bb.putInt(sig);
        if (needSeqNum) {
            bb.putInt(seqNumber);
        }
        bb.put(compressed);

        addChunkCRC(bb);

        bb.flip();
        return bb;
    }

    private ByteBuffer compress(ByteBuffer in, int level) {
        int remaining = in.remaining();
        Deflater deflater = new Deflater(remaining > 42 ? level : 0);

        int size = remaining + 20;
        ByteArrayOutputStream baos = new ByteArrayOutputStream(size);
        DeflaterOutputStream dos = new DeflaterOutputStream(baos, deflater, 0x2000, false);
        WritableByteChannel wbc = Channels.newChannel(dos);
        try {
            wbc.write(in);
            dos.finish();
            dos.flush();
            dos.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        return ByteBuffer.wrap(baos.toByteArray());
    }
}
