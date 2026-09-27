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

import com.fc.v2.mapper.auto.TMetpFixCardMapper;
import com.fc.v2.model.auto.TMetpFixCard;

@ExtendWith(MockitoExtension.class)
class TMetpFixCardServiceImplTest {

    @Mock
    private TMetpFixCardMapper mapper;

    @InjectMocks
    private TMetpFixCardServiceImpl service;

    private TMetpFixCard card(Integer stage, Integer status) {
        TMetpFixCard r = new TMetpFixCard();
        r.setId(1L);
        r.setStage(stage);
        r.setStatus(status);
        return r;
    }

    @Test
    void advanceAndRollbackMoveOneStageWithSameRule() {
        when(mapper.selectById(1L)).thenReturn(card(2, 0));
        when(mapper.updateById(any())).thenReturn(1);
        assertEquals(3, service.advance(1L, "go").getStage().intValue());

        when(mapper.selectById(1L)).thenReturn(card(2, 0));
        assertEquals(1, service.rollback(1L, "back").getStage().intValue());
    }

    @Test
    void advanceCannotJumpTwoAndStopsAtLastGateResolved() {
        when(mapper.selectById(1L)).thenReturn(card(4, 0));
        when(mapper.updateById(any())).thenReturn(1);
        TMetpFixCard r = service.advance(1L, "go");
        assertEquals(5, r.getStage().intValue());
        assertEquals(1, r.getStatus().intValue());

        // 已到头关再推，拒绝
        when(mapper.selectById(1L)).thenReturn(card(5, 1));
        assertNull(service.advance(1L, "go"));
        verify(mapper, never()).deleteById(1L);
    }

    @Test
    void rollbackAtFirstGateReturnsNull() {
        when(mapper.selectById(1L)).thenReturn(card(0, 0));
        assertNull(service.rollback(1L, "back"));
        verify(mapper, never()).updateById(any());
    }

    @Test
    void closedCardRejectsEditRemoveAndFlow() {
        // 已归档
        when(mapper.selectById(1L)).thenReturn(card(3, 2));
        assertNull(service.advance(1L, "go"));
        assertNull(service.rollback(1L, "back"));
        assertEquals(false, service.updateContent(1L, "x"));
        assertEquals(false, service.remove(1L));
        verify(mapper, never()).updateById(any());
        verify(mapper, never()).deleteById(any());
    }

    @Test
    void missingCardReturnsNullOrFalse() {
        when(mapper.selectById(9L)).thenReturn(null);
        assertNull(service.advance(9L, "go"));
        assertNull(service.rollback(9L, "back"));
        assertEquals(false, service.updateContent(9L, "x"));
        assertEquals(false, service.remove(9L));
    }
}
