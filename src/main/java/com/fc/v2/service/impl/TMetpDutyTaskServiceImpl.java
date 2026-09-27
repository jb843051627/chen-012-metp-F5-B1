package com.fc.v2.service.impl;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

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

    /** 每日可派发的时辰窗：[2, 5)，凌晨两点到五点 */
    private static final int WIN_FROM = 2;
    private static final int WIN_TO = 5;
    private static final long ONE_DAY_MS = 24L * 60 * 60 * 1000;
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TMetpDutyTaskMapper metpDutyTaskMapper;

    @Override
    public TMetpDutyTask selectTMetpDutyTaskById(Long id) {
        return this.metpDutyTaskMapper.selectById(id);
    }

    /**
     * 该时刻可处理的条目：在派发时辰窗内、已进入提前派发期且未越过到期时刻、尚未派发且未删。
     * 到期比较全在 Java 侧用毫秒长整进行（不把 java.util.Date 作 JDBC 参数，避开
     * serverTimezone 偏移）；恰好到期（at == dueAt）算在内。
     */
    @Override
    public List<TMetpDutyTask> listDue(Date at) {
        List<TMetpDutyTask> due = new java.util.ArrayList<TMetpDutyTask>();
        if (at == null || !inDispatchWindow(at)) {
            return due;
        }
        List<TMetpDutyTask> all = this.metpDutyTaskMapper.selectList(new QueryWrapper<TMetpDutyTask>()
                .eq("del_flag", 0).eq("status", STATUS_WAIT));
        for (TMetpDutyTask r : all) {
            if (r.getDueAt() == null) {
                continue;
            }
            long aheadDays = r.getAmount() == null ? 0L : Math.max(0L, r.getAmount().longValue());
            long openAt = r.getDueAt().getTime() - aheadDays * ONE_DAY_MS;
            long now = at.getTime();
            if (now >= openAt && now <= r.getDueAt().getTime()) {
                due.add(r);
            }
        }
        return due;
    }

    /** 执行一次：逐条派发并置已派；单条失败记派不出去、跳过继续，绝不因一条断流 */
    @Override
    public int runOnce(Date at) {
        List<TMetpDutyTask> due = listDue(at);
        int ok = 0;
        for (TMetpDutyTask r : due) {
            try {
                r.setStatus(Integer.valueOf(STATUS_DONE));
                if (this.metpDutyTaskMapper.updateById(r) > 0) {
                    ok++;
                }
            } catch (RuntimeException ex) {
                markFailed(r);
            }
        }
        return ok;
    }

    private void markFailed(TMetpDutyTask r) {
        try {
            r.setStatus(Integer.valueOf(STATUS_FAIL));
            this.metpDutyTaskMapper.updateById(r);
        } catch (RuntimeException ignored) {
            // 连失败状态都落不下也不连累其余条目
        }
    }

    private boolean inDispatchWindow(Date at) {
        Calendar c = Calendar.getInstance();
        c.setTime(at);
        int hour = c.get(Calendar.HOUR_OF_DAY);
        return hour >= WIN_FROM && hour < WIN_TO;
    }
}
