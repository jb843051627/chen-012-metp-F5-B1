package com.fc.v2.service.impl;

import org.springframework.stereotype.Service;

import com.fc.v2.mapper.auto.TMetpMoveBillMapper;
import com.fc.v2.model.auto.TMetpMoveBill;
import com.fc.v2.service.ITMetpMoveBillService;

/**
 * 台站迁移变更逐级核准单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMetpMoveBillServiceImpl implements ITMetpMoveBillService {

    /** 层级 0..3，共四层；在末层签满即核讫 */
    private static final int MIN_NODE = 0;
    private static final int MAX_NODE = 3;
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TMetpMoveBillMapper metpMoveBillMapper;

    @Override
    public TMetpMoveBill selectTMetpMoveBillById(Long id) {
        return this.metpMoveBillMapper.selectById(id);
    }

    /**
     * 签批一票：两扇门须齐备——任一人模式一票过层，两名点齐模式须签满本层应签人数才过层。
     * 过层后已签计数清零；末层签满即置已核讫。已办结（核讫/打回）的单子不得再签。
     */
    @Override
    public TMetpMoveBill approve(Long id, String approver, String comment) {
        TMetpMoveBill r = this.metpMoveBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        if (isClosed(r)) {
            return null;
        }
        int node = r.getNodeNo() == null ? MIN_NODE : r.getNodeNo();
        int signed = r.getSignCount() == null ? 0 : r.getSignCount();
        signed++;
        int required = requiredSigns(r);
        if (signed < required) {
            r.setSignCount(Integer.valueOf(signed));
            r.setStatus(STATUS_RUNNING);
            this.metpMoveBillMapper.updateById(r);
            return r;
        }
        // 本层签齐：末层核讫，否则上一层重新起签
        if (node >= MAX_NODE) {
            r.setNodeNo(Integer.valueOf(MAX_NODE));
            r.setSignCount(Integer.valueOf(required));
            r.setStatus(STATUS_PASS);
        } else {
            r.setNodeNo(Integer.valueOf(node + 1));
            r.setSignCount(Integer.valueOf(0));
            r.setStatus(STATUS_RUNNING);
        }
        this.metpMoveBillMapper.updateById(r);
        return r;
    }

    /** 否决：置已打回；已经办结的单子不得再动 */
    @Override
    public TMetpMoveBill reject(Long id, String approver, String comment) {
        TMetpMoveBill r = this.metpMoveBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        if (isClosed(r)) {
            return null;
        }
        r.setStatus(STATUS_VETO);
        this.metpMoveBillMapper.updateById(r);
        return r;
    }

    /** 退回上一环节：本层签票一笔勾销，回到上一层重新起签；首层无处可退、办结单不准退 */
    @Override
    public TMetpMoveBill rollback(Long id, String comment) {
        TMetpMoveBill r = this.metpMoveBillMapper.selectById(id);
        if (r == null || isClosed(r)) {
            return null;
        }
        int node = r.getNodeNo() == null ? MIN_NODE : r.getNodeNo();
        if (node <= MIN_NODE) {
            return null;
        }
        r.setNodeNo(Integer.valueOf(node - 1));
        r.setSignCount(Integer.valueOf(0));
        r.setStatus(STATUS_RUNNING);
        this.metpMoveBillMapper.updateById(r);
        return r;
    }

    /** 本层应签数：两名点齐看 needCount（至少 1），任一人模式始终 1 */
    private int requiredSigns(TMetpMoveBill r) {
        int mode = r.getSignMode() == null ? MODE_OR : r.getSignMode();
        if (mode == MODE_AND) {
            int need = r.getNeedCount() == null ? 0 : r.getNeedCount();
            return Math.max(need, 1);
        }
        return 1;
    }

    private boolean isClosed(TMetpMoveBill r) {
        return r.getStatus() != null
                && (r.getStatus() == STATUS_PASS || r.getStatus() == STATUS_VETO);
    }
}
