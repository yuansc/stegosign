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

import java.io.File;

public class StegoSign {

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      printUsage();
      return;
    }

    String cmd = args[0].toLowerCase();
    switch (cmd) {
      case "embed":
        // existing pixel-domain embed (unchanged)
        if (args.length < 5) {
          System.err.println(
              "Usage: embed <inputImage> <watermarkImage> <outputImage> <strength> (e.g. 0.08)");
          return;
        }
        StegoSignEmbedder.applyWatermark(
            new File(args[1]), new File(args[2]), new File(args[3]), Float.parseFloat(args[4]));
        System.out.println("Embedded (pixel) -> " + args[3]);
        break;

      case "detect":
        // existing detector
        if (args.length < 3) {
          System.err.println("Usage: detect <suspectImage> <watermarkImage>");
          return;
        }
        double s = StegoSignDetector.detectWatermark(new File(args[1]), new File(args[2]));
        System.out.printf("Detection score: %.5f%n", s);
        break;

      case "extract":
        if (args.length < 4) {
          System.err.println(
              "Usage: extract <suspectImage> <watermarkImage> <outputExtractedImage>");
          return;
        }
        StegoSignExtractor.extractWatermark(
            new File(args[1]), new File(args[2]), new File(args[3]));
        System.out.println("Extracted (pixel) -> " + args[3]);
        break;

      default:
        System.err.println("Unknown command: " + cmd);
        printUsage();
    }
  }

  private static void printUsage() {
    System.out.println("Usage:");
    System.out.println("  embed <input> <watermark> <out> <strength> (e.g. 0.08)");
    System.out.println("  detect <suspect> <watermark>");
    System.out.println("  extract <suspect> <watermark> <out>");
  }
}
