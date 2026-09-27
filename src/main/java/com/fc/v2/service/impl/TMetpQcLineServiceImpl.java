package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMetpQcLineMapper;
import com.fc.v2.model.auto.TMetpQcLine;
import com.fc.v2.service.ITMetpQcLineService;
import com.fc.v2.util.MetpGradeUtil;

/**
 * 观测数据质量控制界限 Service业务层处理（rule-eval 形状）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMetpQcLineServiceImpl implements ITMetpQcLineService {

    /** 合法取值区间两端：两处入口的越界校验共用一把尺 */
    private static final BigDecimal INPUT_MIN = BigDecimal.ZERO;
    private static final BigDecimal INPUT_MAX = new BigDecimal("100");

    /** 优先级降序；并列按 ruleCode 降序（确定性） */
    private static final Comparator<TMetpQcLine> PRIORITY_THEN_CODE_DESC = new Comparator<TMetpQcLine>() {
        @Override
        public int compare(TMetpQcLine a, TMetpQcLine b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    @javax.annotation.Resource
    private TMetpQcLineMapper metpQcLineMapper;

    @Override
    public TMetpQcLine selectTMetpQcLineById(Long id) {
        return this.metpQcLineMapper.selectById(id);
    }

    @Override
    public List<TMetpQcLine> listAvailable(Date at) {
        if (at == null) {
            return new java.util.ArrayList<TMetpQcLine>();
        }
        // 现行 + 未删 的全量取回，生效窗口在内存里用同一把尺过滤，新旧规口径不分家
        List<TMetpQcLine> all = this.metpQcLineMapper.selectList(new QueryWrapper<TMetpQcLine>()
                .eq("del_flag", 0).eq("status", 0));
        List<TMetpQcLine> avail = new java.util.ArrayList<TMetpQcLine>();
        for (TMetpQcLine r : all) {
            if (inEffect(r, at)) {
                avail.add(r);
            }
        }
        Collections.sort(avail, PRIORITY_THEN_CODE_DESC);
        return avail;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        if (outOfRange(input)) {
            return 0;
        }
        TMetpQcLine rule = findRule(ruleCode, at);
        return rule == null ? 0 : MetpGradeUtil.levelOf(rule.getTh1Max(), rule.getTh2Max(), rule.getTh3Max(), input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return 0;
        }
        if (outOfRange(input)) {
            return 0;
        }
        List<TMetpQcLine> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return 0;
        }
        TMetpQcLine top = avail.get(0);
        return MetpGradeUtil.levelOf(top.getTh1Max(), top.getTh2Max(), top.getTh3Max(), input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TMetpQcLine r = this.metpQcLineMapper.selectById(id);
        // 旁路复用：与列表/定位同一道可用性判定，一处看不住等于没管
        return r != null && isAvailable(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        return listAvailable(at).size();
    }

    /** 定位某代号在 at 时刻生效的版本：同代号多版本只认窗口内、优先级最高的一本 */
    private TMetpQcLine findRule(String ruleCode, Date at) {
        List<TMetpQcLine> hit = this.metpQcLineMapper.selectList(new QueryWrapper<TMetpQcLine>()
                .eq("del_flag", 0).eq("status", 0).eq("rule_code", ruleCode));
        TMetpQcLine picked = null;
        for (TMetpQcLine r : hit) {
            if (!inEffect(r, at)) {
                continue;
            }
            if (picked == null || PRIORITY_THEN_CODE_DESC.compare(r, picked) < 0) {
                picked = r;
            }
        }
        return picked;
    }

    /** 可用性一把尺：未删 + 现行 + 落在生效窗口 [eff_start, eff_end) 内 */
    private boolean isAvailable(TMetpQcLine r, Date at) {
        if (r.getDelFlag() != null && r.getDelFlag() != 0) {
            return false;
        }
        if (r.getStatus() != null && r.getStatus() != 0) {
            return false;
        }
        return inEffect(r, at);
    }

    /**
     * 生效窗口半开半闭 [启用之日, 交棒之日)：交棒当日即归新规，旧规不再算生效。
     * 两端任一未维护则该端不设限。
     */
    private boolean inEffect(TMetpQcLine r, Date at) {
        if (r.getEffStart() != null && at.before(r.getEffStart())) {
            return false;
        }
        if (r.getEffEnd() != null && !at.before(r.getEffEnd())) {
            return false;
        }
        return true;
    }

    private boolean outOfRange(BigDecimal input) {
        return input.compareTo(INPUT_MIN) < 0 || input.compareTo(INPUT_MAX) > 0;
    }
}
