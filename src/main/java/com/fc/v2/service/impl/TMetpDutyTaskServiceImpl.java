package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMetpDutyTaskMapper;
import com.fc.v2.model.auto.TMetpDutyTask;
import com.fc.v2.service.ITMetpDutyTaskService;

/**
 * 标定巡检到期派工条目 Service业务层处理（scheduling-job 形状：周期执行）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMetpDutyTaskServiceImpl implements ITMetpDutyTaskService {

    private static final Logger logger = LoggerFactory.getLogger(TMetpDutyTaskServiceImpl.class);

    /** 单条提前派发自然日数的允许窗口 [2, 5]（含两端） */
    private static final int WIN_FROM = 2;
    private static final int WIN_TO = 5;
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TMetpDutyTaskMapper metpDutyTaskMapper;

    @Override
    public TMetpDutyTask selectTMetpDutyTaskById(Long id) {
        return this.metpDutyTaskMapper.selectById(id);
    }

    @Override
    public List<TMetpDutyTask> listDue(Date at) {
        List<TMetpDutyTask> all = this.metpDutyTaskMapper.selectList(new QueryWrapper<TMetpDutyTask>()
                .eq("del_flag", 0)
                .eq("status", STATUS_WAIT));
        List<TMetpDutyTask> due = new java.util.ArrayList<TMetpDutyTask>();
        for (TMetpDutyTask r : all) {
            // 执行窗口内（提前日数 2..5）+ 已到日终截止时刻（恰好到期也算，含边界）
            if (r.getDueAt() != null && !at.before(r.getDueAt()) && inWindow(r.getAmount())) {
                due.add(r);
            }
        }
        return due;
    }

    @Override
    public int runOnce(Date at) {
        List<TMetpDutyTask> due = listDue(at);
        // 册上无活到点也稳稳收场，不许摸空册子抛错
        if (due.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (TMetpDutyTask r : due) {
            try {
                r.setStatus(STATUS_DONE);
                if (this.metpDutyTaskMapper.updateById(r) > 0) {
                    ok++;
                }
            } catch (Exception e) {
                // 单条派不出去记"派不出去"，跳过继续，不许一条出格拽回整批
                logger.warn("标定巡检到期派工失败 itemNo={}", r.getItemNo(), e);
                r.setStatus(STATUS_FAIL);
                try {
                    this.metpDutyTaskMapper.updateById(r);
                } catch (Exception ignore) {
                    logger.warn("派工失败状态回写也未成功 itemNo={}", r.getItemNo());
                }
            }
        }
        return ok;
    }

    private boolean inWindow(BigDecimal amount) {
        if (amount == null) {
            return false;
        }
        return amount.compareTo(BigDecimal.valueOf(WIN_FROM)) >= 0
                && amount.compareTo(BigDecimal.valueOf(WIN_TO)) <= 0;
    }
}
