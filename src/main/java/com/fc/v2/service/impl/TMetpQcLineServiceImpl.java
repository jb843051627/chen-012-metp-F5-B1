package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMetpQcLineMapper;
import com.fc.v2.model.auto.TMetpQcLine;
import com.fc.v2.service.ITMetpQcLineService;

/**
 * 观测数据质量控制界限 Service业务层处理（rule-eval 形状）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMetpQcLineServiceImpl implements ITMetpQcLineService {

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

    private static final BigDecimal INPUT_MIN = BigDecimal.ZERO;
    private static final BigDecimal INPUT_MAX = new BigDecimal("100");

    @javax.annotation.Resource
    private TMetpQcLineMapper metpQcLineMapper;

    @Override
    public TMetpQcLine selectTMetpQcLineById(Long id) {
        return this.metpQcLineMapper.selectById(id);
    }

    /** at 时刻可用规则：未删、现行、在生效窗内；按优先级降序、ruleCode 降序排列 */
    @Override
    public List<TMetpQcLine> listAvailable(Date at) {
        List<TMetpQcLine> result = new ArrayList<TMetpQcLine>();
        if (at == null) {
            return result;
        }
        List<TMetpQcLine> all = this.metpQcLineMapper.selectList(new QueryWrapper<TMetpQcLine>()
                .eq("del_flag", 0).eq("status", 0));
        for (TMetpQcLine r : all) {
            if (effectiveAt(r, at)) {
                result.add(r);
            }
        }
        result.sort(PRIORITY_THEN_CODE_DESC);
        return result;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        if (!inRange(input)) {
            return 0;
        }
        TMetpQcLine rule = findRule(ruleCode, at);
        if (rule == null) {
            return 0;
        }
        return levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null || !inRange(input)) {
            return 0;
        }
        List<TMetpQcLine> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return 0;
        }
        return levelOf(avail.get(0), input);
    }

    /** 单条是否可用，口径与定位/列表/计数同出一源 */
    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TMetpQcLine r = this.metpQcLineMapper.selectById(id);
        return r != null && effectiveAt(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        return listAvailable(at).size();
    }

    private TMetpQcLine findRule(String ruleCode, Date at) {
        List<TMetpQcLine> hit = this.metpQcLineMapper.selectList(new QueryWrapper<TMetpQcLine>()
                .eq("del_flag", 0).eq("status", 0).eq("rule_code", ruleCode));
        TMetpQcLine newest = null;
        for (TMetpQcLine r : hit) {
            if (!effectiveAt(r, at)) {
                continue;
            }
            // 同窗内多版本取启用之日最新者
            if (newest == null
                    || (r.getEffStart() != null && r.getEffStart().after(newest.getEffStart()))) {
                newest = r;
            }
        }
        return newest;
    }

    /**
     * 唯一的"某时刻生效"口径：未删 + 现行 + 已启用（eff_start &lt;= at）
     * 且未交棒（eff_end 为空或 at &lt; eff_end，交棒当日不含）。
     * 时段在 Java 侧比较，避免把 java.util.Date 直接作 JDBC 参数撞 serverTimezone 偏移。
     */
    private boolean effectiveAt(TMetpQcLine r, Date at) {
        if (r.getDelFlag() != null && r.getDelFlag() != 0) {
            return false;
        }
        if (r.getStatus() != null && r.getStatus() != 0) {
            return false;
        }
        if (r.getEffStart() != null && r.getEffStart().after(at)) {
            return false;
        }
        if (r.getEffEnd() != null && !r.getEffEnd().after(at)) {
            return false;
        }
        return true;
    }

    /** 入参合法域：[0, 100]，单规则与多规则求值一个眼色 */
    private boolean inRange(BigDecimal input) {
        return input.compareTo(INPUT_MIN) >= 0 && input.compareTo(INPUT_MAX) <= 0;
    }

    /** 分档：等于上限取高一档（与档案种子册"等于上限算高一档"同口径） */
    private int levelOf(TMetpQcLine rule, BigDecimal input) {
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) < 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) < 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) < 0) {
            return 3;
        }
        return 4;
    }
}
