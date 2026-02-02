package com.davixdevelop.worldshifter.utils;

import io.github.ensgijs.nbt.mca.util.VersionAware;
import io.github.ensgijs.nbt.query.NbtPath;

import java.io.*;

public class Utils {
    private static final VersionAware<NbtPath> ENTITIES_BRAIN_MEMORIES_PATH = new VersionAware<NbtPath>()
            .register(0, NbtPath.of("Brain.memories"));

    public static void copyFile(File source, File target) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(target);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }

            is.close();
            os.close();

        } catch (Exception ex) {
            throw ex;
        }
    }

    public static int[] decodeDataLongArray(long[] data, int bits_per_value) {
        int[] res = new int[256];

        int values_per_long = 64 / bits_per_value;

        int expected_data_length = (256 + values_per_long - 1) / values_per_long;
        if(data.length != expected_data_length) {
            bits_per_value = (64 * (data.length  - 1))/255;
            values_per_long = 64 / bits_per_value;
        }

        int mask = (1 << bits_per_value) - 1;

        for(int i = 0; i < 256; i++) {
            int long_index = i / values_per_long;
            int bit_index = (i % values_per_long) * bits_per_value;
            res[i] = (int)((data[long_index] >> bit_index) & mask);
        }

        return res;
    }

    public static long[] encodeDataLongArray(int[] data, int bits_per_value) {
        int values_per_long = 64 / bits_per_value;

        int data_length = (256 + values_per_long - 1) / values_per_long;
        long[] res = new long[data_length];


        long mask = (1L << bits_per_value) - 1;

        for(int i = 0; i < 256; i++) {
            int long_index = i / values_per_long;
            int bit_index = (i % values_per_long) * bits_per_value;

            res[long_index] &= ~(mask << bit_index);
            res[long_index] |= (long) data[i] << bit_index;
        }

        return res;
    }
}
