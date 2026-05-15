package sibarum.dasum.gui.core.render;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Minimal PNG decoder sufficient for msdf-atlas-gen outputs: 8-bit RGB
 * (color type 2) and 8-bit RGBA (color type 6), no interlacing, adaptive
 * filtering. Always returns RGBA byte data (alpha = 255 when the source
 * has no alpha channel).
 * <p>
 * Written to avoid {@code javax.imageio.ImageIO}, whose static initializer
 * touches {@code java.awt.Toolkit} — unsupported under native-image. Uses
 * only {@code java.util.zip.Inflater} and basic I/O, both of which work
 * cleanly in native-image.
 */
public final class PngDecoder {

    public record DecodedImage(int width, int height, byte[] rgba) {}

    private static final byte[] PNG_SIG = {
        (byte) 0x89, 'P', 'N', 'G', '\r', '\n', (byte) 0x1A, '\n'
    };

    private PngDecoder() {}

    public static DecodedImage decode(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        byte[] sig = new byte[8];
        dis.readFully(sig);
        for (int i = 0; i < 8; i++) {
            if (sig[i] != PNG_SIG[i]) throw new IOException("Not a PNG (bad signature)");
        }

        int width = -1, height = -1, bitDepth = -1, colorType = -1, interlace = -1;
        ByteArrayOutputStream idat = new ByteArrayOutputStream();

        while (true) {
            int length = dis.readInt();
            int type = dis.readInt();
            byte[] data = new byte[length];
            dis.readFully(data);
            dis.readInt(); // CRC — not validated here; PNG decoders typically can elide

            if (type == chunkType("IHDR")) {
                width     = readBeInt(data, 0);
                height    = readBeInt(data, 4);
                bitDepth  = data[8] & 0xFF;
                colorType = data[9] & 0xFF;
                int compression = data[10] & 0xFF;
                int filter      = data[11] & 0xFF;
                interlace = data[12] & 0xFF;
                if (compression != 0) throw new IOException("Unsupported PNG compression method: " + compression);
                if (filter != 0)      throw new IOException("Unsupported PNG filter method: " + filter);
                if (bitDepth != 8)    throw new IOException("Unsupported PNG bit depth: " + bitDepth + " (only 8 supported)");
                if (colorType != 2 && colorType != 6) {
                    throw new IOException("Unsupported PNG color type: " + colorType + " (only 2=RGB, 6=RGBA supported)");
                }
                if (interlace != 0)   throw new IOException("Interlaced PNGs not supported");
            } else if (type == chunkType("IDAT")) {
                idat.write(data, 0, data.length);
            } else if (type == chunkType("IEND")) {
                break;
            }
            // All other chunks ignored (sBIT, gAMA, tEXt, etc.)
        }

        if (width < 0) throw new IOException("PNG missing IHDR");

        int srcBpp = (colorType == 6) ? 4 : 3; // bytes per pixel in source
        int srcStride = width * srcBpp;
        int filteredStride = srcStride + 1; // +1 filter byte per row

        byte[] inflated = inflate(idat.toByteArray(), filteredStride * height);
        byte[] reconstructed = defilter(inflated, width, height, srcBpp);
        byte[] rgba = (colorType == 6) ? reconstructed : expandRgbToRgba(reconstructed, width, height);

        return new DecodedImage(width, height, rgba);
    }

    private static byte[] inflate(byte[] data, int expectedSize) throws IOException {
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        byte[] out = new byte[expectedSize];
        int total = 0;
        try {
            while (!inflater.finished() && total < expectedSize) {
                int n = inflater.inflate(out, total, expectedSize - total);
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        throw new IOException("Inflater needs more input but IDAT stream is exhausted");
                    }
                    break;
                }
                total += n;
            }
        } catch (DataFormatException e) {
            throw new IOException("PNG IDAT inflate failed: " + e.getMessage(), e);
        } finally {
            inflater.end();
        }
        if (total != expectedSize) {
            throw new IOException("Inflate produced " + total + " bytes, expected " + expectedSize);
        }
        return out;
    }

    private static byte[] defilter(byte[] filtered, int width, int height, int bpp) throws IOException {
        int rowBytes = width * bpp;
        int filteredStride = rowBytes + 1;
        byte[] out = new byte[rowBytes * height];

        for (int y = 0; y < height; y++) {
            int filterType = filtered[y * filteredStride] & 0xFF;
            int filteredRow = y * filteredStride + 1;
            int outRow = y * rowBytes;
            int outPrevRow = (y == 0) ? -1 : (y - 1) * rowBytes;

            for (int x = 0; x < rowBytes; x++) {
                int filt = filtered[filteredRow + x] & 0xFF;
                int a = (x >= bpp) ? (out[outRow + x - bpp] & 0xFF) : 0;
                int b = (outPrevRow >= 0) ? (out[outPrevRow + x] & 0xFF) : 0;
                int c = (outPrevRow >= 0 && x >= bpp) ? (out[outPrevRow + x - bpp] & 0xFF) : 0;

                int recon = switch (filterType) {
                    case 0 -> filt;                          // None
                    case 1 -> filt + a;                      // Sub
                    case 2 -> filt + b;                      // Up
                    case 3 -> filt + (a + b) / 2;            // Average
                    case 4 -> filt + paeth(a, b, c);         // Paeth
                    default -> throw new IOException("Invalid PNG filter type: " + filterType + " at row " + y);
                };
                out[outRow + x] = (byte) (recon & 0xFF);
            }
        }
        return out;
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc) return a;
        if (pb <= pc) return b;
        return c;
    }

    private static byte[] expandRgbToRgba(byte[] rgb, int width, int height) {
        byte[] rgba = new byte[width * height * 4];
        for (int i = 0, j = 0; i < rgb.length; i += 3, j += 4) {
            rgba[j    ] = rgb[i    ];
            rgba[j + 1] = rgb[i + 1];
            rgba[j + 2] = rgb[i + 2];
            rgba[j + 3] = (byte) 0xFF;
        }
        return rgba;
    }

    private static int readBeInt(byte[] data, int off) {
        return ((data[off] & 0xFF) << 24)
             | ((data[off + 1] & 0xFF) << 16)
             | ((data[off + 2] & 0xFF) << 8)
             |  (data[off + 3] & 0xFF);
    }

    private static int chunkType(String s) {
        return (s.charAt(0) << 24) | (s.charAt(1) << 16) | (s.charAt(2) << 8) | s.charAt(3);
    }
}
