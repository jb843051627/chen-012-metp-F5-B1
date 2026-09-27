package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMetpSubmitRowMapper;
import com.fc.v2.model.auto.TMetpSubmitRow;
import com.fc.v2.service.ITMetpSubmitRowService;
import com.fc.v2.util.StringUtils;

/**
 * 正点观测数据核收转存行 Service业务层处理（batch-process 形状：整批提交）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMetpSubmitRowServiceImpl implements ITMetpSubmitRowService {

    private static final int MAX_ROWS = 500;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TMetpSubmitRowMapper metpSubmitRowMapper;

    @Override
    public TMetpSubmitRow selectTMetpSubmitRowById(Long id) {
        return this.metpSubmitRowMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<TMetpSubmitRow> rows) {
        // 白卷：稳稳托住，按 0 笔收口，不许抛错
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        // 超单批上限：整批原样退回，一笔都不许入库
        if (rows.size() > MAX_ROWS) {
            return -1;
        }
        String no = StringUtils.isNotEmpty(batchNo) ? batchNo.trim() : rows.get(0).getBatchNo();
        // 同一册号回头再投一遭，数目不许翻番
        if (StringUtils.isNotEmpty(no)) {
            Integer exists = this.metpSubmitRowMapper.selectCount(new QueryWrapper<TMetpSubmitRow>()
                    .eq("batch_no", no).eq("del_flag", 0));
            if (exists != null && exists > 0) {
                return 0;
            }
        }
        // 一批里同册同行次的重样行只留头一笔，条数不许虚胖
        List<TMetpSubmitRow> dedup = new ArrayList<TMetpSubmitRow>();
        Set<Integer> seenRowNos = new HashSet<Integer>();
        for (TMetpSubmitRow r : rows) {
            Integer rowNo = r.getRowNo();
            if (rowNo != null && !seenRowNos.add(rowNo)) {
                continue;
            }
            dedup.add(r);
        }
        int ok = 0;
        for (TMetpSubmitRow r : dedup) {
            r.setBatchNo(no);
            // 逐行校验：合法的转存、非法的挂驳回，谁出格记谁，不许把整册本分行全数拽回
            if (r.getItemCode() == null || r.getItemCode().trim().isEmpty()
                    || r.getQty() == null
                    || r.getQty().compareTo(BigDecimal.ZERO) <= 0) {
                r.setStatus(STATUS_FAIL);
            } else {
                r.setStatus(STATUS_OK);
                ok++;
            }
            // 原册行次(row_no)原样保留，不许另起序号把行号串位
            this.metpSubmitRowMapper.insert(r);
        }
        return ok;
    }

    @Override
    public List<TMetpSubmitRow> listErrors(String batchNo) {
        return this.metpSubmitRowMapper.selectList(new QueryWrapper<TMetpSubmitRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL)
                .orderByAsc("row_no"));
    }
}
