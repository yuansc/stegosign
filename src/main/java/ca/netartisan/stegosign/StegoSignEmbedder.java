/**
 * StegoSign - Steganographic Watermarking and Verification Tool
 * Copyright (C) 2025  Ling Hsiung Yuan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and   
 * limitations under the License.
 *
 * See the LICENSE file for more details.
 */
package ca.netartisan.stegosign;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

public class StegoSignEmbedder {

  /**
   * Applies a redundant, multi-scale, luminance-based watermark derived from a handwriting image.
   *
   * @param inputFile input PNG/JPEG
   * @param watermarkFile handwriting image (preferably black strokes on transparent/white)
   * @param outputFile output image file (png/jpg)
   * @param strength overall strength (0.0 - 1.0). Recommended 0.04 - 0.12.
   * @throws IOException on I/O error
   */
  public static void applyWatermark(
      File inputFile, File watermarkFile, File outputFile, float strength) throws IOException {
    BufferedImage src = ImageIO.read(inputFile);
    if (src == null) throw new IOException("Could not read input image: " + inputFile);

    // Work in ARGB to allow safe pixel edits
    BufferedImage working =
        new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = working.createGraphics();
    g.drawImage(src, 0, 0, null);
    g.dispose();

    BufferedImage watermarkImg = ImageIO.read(watermarkFile);
    if (watermarkImg == null)
      throw new IOException("Could not read watermark image: " + watermarkFile);

    // Strength -> base additive delta on luminance (0..255)
    // Tune: strength*80 gives e.g. strength=0.05 -> delta ~4.0
    final float baseDelta = Math.max(1.5f, strength * 80f);

    // Multi-scale redundant embedding (fractions of the full image)
    double[] scales = {1.0, 0.5, 0.25};

    int width = working.getWidth();
    int height = working.getHeight();

    for (double scale : scales) {
      int scaledW = Math.max(4, (int) Math.round(width * scale));
      int scaledH = Math.max(4, (int) Math.round(height * scale));

      float[][] mask = createMaskFromWatermark(watermarkImg, scaledW, scaledH);

      // Tile step with overlap (75% step -> 25% overlap)
      int stepX = Math.max(1, (int) (scaledW * 0.75));
      int stepY = Math.max(1, (int) (scaledH * 0.75));

      // A small offset per scale reduces perfect alignment
      int offsetX = (int) (scale * 7) % Math.max(1, stepX);
      int offsetY = (int) (scale * 11) % Math.max(1, stepY);

      for (int ty = -scaledH; ty < height + scaledH; ty += stepY) {
        for (int tx = -scaledW; tx < width + scaledW; tx += stepX) {
          int baseX = tx + offsetX;
          int baseY = ty + offsetY;

          // Apply mask onto working image by adding a small luminance delta
          for (int my = 0; my < scaledH; my++) {
            int py = baseY + my;
            if (py < 0 || py >= height) continue;
            for (int mx = 0; mx < scaledW; mx++) {
              int px = baseX + mx;
              if (px < 0 || px >= width) continue;
              float m = mask[my][mx]; // 0..1 (higher = darker stroke)
              if (m <= 1e-6f) continue;

              int rgb = working.getRGB(px, py);
              int a = (rgb >> 24) & 0xff;
              int r = (rgb >> 16) & 0xff;
              int gC = (rgb >> 8) & 0xff;
              int bC = rgb & 0xff;

              // delta added to luminance -> distribute into RGB according to luminance coefficients
              float delta = baseDelta * m; // small additive change
              int r2 = clampToByte((int) Math.round(r + delta * 0.299f));
              int g2 = clampToByte((int) Math.round(gC + delta * 0.587f));
              int b2 = clampToByte((int) Math.round(bC + delta * 0.114f));

              int newRgb = (a << 24) | (r2 << 16) | (g2 << 8) | b2;
              working.setRGB(px, py, newRgb);
            }
          }
        }
      }
    }

    // Store using appropriate format; use high-quality for JPEG
    saveImage(working, outputFile);
  }

  private static int clampToByte(int v) {
    if (v < 0) return 0;
    if (v > 255) return 255;
    return v;
  }

  /**
   * Creates a mask (height x width) from the provided watermark image scaled to target size. Mask ~
   * (alpha) * (1 - brightness) -> so dark strokes produce values near 1.
   */
  private static float[][] createMaskFromWatermark(
      BufferedImage watermark, int targetW, int targetH) {
    BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = scaled.createGraphics();
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(watermark, 0, 0, targetW, targetH, null);
    g.dispose();

    float[][] mask = new float[targetH][targetW];
    for (int y = 0; y < targetH; y++) {
      for (int x = 0; x < targetW; x++) {
        int rgba = scaled.getRGB(x, y);
        int a = (rgba >> 24) & 0xff;
        int r = (rgba >> 16) & 0xff;
        int gC = (rgba >> 8) & 0xff;
        int b = rgba & 0xff;
        // brightness 0..1
        float brightness = (r + gC + b) / (3.0f * 255f);
        float alpha = a / 255f;
        // If no alpha channel, alpha=1.0; dark pixels -> mask near 1
        float m = alpha * (1f - brightness);
        mask[y][x] = Math.max(0f, m);
      }
    }
    return mask;
  }

  private static void saveImage(BufferedImage img, File outFile) throws IOException {
    String name = outFile.getName().toLowerCase();
    if (name.endsWith(".png")) {
      ImageIO.write(img, "png", outFile);
    } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
      // Use ImageWriter to set high quality
      Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
      if (!writers.hasNext()) {
        // fallback
        ImageIO.write(img, "jpg", outFile);
        return;
      }
      ImageWriter writer = writers.next();
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(0.95f); // high quality
      try (ImageOutputStream ios = ImageIO.createImageOutputStream(outFile)) {
        writer.setOutput(ios);
        writer.write(null, new IIOImage(img, null, null), param);
        writer.dispose();
      }
    } else {
      // default to png if unknown
      ImageIO.write(img, "png", outFile);
    }
  }
}
