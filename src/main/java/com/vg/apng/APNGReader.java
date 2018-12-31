package com.vg.apng;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static com.vg.apng.APNG.*;

class APNGReader {

    public Gray[] read(File file) throws IOException {
        ByteBuffer in = readFile(file);
        in.position(PNG_SIG.length);
        int frameCount = nextACTL(in).frameCount;

        Gray[] grays = new Gray[frameCount];

        for (int i = 0; i < frameCount; i++) {
            grays[i] = readFrame(in);
        }

        return grays;
    }

    private Gray readFrame(ByteBuffer in) {
        FCTL fctl = nextFCTL(in);
        ByteBuffer data = nextData(in);
        ByteBuffer decompressed = decompress(data);
        ByteBuffer pixels = unfilter(fctl.width, fctl.height, decompressed);
        return new Gray(fctl.width, fctl.height, pixels);
    }

    private ByteBuffer unfilter(int width, int height, ByteBuffer data) {
        ByteBuffer pixels = ByteBuffer.allocate(width * height);

        while (data.hasRemaining()) {
            int filterType = data.get();
            if (filterType != 0) {
                throw new RuntimeException("unsupported filter type " + filterType);
            }

            data.limit(data.position() + width); // one line
            pixels.put(data);
            data.limit(data.capacity());
        }

        return pixels;
    }

    private ByteBuffer decompress(ByteBuffer data) {
        Inflater inflater = new Inflater();
        inflater.setInput(data.array(), data.arrayOffset() + data.position(), data.remaining());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (! inflater.finished()) {
            try {
                int count = inflater.inflate(buffer);
                out.write(buffer, 0, count);
            } catch (DataFormatException e) {
                throw new RuntimeException(e);
            }
        }

        inflater.end();

        return ByteBuffer.wrap(out.toByteArray());
    }

    private ByteBuffer readFile(File file) throws IOException {
        ByteBuffer in = ByteBuffer.allocate((int) file.length());

        ReadableByteChannel ch = new FileInputStream(file).getChannel();
        try {
            ch.read(in);
        } finally {
            ch.close();
        }

        in.flip();

        return in;
    }

    private ACTL nextACTL(ByteBuffer in) {
        while (true) {
            int chunkLen = in.getInt();
            int sig = in.getInt();

            if (sig == acTL_SIG) {
                int frameCount = in.getInt();
                in.getInt(); // loop count
                in.getInt(); // crc
                return new ACTL(frameCount);
            }

            in.position(in.position() + chunkLen + 4);
        }
    }

    private FCTL nextFCTL(ByteBuffer in) {
        in.getInt(); // chunkLen
        int sig = in.getInt();

        if (sig != fcTL_SIG) {
            throw new RuntimeException("fcTL expected but not found");
        }

        in.getInt();        // seqNumber
        int width = in.getInt();
        int height = in.getInt();
        in.getInt();        // x position
        in.getInt();        // y position
        in.getShort();      // fps num
        in.getShort();      // fps den
        in.get();            // dispose 1:clear, 0: do nothing, 2: revert
        in.get();            // blend   1:blend, 0: overwrite
        in.getInt();        // crc

        return new FCTL(width, height);
    }

    private ByteBuffer nextData(ByteBuffer in) {
        int chunkLen = in.getInt();
        int sig = in.getInt();
        int dataSize = chunkLen;

        if (sig == fdAT_SIG) {
            in.getInt(); // seqNumber
            dataSize -= 4;
        } else if (sig != IDAT_SIG) {
            throw new RuntimeException("fdAT or IDAT expected but not found");
        }

        ByteBuffer data = in.duplicate();
        data.limit(in.position() + dataSize);

        in.position(in.position() + dataSize + 4);

        return data;
    }

    private static class ACTL {
        public final int frameCount;

        public ACTL(int frameCount) {
            this.frameCount = frameCount;
        }
    }

    private static class FCTL {
        public final int width;
        public final int height;

        public FCTL(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
