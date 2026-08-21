package fu.osms.inventory.addressmatching.service.impl;

import fu.osms.inventory.addressmatching.AddressMatchResult;
import fu.osms.inventory.addressmatching.AddressMatcher;
import fu.osms.inventory.addressmatching.dto.response.AddressGroupResponse;
import fu.osms.inventory.addressmatching.service.AddressComparisonService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AddressComparisonServiceImpl implements AddressComparisonService {

    @Override
    public AddressMatchResult compare(String address1, String address2) {
        return AddressMatcher.compare(address1, address2);
    }

    @Override
    public AddressGroupResponse groupAddresses(List<String> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return AddressGroupResponse.builder()
                    .groups(Collections.emptyList())
                    .totalGroups(0)
                    .allSameAddress(true)
                    .build();
        }

        if (addresses.size() == 1) {
            return AddressGroupResponse.builder()
                    .groups(List.of(AddressGroupResponse.AddressGroup.builder()
                            .groupId(1)
                            .addresses(List.of(addresses.get(0)))
                            .isSameAddress(true)
                            .averageScore(1.0)
                            .build()))
                    .totalGroups(1)
                    .allSameAddress(true)
                    .build();
        }

        int n = addresses.size();
        int[] parent = new int[n];
        double[] totalScore = new double[n];
        int[] count = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            totalScore[i] = 0.0;
            count[i] = 0;
        }

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                AddressMatchResult result = AddressMatcher.compare(addresses.get(i), addresses.get(j));
                if (result.isMatched()) {
                    int rootI = find(parent, i);
                    int rootJ = find(parent, j);
                    if (rootI != rootJ) {
                        parent[rootI] = rootJ;
                    }
                    totalScore[find(parent, i)] += result.getScore();
                    count[find(parent, i)]++;
                }
            }
        }

        Map<Integer, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            groups.computeIfAbsent(root, k -> new ArrayList<>()).add(i);
        }

        List<AddressGroupResponse.AddressGroup> groupList = new ArrayList<>();
        int groupId = 1;
        for (List<Integer> indices : groups.values()) {
            List<String> groupAddresses = indices.stream()
                    .map(addresses::get)
                    .toList();

            int root = find(parent, indices.get(0));
            double avgScore = count[root] > 0 ? totalScore[root] / count[root] : 1.0;

            groupList.add(AddressGroupResponse.AddressGroup.builder()
                    .groupId(groupId++)
                    .addresses(groupAddresses)
                    .isSameAddress(true)
                    .averageScore(Math.round(avgScore * 100.0) / 100.0)
                    .build());
        }

        return AddressGroupResponse.builder()
                .groups(groupList)
                .totalGroups(groupList.size())
                .allSameAddress(groupList.size() == 1)
                .build();
    }

    private int find(int[] parent, int i) {
        if (parent[i] != i) {
            parent[i] = find(parent, parent[i]);
        }
        return parent[i];
    }
}
