package org.ajtech.schema.fixtures;

import java.util.List;
import java.util.Map;

public class NestedCollectionsPojo {
    public List<List<Integer>> matrix;
    public Map<String, List<String>> indexByKey;
    public List<Map<String, Integer>> histograms;
    public Map<String, Map<String, Double>> nestedMap;
    public List<Address> addresses;
}
