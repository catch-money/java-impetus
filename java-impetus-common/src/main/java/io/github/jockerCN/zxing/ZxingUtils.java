package io.github.jockerCN.zxing;


import com.google.common.collect.Maps;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import io.github.jockerCN.Result;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Objects;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@SuppressWarnings("unused")
public class ZxingUtils {

    /**
     * 二维码颜色
     */
    public static final int BLACK = 0xFF000000;
    /**
     * 二维码颜色
     */
    public static final int WHITE = 0xFFFFFFFF;


    /**
     * 找出二维码的实际边界，去除空白部分
     *
     * @param bitMatrix 二维码BitMatrix
     * @return 边界坐标 [minX, minY, maxX, maxY]
     */
    private static int[] findQRCodeBoundary(BitMatrix bitMatrix) {
        int width = bitMatrix.getWidth();
        int height = bitMatrix.getHeight();

        int minX = width, minY = height, maxX = 0, maxY = 0;

        // 扫描找出二维码的实际边界
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (bitMatrix.get(x, y)) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        return new int[]{minX, minY, maxX, maxY};
    }

    /**
     * 创建条形码BitMatrix
     *
     * @param content 条形码内容
     * @param width 条形码宽度
     * @param height 条形码高度
     * @return BitMatrix对象
     * @throws WriterException 编码异常
     */
    public static BitMatrix createBarcodeBitMatrix(String content, int width, int height) throws WriterException {
        HashMap<EncodeHintType, Object> hints = Maps.newHashMap();
        // 编码
        hints.put(EncodeHintType.CHARACTER_SET, "utf-8");
        // 边框
        hints.put(EncodeHintType.MARGIN, 2);
        return new MultiFormatWriter().encode(content, BarcodeFormat.CODE_128, width, height, hints);
    }

    /**
     * 创建条形码
     *
     * @param content 条形码内容
     * @param width 条形码宽度
     * @param height 条形码高度
     * @param isCrop 是否裁剪空白部分
     * @return 条形码图片结果
     */
    public static Result<BufferedImage> createBarcode(String content, int width, int height, boolean isCrop) {
        try {
            BitMatrix bitMatrix = createBarcodeBitMatrix(content, width, height);
            if (Objects.nonNull(bitMatrix)) {
                // 获取宽度
                int barcodeWidth = bitMatrix.getWidth();
                // 获取高度
                int barcodeHeight = bitMatrix.getHeight();
                // 将条形码放入缓冲流
                BufferedImage image = new BufferedImage(barcodeWidth, barcodeHeight, BufferedImage.TYPE_INT_BGR);
                for (int i = 0; i < barcodeWidth; i++) {
                    for (int j = 0; j < barcodeHeight; j++) {
                        // 循环将条形码内容写入图片
                        image.setRGB(i, j, bitMatrix.get(i, j) ? BLACK : WHITE);
                    }
                }

                if (isCrop) {
                    int[] boundary = findImageBoundary(image);
                    int minX = boundary[0];
                    int minY = boundary[1];
                    int maxX = boundary[2];
                    int maxY = boundary[3];

                    int actualWidth = maxX - minX + 1;
                    int actualHeight = maxY - minY + 1;

                    // 对实际条形码部分进行裁剪
                    image = image.getSubimage(minX, minY, actualWidth, actualHeight);
                }
                return Result.ok(image);
            }
            return Result.failWithMsg("create barCode failed");
        } catch (WriterException e) {
            log.error("create barCode failed", e);
            return Result.failWithMsg("create barCode failed" + e.getMessage());
        }
    }


    public static BitMatrix createQRBitMatrix(String content, int width, int height) throws WriterException {
        HashMap<EncodeHintType, ? super Object> hints = Maps.newHashMap();
        // 编码
        hints.put(EncodeHintType.CHARACTER_SET, "utf-8");
        // 容错等级
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        // 边框
        hints.put(EncodeHintType.MARGIN, 2);
        return new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, width, height, hints);
    }

    public static BitMatrix cropBitMatrix(BitMatrix bitMatrix) {
        int[] boundary = findQRCodeBoundary(bitMatrix);
        int minX = boundary[0], minY = boundary[1], maxX = boundary[2], maxY = boundary[3];
        // 计算有效二维码的宽度和高度
        int qrWidth = maxX - minX + 1;
        int qrHeight = maxY - minY + 1;
        // 创建正好包含二维码的新BitMatrix（无空白边距）
        BitMatrix croppedMatrix = new BitMatrix(qrWidth, qrHeight);
        for (int y = 0; y < qrHeight; y++) {
            for (int x = 0; x < qrWidth; x++) {
                if (bitMatrix.get(x + minX, y + minY)) {
                    croppedMatrix.set(x, y);
                }
            }
        }
        return croppedMatrix;
    }

    /**
     * 查找图片中非白色区域的边界
     *
     * @param image 输入图片
     * @return 边界数组 [minX, minY, maxX, maxY]
     */
    private static int[] findImageBoundary(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        int minX = width;
        int minY = height;
        int maxX = 0;
        int maxY = 0;

        // 扫描图片找出非白色区域
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                // 检查是否为非白色像素
                if ((rgb & 0x00FFFFFF) != 0x00FFFFFF) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        return new int[]{minX, minY, maxX, maxY};
    }



    public static Result<BufferedImage> createQR(String content, int width, int height, boolean isCrop) {
        try {
            BitMatrix bitMatrix = createQRBitMatrix(content, width, height);
            if (Objects.nonNull(bitMatrix)) {
                // 获取宽度
                int qrWidth = bitMatrix.getWidth();
                // 获取高度
                int qrHeight = bitMatrix.getHeight();
                // 将二维码放入缓冲流
                BufferedImage image = new BufferedImage(qrWidth, qrHeight, BufferedImage.TYPE_INT_BGR);
                for (int i = 0; i < qrWidth; i++) {
                    for (int j = 0; j < qrHeight; j++) {
                        // 循环将二维码内容写入图片
                        image.setRGB(i, j, bitMatrix.get(i, j) ? BLACK : WHITE);
                    }
                }

                if (isCrop) {
                    int[] boundary = findImageBoundary(image);
                    int minX = boundary[0];
                    int minY = boundary[1];
                    int maxX = boundary[2];
                    int maxY = boundary[3];

                    int qrActualWidth = maxX - minX + 1;
                    int qrActualHeight = maxY - minY + 1;

                    // 对实际二维码部分进行裁剪
                    image = image.getSubimage(minX, minY, qrActualWidth, qrActualHeight);
                }
                return Result.ok(image);
            }
            return Result.failWithMsg("create QR failed");
        } catch (WriterException e) {
            log.error("create QR failed", e);
            return Result.failWithMsg("create QR failed" + e.getMessage());
        }
    }
}
