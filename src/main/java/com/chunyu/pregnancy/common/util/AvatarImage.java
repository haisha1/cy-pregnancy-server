package com.chunyu.pregnancy.common.util;

import com.chunyu.pregnancy.common.exception.BizException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

/** 图片只接受JPEG/PNG，先检查尺寸再解码，重编码移除EXIF等元数据和附带内容。 */
public final class AvatarImage {
  private AvatarImage() {}

  public static byte[] normalize(byte[] bytes) {
    if (bytes.length == 0 || bytes.length > 2 * 1024 * 1024) throw invalid();
    try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw invalid();
      ImageReader reader = readers.next();
      try {
        String format = reader.getFormatName();
        if (!"JPEG".equalsIgnoreCase(format) && !"PNG".equalsIgnoreCase(format)) throw invalid();
        reader.setInput(input, true, true);
        int w = reader.getWidth(0), h = reader.getHeight(0);
        if (w < 1 || h < 1 || w > 4096 || h > 4096 || (long) w * h > 16000000) throw invalid();
        BufferedImage source = reader.read(0);
        double scale = Math.min(1.0, 256.0 / Math.max(w, h));
        BufferedImage target =
            new BufferedImage(
                Math.max(1, (int) (w * scale)),
                Math.max(1, (int) (h * scale)),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
          graphics.setColor(Color.WHITE);
          graphics.fillRect(0, 0, target.getWidth(), target.getHeight());
          graphics.setRenderingHint(
              RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
          graphics.drawImage(source, 0, 0, target.getWidth(), target.getHeight(), null);
        } finally {
          graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(target, "jpeg", output) || output.size() > 262144) throw invalid();
        return output.toByteArray();
      } finally {
        reader.dispose();
      }
    } catch (IOException ex) {
      throw invalid();
    }
  }

  private static BizException invalid() {
    return new BizException(422, "AVATAR_INVALID", "请选择2MB以内、边长不超过4096像素的JPEG或PNG头像");
  }
}
