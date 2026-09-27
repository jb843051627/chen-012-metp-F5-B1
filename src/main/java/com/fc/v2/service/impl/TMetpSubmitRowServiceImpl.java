package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMetpSubmitRowMapper;
import com.fc.v2.model.auto.TMetpSubmitRow;
import com.fc.v2.service.ITMetpSubmitRowService;

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

    /**
     * 整批提交：
     * 1) 空批次（null/无行）稳稳返回 0，不抛错；
     * 2) 行数超过单批上限（{@value #MAX_ROWS}）返回 -1，整批不入库；
     * 3) 同一册号已有转存成功行，视为重复提交，整批不再入库；
     * 4) 册内同一要素代号的重样行先合并（条数累加），保留首次出现的原册行号；
     * 5) 逐行校验：合法行转存、非法行挂驳回（行号取原册行次，不随循环错位）；
     *    几行出格不连累本分行；
     * 6) 重新报送前清掉该册旧的驳回明细，避免新旧两笔混在一起。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<TMetpSubmitRow> rows) {
        if (batchNo == null || batchNo.trim().isEmpty() || rows == null || rows.isEmpty()) {
            return 0;
        }
        if (rows.size() > MAX_ROWS) {
            return -1;
        }
        Integer existCnt = this.metpSubmitRowMapper.selectCount(new QueryWrapper<TMetpSubmitRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_OK).eq("del_flag", 0));
        if (existCnt != null && existCnt > 0) {
            return 0;
        }
        // 上一稿的驳回明细先撤下，本次以新稿为准
        this.metpSubmitRowMapper.delete(new QueryWrapper<TMetpSubmitRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL).eq("del_flag", 0));

        List<TMetpSubmitRow> merged = mergeDuplicates(rows);

        int ok = 0;
        for (TMetpSubmitRow r : merged) {
            r.setId(null);
            r.setBatchNo(batchNo);
            r.setDelFlag(0);
            if (r.getItemCode() == null || r.getItemCode().trim().isEmpty()
                    || r.getQty() == null
                    || r.getQty().compareTo(BigDecimal.ZERO) <= 0) {
                r.setStatus(STATUS_FAIL);
            } else {
                r.setStatus(STATUS_OK);
                ok++;
            }
            this.metpSubmitRowMapper.insert(r);
        }
        return ok;
    }

    /** 同册内要素代号重样的两笔并成一笔：条数累加，行号守住最先出现的那一行；其余行原序不动 */
    private List<TMetpSubmitRow> mergeDuplicates(List<TMetpSubmitRow> rows) {
        Map<String, TMetpSubmitRow> byItem = new LinkedHashMap<String, TMetpSubmitRow>();
        List<TMetpSubmitRow> result = new ArrayList<TMetpSubmitRow>();
        for (int i = 0; i < rows.size(); i++) {
            TMetpSubmitRow r = rows.get(i);
            if (r.getRowNo() == null) {
                r.setRowNo(Integer.valueOf(i + 1));
            }
            String code = r.getItemCode() == null ? null : r.getItemCode().trim();
            if (code == null || code.isEmpty()) {
                result.add(r);
                continue;
            }
            TMetpSubmitRow kept = byItem.get(code);
            if (kept == null) {
                r.setItemCode(code);
                byItem.put(code, r);
                result.add(r);
            } else if (r.getQty() != null) {
                BigDecimal sum = (kept.getQty() == null ? BigDecimal.ZERO : kept.getQty()).add(r.getQty());
                kept.setQty(sum);
            }
        }
        return result;
    }

    @Override
    public List<TMetpSubmitRow> listErrors(String batchNo) {
        return this.metpSubmitRowMapper.selectList(new QueryWrapper<TMetpSubmitRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL).eq("del_flag", 0)
                .orderByAsc("row_no"));
    }
}
