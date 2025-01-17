/*
 * Copyright (C) 2014 Google Inc.
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

package com.google.cloud.genomics.dataflow.functions.pca;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;

import com.google.api.client.util.Lists;
import com.google.api.client.util.Preconditions;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.values.KV;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableList;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * This function runs a Principal Coordinate Analysis inside of a SeqDo.
 * It can not be parallelized.
 *
 * See http://en.wikipedia.org/wiki/PCoA for more information.
 *
 * Note that this is not the same as
 * Principal Component Analysis (http://en.wikipedia.org/wiki/Principal_component_analysis)
 *
 * The input data to this algorithm must be for a similarity matrix - and the
 * resulting matrix must be symmetric.
 *
 * Input: KV(KV(dataName, dataName), count of how similar the data pair is)
 * Output: GraphResults - an x/y pair and a label
 *
 * Example input for a tiny dataset of size 2:
 *
 * KV(KV(data1, data1), 5)
 * KV(KV(data1, data2), 2)
 * KV(KV(data2, data2), 5)
 * KV(KV(data2, data1), 2)
 */
public class PCoAnalysis extends DoFn<Iterable<KV<KV<String, String>, Long>>,
    Iterable<PCoAnalysis.GraphResult>> {

  public static class GraphResult implements Serializable {

    public double graphX;
    public double graphY;
    public String name;

    public GraphResult(String name, double x, double y) {
      this.name = name;
      this.graphX = Math.floor(x * 100) / 100;
      this.graphY = Math.floor(y * 100) / 100;
    }

    @Override
    public String toString() {
      return String.format("%s\t\t%s\t%s", name, graphX, graphY);
    }

    public static GraphResult fromString(String tsv) {
      Preconditions.checkNotNull(tsv);
      String[] tokens = tsv.split("[\\s\t]+");
      Preconditions.checkState(3 == tokens.length,
          "Expected three values in serialized GraphResult but found %d", tokens.length);
      return new GraphResult(tokens[0],
          Double.parseDouble(tokens[1]),
          Double.parseDouble(tokens[2]));
    }

    @Override // auto-generated via eclipse
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      long temp;
      temp = Double.doubleToLongBits(graphX);
      result = prime * result + (int) (temp ^ (temp >>> 32));
      temp = Double.doubleToLongBits(graphY);
      result = prime * result + (int) (temp ^ (temp >>> 32));
      result = prime * result + ((name == null) ? 0 : name.hashCode());
      return result;
    }

    @Override // auto-generated via eclipse
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      GraphResult other = (GraphResult) obj;
      if (name == null) {
        if (other.name != null)
          return false;
      } else if (!name.equals(other.name))
        return false;
      if (Double.doubleToLongBits(graphX) != Double.doubleToLongBits(other.graphX))
        return false;
      if (Double.doubleToLongBits(graphY) != Double.doubleToLongBits(other.graphY))
        return false;
      return true;
    }
  }

  private BiMap<String, Integer> dataIndices;

  public PCoAnalysis(BiMap<String, Integer> dataIndices) {
    this.dataIndices = dataIndices;
  }

  // Convert the similarity matrix to an Eigen matrix.
  private List<GraphResult> getPcaData(double[][] data, BiMap<Integer, String> dataNames) {
    int rows = data.length;
    int cols = data.length;

    // Center the similarity matrix.
    double matrixSum = 0;
    double[] rowSums = new double[rows];
    for (int i = 0; i < rows; i++) {
      for (int j = 0; j < cols; j++) {
        matrixSum += data[i][j];
        rowSums[i] += data[i][j];
      }
    }
    double matrixMean = matrixSum / rows / cols;
    for (int i = 0; i < rows; i++) {
      for (int j = 0; j < cols; j++) {
        double rowMean = rowSums[i] / rows;
        double colMean = rowSums[j] / rows;
        data[i][j] = data[i][j] - rowMean - colMean + matrixMean;
      }
    }


    // Determine the eigenvectors, and scale them so that their
    // sum of squares equals their associated eigenvalue.
    Matrix matrix = new Matrix(data);
    EigenvalueDecomposition eig = matrix.eig();
    Matrix eigenvectors = eig.getV();
    double[] realEigenvalues = eig.getRealEigenvalues();

    for (int j = 0; j < eigenvectors.getColumnDimension(); j++) {
      double sumSquares = 0;
      for (int i = 0; i < eigenvectors.getRowDimension(); i++) {
        sumSquares += eigenvectors.get(i, j) * eigenvectors.get(i, j);
      }
      for (int i = 0; i < eigenvectors.getRowDimension(); i++) {
        eigenvectors.set(i, j, eigenvectors.get(i,j) * Math.sqrt(realEigenvalues[j] / sumSquares));
      }
    }


    // Find the indices of the top two eigenvalues.
    int maxIndex = -1;
    int secondIndex = -1;
    double maxEigenvalue = 0;
    double secondEigenvalue = 0;

    for (int i = 0; i < realEigenvalues.length; i++) {
      double eigenvector = realEigenvalues[i];
      if (eigenvector > maxEigenvalue) {
        secondEigenvalue = maxEigenvalue;
        secondIndex = maxIndex;
        maxEigenvalue = eigenvector;
        maxIndex = i;
      } else if (eigenvector > secondEigenvalue) {
        secondEigenvalue = eigenvector;
        secondIndex = i;
      }
    }


    // Return projected data
    List<GraphResult> results = Lists.newArrayList();
    for (int i = 0; i < rows; i++) {
      results.add(new GraphResult(dataNames.get(i),
          eigenvectors.get(i, maxIndex), eigenvectors.get(i, secondIndex)));
    }

    return results;
  }

  @ProcessElement
  public void processElement(ProcessContext context) {
    Collection<KV<KV<String, String>, Long>> element = ImmutableList.copyOf(context.element());

    int dataSize = dataIndices.size();
    double[][] matrixData = new double[dataSize][dataSize];
    for (KV<KV<String, String>, Long> entry : element) {
      int d1 = dataIndices.get(entry.getKey().getKey());
      int d2 = dataIndices.get(entry.getKey().getValue());
      double value = entry.getValue();
      matrixData[d1][d2] = value;
      if (d1 != d2) {
        matrixData[d2][d1] = value;
      }
    }
    context.output(getPcaData(matrixData, dataIndices.inverse()));
  }
}
