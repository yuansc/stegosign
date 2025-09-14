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

public class StegoSignEmbedderTest {

  // NOTE: put your test images into testdata/:
  // - input.png (or .jpg) : the carrier image
  // - watermark.png : handwriting (black strokes on white or transparent)
  private final File input = new File("testdata/input.png");
  private final File watermark = new File("testdata/watermark.png");
  private final File output = new File("target/output.png");
  private final File resized = new File("target/output_resized.png");
  private final File colorChanged = new File("target/output_colorchanged.png");

  @Test
  public void testWatermarkCreationAndDetectable() throws Exception {
    float strength = 0.08f; // you can tune: 0.04..0.12
    StegoSignEmbedder.applyWatermark(input, watermark, output, strength);
    assertTrue(output.exists(), "Output file should have been created");

    double score = StegoSignDetector.detectWatermark(output, watermark);
    assertTrue(score > 0.02, "Watermark should be detectable in output. Score: " + score);
  }

  @Test
  public void testResilienceAfterResize() throws Exception {
    float strength = 0.08f;
    StegoSignEmbedder.applyWatermark(input, watermark, output, strength);

    // create resized (50%) version to simulate resizing
    BufferedImage out = ImageIO.read(output);
    BufferedImage small =
        new BufferedImage(out.getWidth() / 2, out.getHeight() / 2, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = small.createGraphics();
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(out, 0, 0, small.getWidth(), small.getHeight(), null);
    g.dispose();
    ImageIO.write(small, "png", resized);

    double score = StegoSignDetector.detectWatermark(resized, watermark);
    assertTrue(score > 0.015, "Watermark should be detectable after resizing. Score: " + score);
  }

  @Test
  public void testResilienceAfterColorChange() throws Exception {
    float strength = 0.08f;
    StegoSignEmbedder.applyWatermark(input, watermark, output, strength);

    BufferedImage out = ImageIO.read(output);
    // simulate color/contrast change (darker)
    BufferedImage altered =
        new BufferedImage(out.getWidth(), out.getHeight(), BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < out.getHeight(); y++) {
      for (int x = 0; x < out.getWidth(); x++) {
        int rgb = out.getRGB(x, y);
        int a = (rgb >> 24) & 0xff;
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        // apply a simple brightness reduction and slight channel shift
        int r2 = clamp((int) (r * 0.9));
        int g2 = clamp((int) (g * 0.85));
        int b2 = clamp((int) (b * 0.9));
        int newRgb = (a << 24) | (r2 << 16) | (g2 << 8) | b2;
        altered.setRGB(x, y, newRgb);
      }
    }
    ImageIO.write(altered, "png", colorChanged);

    double score = StegoSignDetector.detectWatermark(colorChanged, watermark);
    assertTrue(score > 0.015, "Watermark should be detectable after color change. Score: " + score);
  }

  private static int clamp(int v) {
    if (v < 0) return 0;
    if (v > 255) return 255;
    return v;
  }
}
