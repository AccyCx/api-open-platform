package com.accycx.backend.controller;


import com.accycx.apiclientsdk.client.ApiClient;
import com.accycx.backend.service.InterfaceInfoService;
import com.accycx.common.BaseResponse;
import com.accycx.common.ErrorCode;
import com.accycx.common.utils.ResultUtils;
import com.accycx.model.dto.common.DeleteRequest;
import com.accycx.model.dto.interfaceinfo.InterfaceInfoAddRequest;
import com.accycx.model.dto.interfaceinfo.InterfaceInfoInvokeRequest;
import com.accycx.model.dto.interfaceinfo.InterfaceInfoUpdateRequest;
import com.accycx.model.entity.InterfaceInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 接口管理 API
 */
@RestController
@RequestMapping("/interfaceInfo")
@Tag(name = "接口管理",description = "管理员和用户对API接口的增删改查")
public class InterfaceInfoController {

    @Resource
    private InterfaceInfoService interfaceInfoService;

    @Resource
    private ApiClient apiClient;

    // TODO: 这里还需要引入 UserService 获取当前登录用户的 ID，目前我们先写死或跳过.等后面完善网关拦截再补充

    /**
     * 创建接口
     */
    @PostMapping("/add")
    @Operation(summary = "发布新接口")
    public BaseResponse<Long> addInterfaceInfo(@RequestBody InterfaceInfoAddRequest interfaceInfoAddRequest){

        if(interfaceInfoAddRequest == null){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }

//        DTO转实体类
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        BeanUtils.copyProperties(interfaceInfoAddRequest,interfaceInfo);

//        校验参数
        interfaceInfoService.validInterfaceInfo(interfaceInfo,true);

//        这里我们先写死一个用户ID，后续完善了用户系统后再改成动态获取
        interfaceInfo.setUserId(1L);

        boolean result = interfaceInfoService.save(interfaceInfo);
        if(!result){
            return ResultUtils.error(ErrorCode.OPERATION_ERROR,"创建接口失败");
        }
        return ResultUtils.success(interfaceInfo.getId());
    }

    /**
     * 删除接口
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除接口")
    public BaseResponse<Boolean> deleteInterfaceInfo(@RequestBody DeleteRequest deleteRequest){
        if(deleteRequest == null || deleteRequest.getId() <= 0){
            return ResultUtils.error(ErrorCode.OPERATION_ERROR);
        }
        boolean result = interfaceInfoService.removeById(deleteRequest.getId());
        return ResultUtils.success(result);

    }

    /**
     * 更新接口
     */
    @PostMapping("/update")
    @Operation(summary = "更新接口")
    public BaseResponse<Boolean> updateInterfaceInfo(@RequestBody InterfaceInfoUpdateRequest interfaceInfoUpdateRequest){
        if(interfaceInfoUpdateRequest == null || interfaceInfoUpdateRequest.getId()<=0){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }

        InterfaceInfo interfaceInfo = new InterfaceInfo();
        BeanUtils.copyProperties(interfaceInfoUpdateRequest,interfaceInfo);

//        校验参数（非新增参数）
        interfaceInfoService.validInterfaceInfo(interfaceInfo,false);

        boolean result = interfaceInfoService.updateById(interfaceInfo);
        return ResultUtils.success(result);
    }

    /**
     * 根据ID查询接口详细信息
     */
    @GetMapping("/get")
    @Operation(summary = "根据ID获取接口详细信息")
    public BaseResponse<InterfaceInfo> getInterfaceInfoById(Long id){
        if(id <= 0){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }
        InterfaceInfo interfaceInfo = interfaceInfoService.getById(id);
        return ResultUtils.success(interfaceInfo);
    }

    /**
     * 在线调用（测试）接口
     */
    @PostMapping("/invoke")
    @Operation(summary = "在线调用测试接口")
    public BaseResponse<Object> invokeINterfaceInfo(@RequestBody InterfaceInfoInvokeRequest invokeRequest){
//        1.校验参数
        if(invokeRequest == null || invokeRequest.getId() <=0){
            return ResultUtils.success(ErrorCode.PARAMS_ERROR);
        }

//        2.判断接口是否存在
        long id = invokeRequest.getId();
        InterfaceInfo oldInterfaceInfo = interfaceInfoService.getById(id);
        if(oldInterfaceInfo == null){
            return ResultUtils.error(ErrorCode.NOT_FOUND_ERROR,"接口不存在");
        }

//        3.判断接口状态是否开启（1是开启）
        if(oldInterfaceInfo.getStatus() != 1){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR,"接口已关闭");
        }

//        4.发起实际调用
//        这里应该根据oldInterfaceInfo.getUrl()动态去调
//        但是目前为了跑通主流程，先用if-else写死判断，只测试"/name/user"接口
        String userRequestParams = invokeRequest.getUserRequestParams();
        if(oldInterfaceInfo.getUrl().contains("/name/user")){
//            利用Hutool将前端传来的JSON字符串反序列化为User对象
        com.accycx.apiclientsdk.model.User user = cn.hutool.json.JSONUtil.toBean(userRequestParams, com.accycx.apiclientsdk.model.User.class);

//        主后台使用装配好的SDK客户端发起真实网络请求
            String result = apiClient.getUserNameByPost(user);
            return ResultUtils.success(result);
        }
        return ResultUtils.error(ErrorCode.PARAMS_ERROR,"目前仅支持测试/name/user接口");
    }

}








