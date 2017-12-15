package com.vg.apng;

import static com.vg.apng.APNG.IDAT_SIG;
import static com.vg.apng.APNG.IHDR_SIG;
import static com.vg.apng.APNG.PNG_SIG;
import static com.vg.apng.APNG.acTL_SIG;
import static com.vg.apng.APNG.fcTL_SIG;
import static com.vg.apng.APNG.fdAT_SIG;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

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
            0,	  0,   0,   0,
            'I', 'E', 'N', 'D',
            (byte) 0xae, 0x42,
            0x60, (byte) 0x82 //ae4260820
    };

    public void write(Gray[] grays, File file) throws IOException {
        if (grays.length <= 0) {
            throw new RuntimeException("grays[] is empty");
        }

        WritableByteChannel out = new FileOutputStream(file).getChannel();
        try {
            out.write(ByteBuffer.wrap(PNG_SIG));
            out.write(makeIHDRChunk(grays[0].width, grays[0].height));
            out.write(make_acTLChunk(grays.length));

            for (int i = 0, seq = 0; i < grays.length; i++) {
                Gray gray = grays[i];
                out.write(makeFCTL(gray.width, gray.height, seq++));
                out.write(makeDAT(seq, i == 0, filterTypeNone(gray.width, gray.height, grays[i].getData())));
                if (i > 0) seq++;
            }

            out.write(ByteBuffer.wrap(IEND_ARR));
        } finally {
            out.close();
        }
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

    protected ByteBuffer make_acTLChunk(int frameCount) {
        ByteBuffer bb = ByteBuffer.allocate(acTL_TOTAL_LEN);
        bb.putInt(acTL_DATA_LEN);
        bb.putInt(acTL_SIG);
        bb.putInt(frameCount);
        bb.putInt(0);           // loop count, zero=infinity
        addChunkCRC(bb);
        bb.flip();
        return bb;
    }

    private void addChunkCRC(ByteBuffer chunkBuffer) {
        if (chunkBuffer.remaining() != 4)			//CRC32 size 4
            throw new IllegalArgumentException();

        int size = chunkBuffer.position() - 4;

        if (size <= 0)
            throw new IllegalArgumentException();

        chunkBuffer.position(4);			 //size not covered by CRC
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

    private ByteBuffer makeFCTL(int width, int height, int seqNumber) {
        ByteBuffer bb = ByteBuffer.allocate(fcTL_TOTAL_LEN);

        bb.putInt(fcTL_DATA_LEN);
        bb.putInt(fcTL_SIG);

        bb.putInt(seqNumber);
        bb.putInt(width);
        bb.putInt(height);
        bb.putInt(0);               // x position
        bb.putInt(0);               // y position
        bb.putShort((short) 10);     // fps num
        bb.putShort((short) 10);     // fps den
        bb.put((byte) 1);  	        //dispose 1:clear, 0: do nothing, 2: revert
        bb.put(ZERO);           	//blend   1:blend, 0: overwrite

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
