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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

public class WatermarkExtractorTest {

  private final File input = new File("testdata/input.png");
  private final File watermark = new File("testdata/watermark.png");
  private final File output = new File("target/output.png");
  private final File extracted = new File("target/extracted.png");
  private final File resized = new File("target/output_resized.png");
  private final File extractedResized = new File("target/extracted_resized.png");
  private final File colorChanged = new File("target/output_colorchanged.png");
  private final File extractedColor = new File("target/extracted_color.png");

  @Test
  public void testExtractFromOriginalWatermarkedImage() throws Exception {
    float strength = 0.08f;
    WatermarkEmbedder.applyWatermark(input, watermark, output, strength);

    WatermarkExtractor.extractWatermark(output, watermark, extracted);
    assertTrue(extracted.exists(), "Extracted watermark file should exist");

    BufferedImage exImg = ImageIO.read(extracted);
    double variance = computeVariance(exImg);
    assertTrue(variance > 100, "Extracted image should not be flat, variance=" + variance);
  }

  @Test
  public void testExtractAfterResize() throws Exception {
    float strength = 0.08f;
    WatermarkEmbedder.applyWatermark(input, watermark, output, strength);

    // create resized version (50%)
    BufferedImage out = ImageIO.read(output);
    BufferedImage small =
        new BufferedImage(out.getWidth() / 2, out.getHeight() / 2, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = small.createGraphics();
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(out, 0, 0, small.getWidth(), small.getHeight(), null);
    g.dispose();
    ImageIO.write(small, "png", resized);

    WatermarkExtractor.extractWatermark(resized, watermark, extractedResized);
    assertTrue(extractedResized.exists(), "Extracted watermark from resized image should exist");

    BufferedImage exImg = ImageIO.read(extractedResized);
    double variance = computeVariance(exImg);
    assertTrue(
        variance > 50, "Extracted watermark should have signal after resize, variance=" + variance);
  }

  @Test
  public void testExtractAfterColorChange() throws Exception {
    float strength = 0.08f;
    WatermarkEmbedder.applyWatermark(input, watermark, output, strength);

    BufferedImage out = ImageIO.read(output);
    BufferedImage altered =
        new BufferedImage(out.getWidth(), out.getHeight(), BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < out.getHeight(); y++) {
      for (int x = 0; x < out.getWidth(); x++) {
        int rgb = out.getRGB(x, y);
        int a = (rgb >> 24) & 0xff;
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        // apply slight color distortion
        int r2 = clamp((int) (r * 0.85));
        int g2 = clamp((int) (g * 0.9));
        int b2 = clamp((int) (b * 0.95));
        altered.setRGB(x, y, (a << 24) | (r2 << 16) | (g2 << 8) | b2);
      }
    }
    ImageIO.write(altered, "png", colorChanged);

    WatermarkExtractor.extractWatermark(colorChanged, watermark, extractedColor);
    assertTrue(
        extractedColor.exists(), "Extracted watermark from color-changed image should exist");

    BufferedImage exImg = ImageIO.read(extractedColor);
    double variance = computeVariance(exImg);
    assertTrue(
        variance > 50,
        "Extracted watermark should have signal after color change, variance=" + variance);
  }

  // --- Helpers ---
  private static int clamp(int v) {
    return v < 0 ? 0 : Math.min(255, v);
  }

  private static double computeVariance(BufferedImage img) {
    long sum = 0;
    long sumSq = 0;
    int n = img.getWidth() * img.getHeight();
    for (int y = 0; y < img.getHeight(); y++) {
      for (int x = 0; x < img.getWidth(); x++) {
        int rgb = img.getRGB(x, y);
        int gray = rgb & 0xff; // since it's grayscale output
        sum += gray;
        sumSq += (long) gray * gray;
      }
    }
    double mean = sum / (double) n;
    return (sumSq / (double) n) - mean * mean;
  }
}
