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

    /** 关口契约：stage 0..5，共六道关 */
    private static final int MAX_STAGE = 5;
    /** 卡的落定 0在办 1已解除 2已归档；只有 0 在办还能流转 */
    private static final int STATUS_ACTIVE = 0;
    private static final int STATUS_TERMINAL = 2;

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
        TMetpFixCard r = this.metpFixCardMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        // 已归档的卡落了锁；已走到最后一关也不许再往前推
        if (isTerminal(r) || st >= MAX_STAGE) {
            return null;
        }
        // 朝前走只过一道关（+1），与回退的 -1 对称，不许两调各唱各的调
        st = st + 1;
        r.setStage(st);
        r.setStatus(st >= MAX_STAGE ? STATUS_TERMINAL : STATUS_ACTIVE);
        r.setLastAction(remark);
        this.metpFixCardMapper.updateById(r);
        return r;
    }

    @Override
    public TMetpFixCard rollback(Long id, String remark) {
        TMetpFixCard r = this.metpFixCardMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        // 已归档的卡落了锁；还在头道关之前无关可退
        if (isTerminal(r) || st <= 0) {
            return null;
        }
        // 朝回退只退一道关（-1）
        st = st - 1;
        r.setStage(st);
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.metpFixCardMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TMetpFixCard r = this.metpFixCardMapper.selectById(id);
        if (r == null) {
            return false;
        }
        // 已办结（归档）不得再改
        if (isTerminal(r)) {
            return false;
        }
        r.setContent(remark);
        return this.metpFixCardMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TMetpFixCard r = this.metpFixCardMapper.selectById(id);
        if (r == null) {
            return false;
        }
        // 已办结（归档）不得删除，与改的口径归一
        if (isTerminal(r)) {
            return false;
        }
        return this.metpFixCardMapper.deleteById(id) > 0;
    }

    /** 终态判定一处定音：流转、改记、删除都照这一把尺——非"在办(0)"即落锁 */
    private boolean isTerminal(TMetpFixCard r) {
        return r.getStatus() != null && r.getStatus() != STATUS_ACTIVE;
    }

}
