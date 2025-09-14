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
import javax.imageio.ImageIO;

public class StegoSignExtractor {

  /**
   * Extracts a visual watermark pattern from a suspect image given the original watermark image.
   *
   * @param suspectFile the image suspected of containing the watermark
   * @param watermarkFile the original watermark.png
   * @param outputFile where to save extracted visualization (PNG recommended)
   * @throws IOException on read/write error
   */
  public static void extractWatermark(File suspectFile, File watermarkFile, File outputFile)
      throws IOException {
    BufferedImage suspect = ImageIO.read(suspectFile);
    if (suspect == null) throw new IOException("Cannot read suspect image: " + suspectFile);

    BufferedImage watermark = ImageIO.read(watermarkFile);
    if (watermark == null) throw new IOException("Cannot read watermark image: " + watermarkFile);

    int width = suspect.getWidth();
    int height = suspect.getHeight();

    // luminance array of suspect image
    double[][] lum = new double[height][width];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int rgb = suspect.getRGB(x, y);
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        lum[y][x] = 0.299 * r + 0.587 * g + 0.114 * b;
      }
    }

    // build composite reference mask with multi-scale redundant pattern
    double[][] ref = new double[height][width];
    double[] scales = {1.0, 0.5, 0.25};

    for (double s : scales) {
      int scaledW = Math.max(4, (int) Math.round(width * s));
      int scaledH = Math.max(4, (int) Math.round(height * s));
      float[][] mask = createMaskFromWatermark(watermark, scaledW, scaledH);

      int stepX = Math.max(1, (int) (scaledW * 0.75));
      int stepY = Math.max(1, (int) (scaledH * 0.75));
      int offsetX = (int) (s * 7) % Math.max(1, stepX);
      int offsetY = (int) (s * 11) % Math.max(1, stepY);

      for (int ty = -scaledH; ty < height + scaledH; ty += stepY) {
        for (int tx = -scaledW; tx < width + scaledW; tx += stepX) {
          int baseX = tx + offsetX;
          int baseY = ty + offsetY;
          for (int my = 0; my < scaledH; my++) {
            int py = baseY + my;
            if (py < 0 || py >= height) continue;
            for (int mx = 0; mx < scaledW; mx++) {
              int px = baseX + mx;
              if (px < 0 || px >= width) continue;
              float m = mask[my][mx];
              if (m <= 1e-6f) continue;
              ref[py][px] += m;
            }
          }
        }
      }
    }

    // Normalize ref and lum
    normalize(ref);
    normalize(lum);

    // Compute difference map (lum correlated with ref)
    BufferedImage extracted = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        double value = ref[y][x] * lum[y][x];
        int gray = clamp((int) Math.round(255 * value));
        int argb = (0xff << 24) | (gray << 16) | (gray << 8) | gray;
        extracted.setRGB(x, y, argb);
      }
    }

    ImageIO.write(extracted, "png", outputFile);
  }

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
        float brightness = (r + gC + b) / (3.0f * 255f);
        float alpha = a / 255f;
        float m = alpha * (1f - brightness);
        mask[y][x] = Math.max(0f, m);
      }
    }
    return mask;
  }

  private static void normalize(double[][] arr) {
    double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
    for (double[] row : arr) {
      for (double v : row) {
        if (v < min) min = v;
        if (v > max) max = v;
      }
    }
    double range = max - min;
    if (range < 1e-12) return;
    for (int y = 0; y < arr.length; y++) {
      for (int x = 0; x < arr[0].length; x++) {
        arr[y][x] = (arr[y][x] - min) / range;
      }
    }
  }

  private static int clamp(int v) {
    return v < 0 ? 0 : Math.min(255, v);
  }
}
