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

    /** 层级契约：node_no 0..3，共四层 */
    private static final int MAX_NODE = 3;
    /** 同层核准方式 0任一人 1两名点齐 */
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;
    /** 报批情形 0在核 1已核讫 2已打回 */
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TMetpMoveBillMapper metpMoveBillMapper;

    @Override
    public TMetpMoveBill selectTMetpMoveBillById(Long id) {
        return this.metpMoveBillMapper.selectById(id);
    }

    @Override
    public TMetpMoveBill approve(Long id, String approver, String comment) {
        TMetpMoveBill r = this.metpMoveBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        // 已核讫 / 已打回都落了锁，不许再签
        if (isFinished(r)) {
            return null;
        }
        int mode = r.getSignMode() == null ? MODE_OR : r.getSignMode();
        int signed = r.getSignCount() == null ? 0 : r.getSignCount() + 1;
        r.setSignCount(signed);
        if (mode == MODE_AND) {
            // 两名点齐：本层应签人数须齐备，只上闩一边等于没上
            int need = r.getNeedCount() == null ? 2 : r.getNeedCount();
            if (signed < need) {
                this.metpMoveBillMapper.updateById(r);
                return r;
            }
        }
        // OR 任一人签即过；AND 点齐后过——两扇门的法则在此归一
        passThisNode(r);
        this.metpMoveBillMapper.updateById(r);
        return r;
    }

    @Override
    public TMetpMoveBill reject(Long id, String approver, String comment) {
        TMetpMoveBill r = this.metpMoveBillMapper.selectById(id);
        // 签批须有落笔人，与 approve 同一道门槛
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        if (isFinished(r)) {
            return null;
        }
        // 否决即把单子落成"已打回"，不许原样空更新、抛错收兵
        r.setStatus(STATUS_VETO);
        this.metpMoveBillMapper.updateById(r);
        return r;
    }

    @Override
    public TMetpMoveBill rollback(Long id, String comment) {
        TMetpMoveBill r = this.metpMoveBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        if (isFinished(r)) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        if (node <= 0) {
            return null;
        }
        // 退回上一层，本层已签的票一并清零重签
        r.setNodeNo(node - 1);
        r.setSignCount(0);
        r.setStatus(STATUS_RUNNING);
        this.metpMoveBillMapper.updateById(r);
        return r;
    }

    /**
     * 本层签齐：未到末层则进下一层（票清零、仍在核）；已在末层(3)签齐则核讫，
     * node_no 停在 3 不越出 0..3 契约。四层 0/1/2/3 各签齐一回方讫。
     */
    private void passThisNode(TMetpMoveBill r) {
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        if (node >= MAX_NODE) {
            r.setStatus(STATUS_PASS);
            return;
        }
        r.setNodeNo(node + 1);
        r.setSignCount(0);
        r.setStatus(STATUS_RUNNING);
    }

    /** 终态一处定音：非"在核(0)"即落锁 */
    private boolean isFinished(TMetpMoveBill r) {
        return r.getStatus() != null && r.getStatus() != STATUS_RUNNING;
    }
}
