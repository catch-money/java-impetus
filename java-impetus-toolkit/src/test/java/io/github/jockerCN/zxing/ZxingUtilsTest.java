package io.github.jockerCN.zxing;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import io.github.jockerCN.Result;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZxingUtilsTest {

    @Test
    void generatedQrCodeCanBeDecoded() throws NotFoundException {
        String content = "https://example.com/toolkit";
        Result<BufferedImage> result = ZxingUtils.createQR(content, 300, 300, false);

        assertTrue(result.isOk());
        assertEquals(content, decode(result.getData()));
    }

    @Test
    void generatedBarcodeCanBeDecoded() throws NotFoundException {
        String content = "1234567890";
        Result<BufferedImage> result = ZxingUtils.createBarcode(content, 400, 120, false);

        assertTrue(result.isOk());
        assertEquals(content, decode(result.getData()));
    }

    private static String decode(BufferedImage image) throws NotFoundException {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
        return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText();
    }
}
