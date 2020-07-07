package org.apache.ignite.tcbot.engine.newtests;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class NewTestsStorage {
    private final Cache<LocalDate, List<String>> newTests = CacheBuilder.newBuilder()
        .expireAfterWrite(5, TimeUnit.DAYS).build();

    public boolean isNewTest(LocalDate date, String branch, String testId, String srvId) {
        for (int day = 0; day < 5; day++) {
            List<String> dayList = newTests.getIfPresent(date.minusDays(day));

            if (dayList != null) {
                boolean match = dayList.stream().anyMatch(globalTestId -> {
                    return !globalTestId.startsWith(branch) && globalTestId.contains(testId + srvId);
                });

                if (match)
                    return false;
            }

        }

        return true;
    }

    public void putNewTest(LocalDate date, String globalTestId) {
        List<String> currDayList = newTests.getIfPresent(date);
        if (currDayList != null)
            currDayList.add(globalTestId);
        else {
            List<String> list = new ArrayList<>();
            list.add(globalTestId);
            newTests.put(date, list);
        }
    }

}
