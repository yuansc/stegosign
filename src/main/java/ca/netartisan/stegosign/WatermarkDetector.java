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

public class WatermarkDetector {

  /**
   * Returns a correlation score (higher = stronger evidence watermark present). Use same scales as
   * embedder for best result.
   *
   * @param suspectFile image to analyze
   * @param watermarkFile original handwriting watermark image
   * @return max correlation across tested scales
   * @throws IOException on I/O error
   */
  public static double detectWatermark(File suspectFile, File watermarkFile) throws IOException {
    BufferedImage suspect = ImageIO.read(suspectFile);
    if (suspect == null) throw new IOException("Could not read suspect image: " + suspectFile);
    BufferedImage watermark = ImageIO.read(watermarkFile);
    if (watermark == null)
      throw new IOException("Could not read watermark image: " + watermarkFile);

    int width = suspect.getWidth();
    int height = suspect.getHeight();

    // compute luminance array
    double[] lum = new double[width * height];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int rgb = suspect.getRGB(x, y);
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        double Y = 0.299 * r + 0.587 * g + 0.114 * b;
        lum[y * width + x] = Y;
      }
    }

    double maxCorr = 0.0;

    double[] scales = {1.0, 0.5, 0.25};

    for (double s : scales) {
      int scaledW = Math.max(4, (int) Math.round(width * s));
      int scaledH = Math.max(4, (int) Math.round(height * s));

      float[][] mask = createMaskFromWatermark(watermark, scaledW, scaledH);

      // Build tiled reference pattern
      double[] ref = new double[width * height];
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
              ref[py * width + px] += m;
            }
          }
        }
      }

      // compute Pearson-like correlation between lum[] and ref[]
      double corr = pearsonCorrelation(lum, ref);
      if (corr > maxCorr) maxCorr = corr;
    }

    return maxCorr;
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

  private static double pearsonCorrelation(double[] a, double[] b) {
    if (a.length != b.length) throw new IllegalArgumentException("Arrays must be same length");
    int n = a.length;
    double meanA = 0, meanB = 0;
    for (int i = 0; i < n; i++) {
      meanA += a[i];
      meanB += b[i];
    }
    meanA /= n;
    meanB /= n;

    double sda = 0, sdb = 0, cov = 0;
    for (int i = 0; i < n; i++) {
      double da = a[i] - meanA;
      double db = b[i] - meanB;
      cov += da * db;
      sda += da * da;
      sdb += db * db;
    }
    if (sda <= 1e-12 || sdb <= 1e-12) return 0.0;
    return cov / Math.sqrt(sda * sdb);
  }
}
