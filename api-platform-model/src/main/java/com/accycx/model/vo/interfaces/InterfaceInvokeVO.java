package com.accycx.model.vo.interfaces;

import com.accycx.model.entity.InterfaceInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "接口top5查询返回体")
public class InterfaceInvokeVO extends InterfaceInfo implements Serializable {

    /**
     * 接口总调用次数 (用于 ECharts 柱状图渲染)
     */
    private Integer totalNum;

    @Serial
    private static final long serialVersionUID = 1L;
}