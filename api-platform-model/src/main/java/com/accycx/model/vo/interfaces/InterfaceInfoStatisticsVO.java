package com.accycx.model.vo.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(description = "接口统计返回体")
public class InterfaceInfoStatisticsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 接口总数
     */
    private Long totalNum;

    /**
     * 已发布（上线）接口数
     */
    private Long onlineNum;

    /**
     * 已下线接口数
     */
    private Long offlineNum;
}
