package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TMetpMoveBillMapper;
import com.fc.v2.model.auto.TMetpMoveBill;

@ExtendWith(MockitoExtension.class)
class TMetpMoveBillServiceImplTest {

    @Mock
    private TMetpMoveBillMapper mapper;

    @InjectMocks
    private TMetpMoveBillServiceImpl service;

    private TMetpMoveBill bill(Integer node, Integer mode, Integer need, Integer signed, Integer status) {
        TMetpMoveBill r = new TMetpMoveBill();
        r.setId(1L);
        r.setNodeNo(node);
        r.setSignMode(mode);
        r.setNeedCount(need);
        r.setSignCount(signed);
        r.setStatus(status);
        return r;
    }

    @Test
    void orModeOneVotePassesNode() {
        // 任一人模式：一票就过层、计数清零
        when(mapper.selectById(1L)).thenReturn(bill(0, 0, 1, 0, 0));
        when(mapper.updateById(any())).thenReturn(1);
        TMetpMoveBill r = service.approve(1L, "zhang", "ok");
        assertEquals(1, r.getNodeNo().intValue());
        assertEquals(0, r.getSignCount().intValue());
        assertEquals(0, r.getStatus().intValue());
    }

    @Test
    void andModeNeedsBothSignatures() {
        // 两名点齐：第一票不过层、签1；第二票过层、清零
        when(mapper.selectById(1L)).thenReturn(bill(0, 1, 2, 0, 0));
        when(mapper.updateById(any())).thenReturn(1);
        TMetpMoveBill first = service.approve(1L, "zhang", "ok");
        assertEquals(0, first.getNodeNo().intValue());
        assertEquals(1, first.getSignCount().intValue());

        when(mapper.selectById(1L)).thenReturn(bill(0, 1, 2, 1, 0));
        TMetpMoveBill second = service.approve(1L, "li", "ok");
        assertEquals(1, second.getNodeNo().intValue());
        assertEquals(0, second.getSignCount().intValue());
    }

    @Test
    void rejectSetsVetoAndLocks() {
        when(mapper.selectById(1L)).thenReturn(bill(1, 0, 1, 0, 0));
        when(mapper.updateById(any())).thenReturn(1);
        TMetpMoveBill r = service.reject(1L, "wang", "no");
        assertEquals(2, r.getStatus().intValue());

        // 已打回的单子再签再否决，一律拒绝
        when(mapper.selectById(1L)).thenReturn(bill(1, 0, 1, 0, 2));
        assertNull(service.approve(1L, "zhang", "ok"));
        assertNull(service.reject(1L, "wang", "no"));
        assertNull(service.rollback(1L, "back"));
        // 首层无处可退
        when(mapper.selectById(2L)).thenReturn(bill(0, 0, 1, 0, 0));
        assertNull(service.rollback(2L, "back"));
    }

    @Test
    void rollbackRetractsCurrentNodeAndMovesBack() {
        // 第二层已签1票后退回：签票清零、退一层
        when(mapper.selectById(1L)).thenReturn(bill(2, 1, 2, 1, 0));
        when(mapper.updateById(any())).thenReturn(1);
        TMetpMoveBill r = service.rollback(1L, "back");
        assertEquals(1, r.getNodeNo().intValue());
        assertEquals(0, r.getSignCount().intValue());
        assertEquals(0, r.getStatus().intValue());
    }

    @Test
    void passAtLastNode() {
        // 末层(3)任一模式一票核讫
        when(mapper.selectById(1L)).thenReturn(bill(3, 0, 1, 0, 0));
        when(mapper.updateById(any())).thenReturn(1);
        TMetpMoveBill r = service.approve(1L, "zhang", "ok");
        assertEquals(3, r.getNodeNo().intValue());
        assertEquals(1, r.getStatus().intValue());
        assertEquals(1, r.getSignCount().intValue());
    }

    @Test
    void nullApproverRejected() {
        when(mapper.selectById(1L)).thenReturn(bill(0, 0, 1, 0, 0));
        assertNull(service.approve(1L, "  ", "ok"));
        assertNull(service.approve(1L, null, "ok"));
        verify(mapper, never()).updateById(any());
    }
}
