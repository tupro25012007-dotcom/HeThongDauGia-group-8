package Shared.model;

import Shared.exception.AuctionException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;
import javax.imageio.*;

/** Downsample before decoding camera originals. Only bounded JPEGs cross the network. */
public final class ProductPhoto {
    private static final int SIDE = 1000;
    private static final int MAX_ENCODED = 2_000_000;
    private ProductPhoto() {}

    public static String prepare(File file) throws IOException {
        if (file.length() > 16 * 1024 * 1024) throw new IOException("Chọn ảnh nhỏ hơn 16 MB.");
        try (var input = ImageIO.createImageInputStream(file)) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Chọn ảnh JPEG hoặc PNG.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                var format = reader.getFormatName();
                if (!format.equalsIgnoreCase("jpeg") && !format.equalsIgnoreCase("png"))
                    throw new IOException("Chọn ảnh JPEG hoặc PNG.");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                int sample = Math.max(1, Math.max(width, height) / SIDE);
                var params = reader.getDefaultReadParam();
                params.setSourceSubsampling(sample, sample, 0, 0);
                BufferedImage original = reader.read(0, params);
                double scale = Math.min(1, SIDE / (double)Math.max(original.getWidth(), original.getHeight()));
                var rgb = new BufferedImage(Math.max(1, (int)(original.getWidth()*scale)),
                        Math.max(1, (int)(original.getHeight()*scale)), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                try {
                    g.setColor(Color.WHITE); g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(original, 0, 0, rgb.getWidth(), rgb.getHeight(), null);
                } finally { g.dispose(); }
                var out = new ByteArrayOutputStream();
                ImageIO.write(rgb, "jpeg", out);
                return Base64.getEncoder().encodeToString(out.toByteArray());
            } finally { reader.dispose(); }
        }
    }
    public static void validate(String encoded) {
        try {
            if (encoded.length() > MAX_ENCODED) throw new IOException();
            byte[] bytes = Base64.getDecoder().decode(encoded);
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IOException();
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    if (!reader.getFormatName().equalsIgnoreCase("jpeg") || reader.getWidth(0) > SIDE || reader.getHeight(0) > SIDE)
                        throw new IOException();
                    reader.read(0);
                } finally { reader.dispose(); }
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new AuctionException("Ảnh sản phẩm không hợp lệ. Hãy chọn lại ảnh.");
        }
    }
}
