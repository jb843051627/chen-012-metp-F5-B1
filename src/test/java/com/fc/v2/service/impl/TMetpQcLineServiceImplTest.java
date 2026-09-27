package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TMetpQcLineMapper;
import com.fc.v2.model.auto.TMetpQcLine;

@ExtendWith(MockitoExtension.class)
class TMetpQcLineServiceImplTest {

    @Mock
    private TMetpQcLineMapper mapper;

    @InjectMocks
    private TMetpQcLineServiceImpl service;

    private static Date t(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TMetpQcLine rule(Long id, String code, Integer prio, Integer status,
                             String start, String end, String th1, String th2, String th3) {
        TMetpQcLine r = new TMetpQcLine();
        r.setId(id);
        r.setRuleCode(code);
        r.setPriority(prio);
        r.setStatus(status);
        r.setDelFlag(0);
        r.setEffStart(start == null ? null : t(start));
        r.setEffEnd(end == null ? null : t(end));
        r.setTh1Max(th1 == null ? null : new BigDecimal(th1));
        r.setTh2Max(th2 == null ? null : new BigDecimal(th2));
        r.setTh3Max(th3 == null ? null : new BigDecimal(th3));
        return r;
    }

    @Test
    void onlyRulesEffectiveAtMomentAreListedOrderedByPriority() {
        // 现行旧规(2020-2025)、现行新规(2025起)、已停用、未启用
        TMetpQcLine oldRule = rule(1L, "R", 5, 0, "2020-01-01 00:00:00", "2025-01-01 00:00:00", "10", "50", "90");
        TMetpQcLine newRule = rule(2L, "R", 9, 0, "2025-01-01 00:00:00", null, "10", "50", "90");
        TMetpQcLine stopped = rule(3L, "S", 9, 1, "2020-01-01 00:00:00", null, "10", "50", "90");
        TMetpQcLine future = rule(4L, "F", 9, 0, "2099-01-01 00:00:00", null, "10", "50", "90");
        when(mapper.selectList(any())).thenReturn(Arrays.asList(oldRule, newRule, stopped, future));

        // 交棒当日不含：恰在 2025-01-01 00:00:00 旧规失效、新规生效
        List<TMetpQcLine> avail = service.listAvailable(t("2025-01-01 00:00:00"));
        assertEquals(1, avail.size());
        assertEquals(Long.valueOf(2L), avail.get(0).getId());
        assertEquals(1, service.countAvailable(t("2025-01-01 00:00:00")));

        // 切换前只有旧规；已停用/未启用两本账都不进
        List<TMetpQcLine> before = service.listAvailable(t("2024-12-31 23:59:59"));
        assertEquals(1, before.size());
        assertEquals(Long.valueOf(1L), before.get(0).getId());
    }

    @Test
    void evaluatePicksEffectiveVersionWithOneYardstick() {
        TMetpQcLine oldRule = rule(1L, "R", 5, 0, "2020-01-01 00:00:00", "2025-01-01 00:00:00", "10", "50", "90");
        TMetpQcLine newRule = rule(2L, "R", 9, 0, "2025-01-01 00:00:00", null, "20", "60", "95");
        when(mapper.selectList(any())).thenReturn(Arrays.asList(oldRule, newRule));

        // 同一时刻新旧两本账只许一把尺：新窗口内按新规 15 落一档
        assertEquals(1, service.evaluate("R", new BigDecimal("15"), t("2025-06-01 00:00:00")));
        // 旧窗口内 15 落二档
        assertEquals(2, service.evaluate("R", new BigDecimal("15"), t("2024-06-01 00:00:00")));
        // 越界一律 0
        assertEquals(0, service.evaluate("R", new BigDecimal("-1"), t("2025-06-01 00:00:00")));
        assertEquals(0, service.evaluate("R", new BigDecimal("101"), t("2025-06-01 00:00:00")));
        // 无生效版本返回 0（查不到任何规则）
        when(mapper.selectList(any())).thenReturn(java.util.Collections.<TMetpQcLine>emptyList());
        assertEquals(0, service.evaluate("X", new BigDecimal("1"), t("2025-06-01 00:00:00")));
    }

    @Test
    void equalToUpperBoundGoesOneLevelHigher() {
        TMetpQcLine r = rule(1L, "R", 1, 0, "2020-01-01 00:00:00", null, "10", "50", "90");
        when(mapper.selectList(any())).thenReturn(Arrays.asList(r));
        assertEquals(2, service.evaluate("R", new BigDecimal("10"), t("2025-01-01 00:00:00")));
        assertEquals(3, service.evaluate("R", new BigDecimal("50"), t("2025-01-01 00:00:00")));
        assertEquals(4, service.evaluate("R", new BigDecimal("90"), t("2025-01-01 00:00:00")));
        assertEquals(4, service.evaluate("R", new BigDecimal("91"), t("2025-01-01 00:00:00")));
    }

    @Test
    void usableSharesSameEffectiveRule() {
        TMetpQcLine active = rule(1L, "R", 1, 0, "2025-01-01 00:00:00", null, "10", "50", "90");
        when(mapper.selectById(1L)).thenReturn(active);
        assertTrue(service.usable(1L, t("2025-06-01 00:00:00")));
        assertFalse(service.usable(1L, t("2024-01-01 00:00:00")));
        assertFalse(service.usable(null, t("2025-06-01 00:00:00")));
    }

    @Test
    void evaluateTopPicksHighestPriority() {
        TMetpQcLine low = rule(1L, "A", 1, 0, "2020-01-01 00:00:00", null, "1", "2", "3");
        TMetpQcLine high = rule(2L, "B", 9, 0, "2020-01-01 00:00:00", null, "50", "60", "70");
        when(mapper.selectList(any())).thenReturn(Arrays.asList(low, high));
        // 优先级高者 B 判档：15 落在 B 的二档
        assertEquals(2, service.evaluateTop(new BigDecimal("50"), t("2025-01-01 00:00:00")));
        assertEquals(0, service.evaluateTop(new BigDecimal("101"), t("2025-01-01 00:00:00")));
    }
}
