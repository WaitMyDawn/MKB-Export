package yagen.waitmydawn.mkb_export.core;

import java.io.*;
import javax.imageio.ImageIO;

/**
 * 完全透明属于渲染结果，只有解码失败、尺寸错误或缺少 Alpha 才是无效图像。
 */
public final class IconPng {
    private IconPng() {
    }

    public static String status(byte[] bytes, int size) throws IOException {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Invalid PNG");
            var reader = readers.next();
            try {
                reader.setInput(input);
                if (!reader.getFormatName().equalsIgnoreCase("png") || reader.getWidth(0) != size || reader.getHeight(0) != size)
                    throw new IOException("Invalid PNG dimensions");
                var image = reader.read(0);
                if (image == null || !image.getColorModel().hasAlpha()) throw new IOException("Missing alpha");
                for (int y = 0; y < size; y++)
                    for (int x = 0; x < size; x++)
                        if ((image.getRGB(x, y) >>> 24) != 0) return "OK";
                return "TRANSPARENT";
            } finally {
                reader.dispose();
            }
        }
    }
}