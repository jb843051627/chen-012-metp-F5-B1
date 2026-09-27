package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMetpFixCardMapper;
import com.fc.v2.model.auto.TMetpFixCard;
import com.fc.v2.service.ITMetpFixCardService;

/**
 * 探测环境违规整改事务卡 Service业务层处理（state-machine 形状：单据流转）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMetpFixCardServiceImpl implements ITMetpFixCardService {

    /** 关口 0..5，共六道 */
    private static final int MIN_STAGE = 0;
    private static final int MAX_STAGE = 5;
    private static final int STATUS_ACTIVE = 0;
    private static final int STATUS_RESOLVED = 1;
    private static final int STATUS_ARCHIVED = 2;

    @javax.annotation.Resource
    private TMetpFixCardMapper metpFixCardMapper;

    @Override
    public TMetpFixCard selectTMetpFixCardById(Long id) {
        return this.metpFixCardMapper.selectById(id);
    }

    @Override
    public List<TMetpFixCard> selectTMetpFixCardList(QueryWrapper<TMetpFixCard> queryWrapper) {
        return this.metpFixCardMapper.selectList(queryWrapper);
    }

    @Override
    public TMetpFixCard advance(Long id, String remark) {
        return move(id, remark, 1);
    }

    @Override
    public TMetpFixCard rollback(Long id, String remark) {
        return move(id, remark, -1);
    }

    /**
     * 朝前走、朝回退共用一支：先验"卡还在不在、办结没有"两扇门，再各挪一关。
     * 已到头关不得再推，已在底关不得再退，越界一律返回 null。
     */
    private TMetpFixCard move(Long id, String remark, int delta) {
        TMetpFixCard r = this.metpFixCardMapper.selectById(id);
        if (r == null) {
            return null;
        }
        if (isClosed(r)) {
            return null;
        }
        int st = r.getStage() == null ? MIN_STAGE : r.getStage();
        int next = st + delta;
        if (next < MIN_STAGE || next > MAX_STAGE) {
            return null;
        }
        r.setStage(Integer.valueOf(next));
        if (next == MAX_STAGE) {
            r.setStatus(STATUS_RESOLVED);
        } else {
            r.setStatus(STATUS_ACTIVE);
        }
        r.setLastAction(remark);
        this.metpFixCardMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TMetpFixCard r = this.metpFixCardMapper.selectById(id);
        if (r == null || isClosed(r)) {
            return false;
        }
        r.setContent(remark);
        return this.metpFixCardMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TMetpFixCard r = this.metpFixCardMapper.selectById(id);
        if (r == null || isClosed(r)) {
            return false;
        }
        return this.metpFixCardMapper.deleteById(id) > 0;
    }

    /** 已解除、已归档即办结，两扇门都落了锁：改不得、删不得、流转不得 */
    private boolean isClosed(TMetpFixCard r) {
        return r.getStatus() != null
                && (r.getStatus() == STATUS_RESOLVED || r.getStatus() == STATUS_ARCHIVED);
    }

}
