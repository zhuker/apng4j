package com.vg.apng;

import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

public class APNGTest {
    @Test
    public void testReadWrite() throws IOException {
        byte x = (byte) 255;
        byte[] a0 = {
                0, x, 0,
                x, 0, x,
                0, x, 0 };
        byte[] a1 = {
                0, x, 0,
                0, x, 0,
                0, x, 0,
                0, x, 0 };
        byte[] a2 = {
                x, 0, x,
                x, 0, x,
                x, 0, x };

        Gray[] g = new Gray[] {
                new Gray(3, 3, a0, APNG.DELAY_1S),
                new Gray(3, 4, a1, APNG.DELAY_1S),
                new Gray(3, 3, a2, APNG.DELAY_1S)};

        File file = new File("ab.png");
        file.deleteOnExit();
        APNG.write(g, file, APNG.INFINITE_LOOP);
        Gray[] gr = APNG.read(file);

        Assert.assertEquals(3, gr.length);
        Assert.assertArrayEquals(a0, gr[0].data.array());
        Assert.assertArrayEquals(a1, gr[1].data.array());
        Assert.assertArrayEquals(a2, gr[2].data.array());
    }
}
