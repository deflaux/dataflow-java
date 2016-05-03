/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.genomics.dataflow.pipelines;

import static org.hamcrest.MatcherAssert.assertThat;

import com.google.api.client.util.Lists;
import com.google.cloud.dataflow.sdk.util.gcsfs.GcsPath;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.util.List;

/**
 * This integration test will call the Genomics API and write to Cloud Storage.
 *
 * The following environment variables are required:
 * - a Google Cloud API key in GOOGLE_API_KEY,
 * - a Google Cloud project name in TEST_PROJECT,
 * - a Cloud Storage folder path in TEST_OUTPUT_GCS_FOLDER to store temporary test outputs,
 * - a Cloud Storage folder path in TEST_STAGING_GCS_FOLDER to store temporary files,
 *
 * Cloud Storage folder paths should be of the form "gs://bucket/folder/"
 *
 * When doing e.g. mvn install, you can skip integration tests using:
 *      mvn install -DskipITs
 *
 * To run one test:
 *      mvn -Dit.test=AnnotateVariantsITCase#testLocal verify
 *
 * See also http://maven.apache.org/surefire/maven-failsafe-plugin/examples/single-test.html
 */
public class AnnotateVariantsITCase {
  static final String[] EXPECTED_RESULT =
      {
          "chr17:40714803:A:CI7s77ro84KpKhIFY2hyMTcYs4S1EyDwuoPB1PDR19AB: WindowReiterable{[{alternateBases=A, effect=NONSYNONYMOUS_SNP, "
              + "geneId=ChYIiN-g9eP0uo-UARDi_aPt7qzv9twBEgIxNxjr_rQTIJrU8My-4_2UdA, transcriptIds=[ChYIiN-g9eP0uo-UARDm-eqXgp7Bi5IBEgIxNxjr_rQTII_53bW3_PSh6AE], type=SNP}]}",
          "chr17:40722028:G:CI7s77ro84KpKhIFY2hyMTcY7Ly1EyDvqeCryb2xrQw: WindowReiterable{[{alternateBases=G, effect=NONSYNONYMOUS_SNP, "
              + "geneId=ChYIiN-g9eP0uo-UARDi_aPt7qzv9twBEgIxNxjlpbUTIL3v58KG8MzFJw, transcriptIds=[ChYIiN-g9eP0uo-UARDm-eqXgp7Bi5IBEgIxNxjlpbUTIMvX96zMvJyV0gE], type=SNP}]}",
          "chr17:40706905:A:CI7s77ro84KpKhIFY2hyMTcY2ca0EyCw4NnN8qzS8S0: WindowReiterable{[{alternateBases=A, effect=NONSYNONYMOUS_SNP, "
              + "geneId=ChYIiN-g9eP0uo-UARDi_aPt7qzv9twBEgIxNxjvr7QTIITZ6M7yo8CnbA, transcriptIds=[ChYIiN-g9eP0uo-UARDm-eqXgp7Bi5IBEgIxNxjvr7QTINX5koLhyJHYkwE], type=SNP}]}",
      };

  static String outputPrefix;
  static IntegrationTestHelper helper;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    helper = new IntegrationTestHelper();
    outputPrefix = helper.getTestOutputGcsFolder() + "annotateVariants";
  }

  @After
  public void tearDown() throws Exception {
    for (GcsPath path : helper.gcsUtil.expand(GcsPath.fromUri(outputPrefix + "*"))) {
            helper.deleteOutput(path.toString());
    }
  }

  @Test
  public void testLocal() throws Exception {
    String[] ARGS = {
        "--references=chr17:40700000:40800000",
        "--variantSetId=" + helper.PLATINUM_GENOMES_DATASET,
        "--transcriptSetIds=CIjfoPXj9LqPlAEQ5vnql4KewYuSAQ",
        "--variantAnnotationSetIds=CILSqfjtlY6tHxC0nNH-4cu-xlQ",
        "--callSetIds=3049512673186936334-0",
        "--output=" + outputPrefix,
        };

    System.out.println(ARGS);

    testBase(ARGS, EXPECTED_RESULT);
  }

  private void testBase(String[] ARGS, String[] expectedResult) throws Exception {
    // Run the pipeline.
    AnnotateVariants.main(ARGS);

    // Download the pipeline results.
    List<String> results = Lists.newArrayList();
    for (GcsPath path : helper.gcsUtil.expand(GcsPath.fromUri(outputPrefix + "*"))) {
      BufferedReader reader = helper.openOutput(path.toString());
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
        results.add(line);
      }
    }

    // Check the pipeline results.
    assertThat(results,
        CoreMatchers.allOf(CoreMatchers.hasItems(expectedResult)));
  }
}


