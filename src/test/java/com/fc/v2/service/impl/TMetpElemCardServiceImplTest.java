package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.mapper.auto.TMetpElemCardMapper;
import com.fc.v2.mapper.auto.TMetpStnDocMapper;
import com.fc.v2.model.auto.TMetpElemCard;
import com.fc.v2.model.auto.TMetpStnDoc;

@ExtendWith(MockitoExtension.class)
class TMetpElemCardServiceImplTest {

    @Mock
    private TMetpElemCardMapper baseMapper;

    @Mock
    private TMetpStnDocMapper stnDocMapper;

    @InjectMocks
    private TMetpElemCardServiceImpl service;

    private TMetpStnDoc arch(Long id, String siteNo, int status) {
        TMetpStnDoc a = new TMetpStnDoc();
        a.setId(id);
        a.setSiteNo(siteNo);
        a.setStatus(status);
        a.setDelFlag(0);
        a.setTh1Max(new BigDecimal("12"));
        a.setTh2Max(new BigDecimal("40"));
        a.setTh3Max(new BigDecimal("120"));
        return a;
    }

    private TMetpElemCard card(Integer siteId, String billNo, String qty) {
        TMetpElemCard c = new TMetpElemCard();
        c.setSiteId(siteId);
        c.setBillNo(billNo);
        c.setQty(qty == null ? null : new BigDecimal(qty));
        return c;
    }

    @Test
    void insertRejectsMissingOrStoppedArch() {
        when(stnDocMapper.selectOne(any())).thenReturn(null);
        assertEquals(0, service.insertTMetpElemCard(card(9, "B1", "5")));
        verify(baseMapper, never()).insert(any());

        when(stnDocMapper.selectOne(any())).thenReturn(arch(1L, "TQ01", 1));
        assertEquals(0, service.insertTMetpElemCard(card(1, "B1", "5")));
        verify(baseMapper, never()).insert(any());
    }

    @Test
    void insertRejectsDuplicateBillNo() {
        when(stnDocMapper.selectOne(any())).thenReturn(arch(0L, "TQ00", 0));
        when(baseMapper.selectCount(any())).thenReturn(1);
        assertEquals(0, service.insertTMetpElemCard(card(0, "B1", "5")));
        verify(baseMapper, never()).insert(any());
    }

    @Test
    void insertCopiesSiteNoAndGradesWithOneYardstickEqualGoesHigher() {
        when(stnDocMapper.selectOne(any())).thenReturn(arch(0L, "TQ00", 0));
        when(baseMapper.selectCount(any())).thenReturn(0);
        when(baseMapper.insert(any())).thenReturn(1);

        TMetpElemCard c = card(0, "B1", "12");
        assertEquals(1, service.insertTMetpElemCard(c));
        assertEquals("TQ00", c.getSiteNo());
        assertEquals(Integer.valueOf(2), c.getGradeLevel());
        assertEquals(Integer.valueOf(0), c.getDelFlag());

        // 同一源头：13→二档，40→三档，120→四档
        when(stnDocMapper.selectOne(any())).thenReturn(arch(0L, "TQ00", 0));
        TMetpElemCard c2 = card(0, "B2", "40");
        service.insertTMetpElemCard(c2);
        assertEquals(Integer.valueOf(3), c2.getGradeLevel());
    }

    @Test
    void updateValidatesArchLikeInsertAndRefusesStoppedSite() {
        TMetpElemCard old = card(0, "B1", "5");
        old.setId(100L);
        when(baseMapper.selectOne(any())).thenReturn(old);
        // 改挂到已停测档案：拒绝
        when(stnDocMapper.selectOne(any())).thenReturn(arch(1L, "TQ01", 1));
        TMetpElemCard changed = card(1, "B1", "5");
        changed.setId(100L);
        assertEquals(0, service.updateTMetpElemCard(changed));
        verify(baseMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    void updateResyncsSiteNoAndGradeFromArch() {
        TMetpElemCard old = card(0, "B1", "5");
        old.setId(100L);
        when(baseMapper.selectOne(any())).thenReturn(old);
        when(stnDocMapper.selectOne(any())).thenReturn(arch(0L, "TQ00", 0));
        when(baseMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        TMetpElemCard changed = new TMetpElemCard();
        changed.setId(100L);
        changed.setBillNo("B1");
        changed.setQty(new BigDecimal("50"));
        assertEquals(1, service.updateTMetpElemCard(changed));
        assertEquals("TQ00", changed.getSiteNo());
        assertEquals(Integer.valueOf(0), changed.getSiteId());
        assertEquals(Integer.valueOf(3), changed.getGradeLevel());
    }

    @Test
    void deleteIsSoftDelete() {
        when(baseMapper.update(any(), any(Wrapper.class))).thenReturn(2);
        assertEquals(2, service.deleteTMetpElemCardByIds("1,2"));
        ArgumentCaptor<TMetpElemCard> captor = ArgumentCaptor.forClass(TMetpElemCard.class);
        verify(baseMapper).update(captor.capture(), any(Wrapper.class));
        assertEquals(Integer.valueOf(1), captor.getValue().getDelFlag());
        verify(baseMapper, never()).deleteBatchIds(any());
    }

    @Test
    void listUsesCallerWrapperWithoutForcedPagingOrStatusLock() {
        service.selectTMetpElemCardList(null);
        verify(baseMapper).selectList(any());
    }
}
