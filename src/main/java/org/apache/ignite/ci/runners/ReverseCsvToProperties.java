package org.apache.ignite.ci.runners;

import com.google.common.base.Strings;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.IgniteTeamcityHelper;
import org.apache.ignite.ci.model.BuildType;

/**
 * Created by Дмитрий on 29.07.2017
 */
public class ReverseCsvToProperties {
    public static void main(String[] args) throws Exception {
        String tcId = "public";
        List<BuildType> tests = getTests(tcId, "Ignite20Tests");

        int indexValue = 2;
        reverseCsvByConfName("PublicTC.csv", tcId, tests, indexValue);

        String tcPrivId = "private";
        List<BuildType> privTests = getTests(tcPrivId, "id8xIgniteGridGainTests");
        reverseCsvByConfName("Private TC.csv", tcPrivId, privTests, 2);

    }

    private static void reverseCsvByConfName(String csvFile, String tcId, List<BuildType> tests,
        int indexValue) throws IOException {
        String cvsSplitBy = ",";
        Properties reversedProps = new Properties();
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                // use comma as separator
                String[] data = line.split(cvsSplitBy);
                if (data.length == 0)
                    break;
                String configName = normalizeConfName(data[0]);
                Optional<BuildType> first = tests.stream()
                    .filter(cName -> normalizeConfName(cName.getName()).equals(configName.trim())).findFirst();
                if (!first.isPresent()) {
                    System.err.println("Unable to resolve run config: " + configName);
                    continue;
                }
                String valueForProps = normalizeConfName(data[indexValue]);
                reversedProps.put(first.get().getId(), valueForProps);
            }
        }
        for (BuildType next : tests) {
            String btId = next.getId();
            String reversed = reversedProps.getProperty(btId);
            if (reversed == null) {
                System.err.println("Reverse failed for " + btId);
            }
        }
        try (FileWriter writer = new FileWriter(tcId + ".resp.properties");) {
            reversedProps.store(writer, "");
        }
    }

    private static String normalizeConfName(String cName) {
        return Strings.nullToEmpty(cName).trim().replaceAll("&amp;", "&");
    }

    private static List<BuildType> getTests(String tcId, String projectId) throws Exception {
        try (ITeamcity teamcity = new IgniteTeamcityHelper(tcId)) {
            List<BuildType> tests = teamcity.getProjectSuites(projectId).get();
            return tests;
        }
    }
}
