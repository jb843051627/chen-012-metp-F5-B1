package com.fc.v2.util;

import java.math.BigDecimal;

/**
 * 气象档案相关各本账共用的阈值分档尺。
 *
 * <p>台站基础档案 {@code t_metp_stn_doc} 的三档上限是唯一源头，建档卡判档、
 * 质控界限判档都照此一把尺，不许两处各写一套比较。</p>
 *
 * <p>口径（与档案种子约定一致）：等于上限取高一档——
 * input 严格小于一档上限入一档；触到一档上限即入二档，依此类推；触到/超过三档上限入四档。
 * 某档上限未维护(null)则该档不设限，顺延判下一档。</p>
 *
 * @author fuce
 * @date 2026-09-27
 */
public final class MetpGradeUtil {

    private MetpGradeUtil() {
    }

    /**
     * 按三档上限判档，返回 1..4。
     *
     * @param th1Max 一档上限（含则高一档）
     * @param th2Max 二档上限（含则高一档）
     * @param th3Max 三档上限（含则高一档）
     * @param input  待判数值
     * @return 档位 1..4；input 为 null 返回 0
     */
    public static int levelOf(BigDecimal th1Max, BigDecimal th2Max, BigDecimal th3Max, BigDecimal input) {
        if (input == null) {
            return 0;
        }
        if (th1Max != null && input.compareTo(th1Max) < 0) {
            return 1;
        }
        if (th2Max != null && input.compareTo(th2Max) < 0) {
            return 2;
        }
        if (th3Max != null && input.compareTo(th3Max) < 0) {
            return 3;
        }
        return 4;
    }
}
