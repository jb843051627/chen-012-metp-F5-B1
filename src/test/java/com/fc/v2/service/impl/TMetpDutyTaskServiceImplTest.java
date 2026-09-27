package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TMetpDutyTaskMapper;
import com.fc.v2.model.auto.TMetpDutyTask;

@ExtendWith(MockitoExtension.class)
class TMetpDutyTaskServiceImplTest {

    @Mock
    private TMetpDutyTaskMapper mapper;

    @InjectMocks
    private TMetpDutyTaskServiceImpl service;

    private static Date t(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TMetpDutyTask task(Long id, String due, String aheadDays, int status) {
        TMetpDutyTask r = new TMetpDutyTask();
        r.setId(id);
        r.setDueAt(due == null ? null : t(due));
        r.setAmount(aheadDays == null ? null : new BigDecimal(aheadDays));
        r.setStatus(status);
        r.setDelFlag(0);
        return r;
    }

    @Test
    void emptyDueListNeverThrows() {
        when(mapper.selectList(any())).thenReturn(new ArrayList<TMetpDutyTask>());
        assertEquals(0, service.runOnce(t("2026-09-30 03:00:00")));
        assertEquals(0, service.listDue(t("2026-09-30 03:00:00")).size());
    }

    @Test
    void nullTimeReturnsEmpty() {
        assertEquals(0, service.listDue(null).size());
        assertEquals(0, service.runOnce(null));
    }

    @Test
    void dispatchWindowAndAheadDaysFilter() {
        // 到期 10-01 03:00，提前 2 日：窗口 [09-29 03:00, 10-01 03:00]
        TMetpDutyTask r = task(1L, "2026-10-01 03:00:00", "2", 0);
        when(mapper.selectList(any())).thenReturn(Arrays.asList(r));

        // 窗口时辰外（上午10点）即便日期合适也不派
        assertEquals(0, service.listDue(t("2026-09-30 10:00:00")).size());
        // 提前窗未开
        assertEquals(0, service.listDue(t("2026-09-29 02:59:59")).size());
        // 开窗瞬间派
        assertEquals(1, service.listDue(t("2026-09-29 03:00:00")).size());
        // 恰好到期也派（含边界）
        assertEquals(1, service.listDue(t("2026-10-01 03:00:00")).size());
        // 越过到期不派
        assertEquals(0, service.listDue(t("2026-10-01 04:00:00")).size());
    }

    @Test
    void runOnceDispatchesAndMarksDone() {
        TMetpDutyTask a = task(1L, "2026-10-01 03:00:00", "2", 0);
        TMetpDutyTask b = task(2L, "2026-10-01 04:00:00", "2", 0);
        when(mapper.selectList(any())).thenReturn(Arrays.asList(a, b));
        when(mapper.updateById(any())).thenReturn(1);

        // a 恰好到期（含边界）、b 尚未到期，两条都该派
        int ok = service.runOnce(t("2026-10-01 03:00:00"));
        assertEquals(2, ok);
        ArgumentCaptor<TMetpDutyTask> captor = ArgumentCaptor.forClass(TMetpDutyTask.class);
        verify(mapper, times(2)).updateById(captor.capture());
        for (TMetpDutyTask saved : captor.getAllValues()) {
            assertEquals(1, saved.getStatus().intValue());
        }
    }

    @Test
    void singleFailureIsMarkedFailAndDoesNotStopOthers() {
        TMetpDutyTask bad = task(1L, "2026-10-01 03:00:00", "2", 0);
        TMetpDutyTask good = task(2L, "2026-10-01 04:00:00", "2", 0);
        when(mapper.selectList(any())).thenReturn(Arrays.asList(bad, good));
        // 第一条置已派时炸；置派不出去时成功；第二条正常
        when(mapper.updateById(any()))
                .thenThrow(new RuntimeException("db down"))
                .thenReturn(1)
                .thenReturn(1);

        int ok = service.runOnce(t("2026-10-01 03:00:00"));
        assertEquals(1, ok);
        assertEquals(2, bad.getStatus().intValue());
        assertEquals(1, good.getStatus().intValue());
    }
}
