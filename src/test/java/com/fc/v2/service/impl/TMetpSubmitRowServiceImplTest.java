package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TMetpSubmitRowMapper;
import com.fc.v2.model.auto.TMetpSubmitRow;

@ExtendWith(MockitoExtension.class)
class TMetpSubmitRowServiceImplTest {

    @Mock
    private TMetpSubmitRowMapper mapper;

    @InjectMocks
    private TMetpSubmitRowServiceImpl service;

    private TMetpSubmitRow row(Integer rowNo, String code, String qty) {
        TMetpSubmitRow r = new TMetpSubmitRow();
        r.setRowNo(rowNo);
        r.setItemCode(code);
        r.setQty(qty == null ? null : new BigDecimal(qty));
        return r;
    }

    @Test
    void emptyBatchReturnsZeroWithoutThrowing() {
        assertEquals(0, service.submitBatch("B", null));
        assertEquals(0, service.submitBatch("B", new ArrayList<TMetpSubmitRow>()));
        assertEquals(0, service.submitBatch(null, java.util.Collections.singletonList(row(1, "A", "1"))));
        verify(mapper, never()).insert(any());
    }

    @Test
    void overLimitReturnsMinusOneAndNothingPersisted() {
        List<TMetpSubmitRow> rows = new ArrayList<TMetpSubmitRow>();
        for (int i = 0; i < 501; i++) {
            rows.add(row(i + 1, "C" + i, "1"));
        }
        assertEquals(-1, service.submitBatch("B", rows));
        verify(mapper, never()).insert(any());
    }

    @Test
    void duplicateResubmitIsRejected() {
        when(mapper.selectCount(any())).thenReturn(1);
        assertEquals(0, service.submitBatch("B", java.util.Collections.singletonList(row(1, "A", "1"))));
        verify(mapper, never()).insert(any());
    }

    @Test
    void repeatedRowsInOneBatchAreMergedKeepingFirstRowNo() {
        when(mapper.selectCount(any())).thenReturn(0);
        when(mapper.delete(any())).thenReturn(0);
        when(mapper.insert(any())).thenReturn(1);

        List<TMetpSubmitRow> rows = new ArrayList<TMetpSubmitRow>();
        rows.add(row(7, "A", "2"));
        rows.add(row(9, "A", "3"));

        assertEquals(1, service.submitBatch("B01", rows));

        ArgumentCaptor<TMetpSubmitRow> captor = ArgumentCaptor.forClass(TMetpSubmitRow.class);
        verify(mapper, times(1)).insert(captor.capture());
        TMetpSubmitRow saved = captor.getValue();
        assertEquals(0, new BigDecimal("5").compareTo(saved.getQty()));
        assertEquals(Integer.valueOf(7), saved.getRowNo());
        assertEquals("B01", saved.getBatchNo());
        assertEquals(Integer.valueOf(1), saved.getStatus());
    }

    @Test
    void invalidRowsDoNotDragValidRowsBackAndKeepOriginalRowNo() {
        when(mapper.selectCount(any())).thenReturn(0);
        when(mapper.delete(any())).thenReturn(0);
        when(mapper.insert(any())).thenReturn(1);

        List<TMetpSubmitRow> rows = new ArrayList<TMetpSubmitRow>();
        rows.add(row(3, "A", "1"));        // 合法
        rows.add(row(5, "", "2"));         // 缺代号
        rows.add(row(8, "C", "0"));        // 条数非正

        assertEquals(1, service.submitBatch("B", rows));

        ArgumentCaptor<TMetpSubmitRow> captor = ArgumentCaptor.forClass(TMetpSubmitRow.class);
        verify(mapper, times(3)).insert(captor.capture());
        List<TMetpSubmitRow> saved = captor.getAllValues();
        assertEquals(Integer.valueOf(1), saved.get(0).getStatus());
        assertEquals(Integer.valueOf(2), saved.get(1).getStatus());
        assertEquals(Integer.valueOf(5), saved.get(1).getRowNo());
        assertEquals(Integer.valueOf(2), saved.get(2).getStatus());
        assertEquals(Integer.valueOf(8), saved.get(2).getRowNo());
    }

    @Test
    void missingRowNoFallsBackToPositionButPresentOneNeverShifted() {
        when(mapper.selectCount(any())).thenReturn(0);
        when(mapper.delete(any())).thenReturn(0);
        when(mapper.insert(any())).thenReturn(1);

        TMetpSubmitRow r = row(null, "A", "1");
        service.submitBatch("B", java.util.Collections.singletonList(r));
        assertEquals(Integer.valueOf(1), r.getRowNo());
        assertNull(r.getRemark());
    }
}
