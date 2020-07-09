package org.apache.ignite.tcbot.engine.newtests;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** */
public class NewTestsStorage {
    private final Cache<LocalDate, List<String>> newTests = CacheBuilder.newBuilder()
        .expireAfterWrite(3, TimeUnit.DAYS).build();

    public boolean isNewTest(String branch, String testId, String srvId) {
        LocalDate nowDate = LocalDate.now();

        for (int day = 0; day < 3; day++) {
            List<String> dayList = newTests.getIfPresent(nowDate.minusDays(day));

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

    public void putNewTest(String globalTestId) {
        List<String> currDayList = newTests.getIfPresent(LocalDate.now());
        if (currDayList != null)
            currDayList.add(globalTestId);
        else {
            List<String> list = new ArrayList<>();
            list.add(globalTestId);
            newTests.put(LocalDate.now(), list);
        }
    }
}
