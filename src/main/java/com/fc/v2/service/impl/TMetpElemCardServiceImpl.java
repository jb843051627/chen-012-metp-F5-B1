package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TMetpElemCardMapper;
import com.fc.v2.mapper.auto.TMetpStnDocMapper;
import com.fc.v2.model.auto.TMetpElemCard;
import com.fc.v2.model.auto.TMetpStnDoc;
import com.fc.v2.service.ITMetpElemCardService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 观测要素挂用建档卡Service业务层处理
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TMetpElemCardServiceImpl extends ServiceImpl<TMetpElemCardMapper, TMetpElemCard> implements ITMetpElemCardService {

    @Autowired
    private TMetpStnDocMapper metpStnDocMapper;

    @Override
    public TMetpElemCard selectTMetpElemCardById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TMetpElemCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    /** 按调用方给的条件查；只统一兜一道"未删"底，分页交给调用方，不擅自锁进展 */
    @Override
    public List<TMetpElemCard> selectTMetpElemCardList(Wrapper<TMetpElemCard> queryWrapper) {
        QueryWrapper<TMetpElemCard> wrapper;
        if (queryWrapper instanceof QueryWrapper) {
            wrapper = (QueryWrapper<TMetpElemCard>) queryWrapper;
        } else {
            wrapper = new QueryWrapper<TMetpElemCard>();
        }
        wrapper.eq("del_flag", 0);
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    public int insertTMetpElemCard(TMetpElemCard record) {
        if (record == null) {
            return 0;
        }
        TMetpStnDoc arch = loadActiveArch(record.getSiteId() == null ? null : record.getSiteId().longValue());
        if (arch == null) {
            return 0;
        }
        if (StringUtils.isNotEmpty(record.getBillNo()) && existsBillNo(record.getBillNo(), null)) {
            return 0;
        }
        record.setSiteNo(arch.getSiteNo());
        record.setGradeLevel(gradeLevel(record.getQty(), arch));
        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTMetpElemCard(TMetpElemCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }
        TMetpElemCard old = this.baseMapper.selectOne(new QueryWrapper<TMetpElemCard>()
                .eq("id", record.getId()).eq("del_flag", 0));
        if (old == null) {
            return 0;
        }
        if (StringUtils.isNotEmpty(record.getBillNo()) && existsBillNo(record.getBillNo(), record.getId())) {
            return 0;
        }
        // 挂接档案与新增走同一道校验：指向档案须在册，站点编号/等级档随档案一个源头
        Long targetSiteId = record.getSiteId() == null
                ? (old.getSiteId() == null ? null : old.getSiteId().longValue())
                : record.getSiteId().longValue();
        TMetpStnDoc arch = loadActiveArch(targetSiteId);
        if (arch == null) {
            return 0;
        }
        if (record.getSiteId() == null) {
            record.setSiteId(old.getSiteId());
        }
        record.setSiteNo(arch.getSiteNo());
        BigDecimal qty = record.getQty() == null ? old.getQty() : record.getQty();
        record.setGradeLevel(gradeLevel(qty, arch));

        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<TMetpElemCard>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    @Override
    public int deleteTMetpElemCardByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        if (idArr == null || idArr.length == 0) {
            return 0;
        }
        TMetpElemCard update = new TMetpElemCard();
        update.setDelFlag(Integer.valueOf(1));
        return this.baseMapper.update(update, new UpdateWrapper<TMetpElemCard>()
                .in("id", Arrays.asList(idArr))
                .eq("del_flag", 0));
    }

    @Override
    public int deleteTMetpElemCardById(Long id) {
        TMetpElemCard update = new TMetpElemCard();
        update.setDelFlag(Integer.valueOf(1));
        return this.baseMapper.update(update, new UpdateWrapper<TMetpElemCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    /** 档案唯一入口：只查这一回——须存在、未删、在册；已停测的档案挂不上卡 */
    private TMetpStnDoc loadActiveArch(Long siteId) {
        if (siteId == null) {
            return null;
        }
        TMetpStnDoc arch = metpStnDocMapper.selectOne(new QueryWrapper<TMetpStnDoc>()
                .eq("id", siteId).eq("del_flag", 0));
        if (arch == null || (arch.getStatus() != null && arch.getStatus() == 1)) {
            return null;
        }
        return arch;
    }

    private boolean existsBillNo(String billNo, Long excludeId) {
        QueryWrapper<TMetpElemCard> qw = new QueryWrapper<TMetpElemCard>()
                .eq("bill_no", billNo).eq("del_flag", 0);
        if (excludeId != null) {
            qw.ne("id", excludeId);
        }
        Integer dupCnt = this.baseMapper.selectCount(qw);
        return dupCnt != null && dupCnt > 0;
    }

    /**
     * 资料保障等级档：以基础档案的三档上限为唯一尺，等于上限算高一档
     * （与 t_metp_stn_doc 种子册、质控界限分档口径一致）。
     */
    private Integer gradeLevel(BigDecimal qty, TMetpStnDoc arch) {
        if (qty == null) {
            return Integer.valueOf(0);
        }
        if (arch.getTh1Max() != null && qty.compareTo(arch.getTh1Max()) < 0) {
            return Integer.valueOf(1);
        }
        if (arch.getTh2Max() != null && qty.compareTo(arch.getTh2Max()) < 0) {
            return Integer.valueOf(2);
        }
        if (arch.getTh3Max() != null && qty.compareTo(arch.getTh3Max()) < 0) {
            return Integer.valueOf(3);
        }
        return Integer.valueOf(4);
    }
}
