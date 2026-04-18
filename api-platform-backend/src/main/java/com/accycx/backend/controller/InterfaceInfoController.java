package com.accycx.backend.controller;


import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.Method;
import com.accycx.common.AuthCheck;
import com.accycx.common.enums.InterfaceInfoStatus;
import com.accycx.common.utils.AuthUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.accycx.backend.service.InterfaceInfoService;
import com.accycx.backend.service.UserService;
import com.accycx.common.BaseResponse;
import com.accycx.common.enums.ErrorCode;
import com.accycx.common.utils.ResultUtils;
import com.accycx.model.dto.interfaceinfo.InterfaceInfoAddRequest;
import com.accycx.model.dto.interfaceinfo.InterfaceInfoInvokeRequest;
import com.accycx.model.dto.interfaceinfo.InterfaceInfoQueryRequest;
import com.accycx.model.dto.interfaceinfo.InterfaceInfoUpdateRequest;
import com.accycx.model.entity.InterfaceInfo;
import com.accycx.model.entity.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/**
 * 接口管理 API
 */
@RestController
@RequestMapping("/interfaceInfo")
@Tag(name = "接口管理",description = "管理员和用户对API接口的增删改查")
@Slf4j
public class InterfaceInfoController {

    @Resource
    private InterfaceInfoService interfaceInfoService;

    @Resource
    private UserService userService;

    @Value("${api.gateway.host}")
    private String gatewayHost; //动态获取网关地址


    /**
     * 创建接口
     */
    @PostMapping
    @Operation(summary = "创建新接口")
    @AuthCheck(mustRole = "admin") //只有管理员才能发布新接口
    public BaseResponse<Long> addInterfaceInfo(@RequestBody InterfaceInfoAddRequest interfaceInfoAddRequest,HttpServletRequest request){

        if(interfaceInfoAddRequest == null){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }

//        DTO转实体类
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        BeanUtils.copyProperties(interfaceInfoAddRequest,interfaceInfo);

//        校验参数
        interfaceInfoService.validInterfaceInfo(interfaceInfo,true);

//        动态获取当前登录用户的ID
        User loginUser = userService.getLoginUser(request);
        interfaceInfo.setUserId(loginUser.getId());

        interfaceInfo.setStatus(InterfaceInfoStatus.OFFLINE.getValue());
        boolean result = interfaceInfoService.save(interfaceInfo);
        if(!result){
            return ResultUtils.error(ErrorCode.OPERATION_ERROR,"创建接口失败");
        }
        return ResultUtils.success(interfaceInfo.getId());
    }

    /**
     * 删除接口
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除接口")
    @AuthCheck(mustRole = "admin") //只有管理员才能删除接口
    public BaseResponse<Boolean> deleteInterfaceInfo(@PathVariable("id") Long id){
        if(id == null || id <= 0){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }
        boolean result = interfaceInfoService.removeById(id);
        return ResultUtils.success(result);

    }

    /**
     * 更新接口
     */
    @PutMapping
    @Operation(summary = "更新接口")
    @AuthCheck(mustRole = "admin") //只有管理员才能更新接口
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
    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取接口详细信息")
    public BaseResponse<InterfaceInfo> getInterfaceInfoById(@PathVariable("id") Long id){
        if(id <= 0){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }
        InterfaceInfo interfaceInfo = interfaceInfoService.getById(id);
        return ResultUtils.success(interfaceInfo);
    }
    /**
     * 发布接口（Online）
     */
    @PostMapping("/{id}/online")
    @Operation(summary = "发布接口")
    @AuthCheck(mustRole = "admin") //只有管理员才能发布接口
    @SuppressWarnings("Duplicates")
    public BaseResponse<Boolean> onlineInterfaceInfo(@PathVariable("id") Long id,HttpServletRequest request){
        if(id == null || id <= 0){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }
//        1.校验接口是否存在
        InterfaceInfo oldInterfaceInfo = interfaceInfoService.getById(id);
        if(oldInterfaceInfo == null){
            return ResultUtils.error(ErrorCode.NOT_FOUND_ERROR,"接口不存在");
        }

//        2.动态获取当前管理员的AK/SK
        User loginUser = userService.getLoginUser(request);
        String accessKey = loginUser.getAccessKey();
        String secretKey = loginUser.getSecretKey();

//        动态拼接请求URL和方法
        String url = gatewayHost + oldInterfaceInfo.getUrl();
        String methodStr = oldInterfaceInfo.getMethod();

//        尝试从数据库获取该接口的标准请求参数,如果没有，给个空JSON兜底防报错
        String requestParams = StringUtils.isNotBlank(oldInterfaceInfo.getRequestParams()) ? oldInterfaceInfo.getRequestParams() : "{}";

        log.info("开始进行接口连通性测试，目标URL：{}，请求方法：{}", url, methodStr);

//        动态网络调用与容错处理
        try{
            Method httpMethod = Method.valueOf(methodStr.toUpperCase());

            try(HttpResponse response = HttpRequest.of(url)
                    .method(httpMethod)
                    .addHeaders(AuthUtils.getHeaderMap(requestParams,accessKey,secretKey))
                    .body(requestParams)
                    .timeout(5000) //设置超时时间为5秒,防止接口无响应导致发布卡死
                    .execute()){
                int status = response.getStatus();
                log.info("接口测试完毕，响应状态码：{}", status);

//                只要网关没有返回404、500、502、503等系统致命错误，说明接口是通的
                if(status >= 404 && status != 405){
                    return ResultUtils.error(ErrorCode.SYSTEM_ERROR,"接口连通性测试失败，网关返回异常状态码: " + status);
                }
            }
        } catch(IllegalArgumentException e){
            log.error("不支持的HTTP方法：{}",methodStr,e);
            return ResultUtils.error(ErrorCode.PARAMS_ERROR,"不支持的HTTP方法："+ methodStr);
        } catch(Exception e){
            log.error("接口连通性测试网络异常",e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR,"接口连通性测试失败，网络异常: " + e.getMessage());
        }
//        5.测试通过，更新接口状态为上线
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setId(id);
        interfaceInfo.setStatus(InterfaceInfoStatus.ONLINE.getValue());
        boolean result = interfaceInfoService.updateById(interfaceInfo);
        return ResultUtils.success(result);
    }

    /**
     * 下线接口（Offline）
     */
    @PostMapping("/{id}/offline")
    @Operation(summary = "下线接口")
    @AuthCheck(mustRole = "admin")
    @SuppressWarnings("Duplicates")
    public BaseResponse<Boolean> offlineInterfaceInfo(@PathVariable("id") Long id){
        if(id == null || id <= 0){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }
//        检验接口是否存在
        InterfaceInfo oldInterfaceInfo = interfaceInfoService.getById(id);
        if(oldInterfaceInfo == null){
            return ResultUtils.error(ErrorCode.NOT_FOUND_ERROR,"接口不存在");
        }
//        更新接口状态为下线
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setId(id);
        interfaceInfo.setStatus(InterfaceInfoStatus.OFFLINE.getValue());
        boolean result = interfaceInfoService.updateById(interfaceInfo);

        log.info("管理员成功下线接口，接口ID：{}", id);
        return ResultUtils.success(result);
    }


    /**
     * 在线调用（测试）接口
     */
    @PostMapping("/invoke")
    @Operation(summary = "在线调用测试接口")
    @SuppressWarnings("Duplicates")
    public BaseResponse<Object> invokeInterfaceInfo(@RequestBody InterfaceInfoInvokeRequest invokeRequest, HttpServletRequest request) {
//        1.校验参数
        if (invokeRequest == null || invokeRequest.getId() <= 0) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }

//        2.判断接口是否存在
        InterfaceInfo oldInterfaceInfo = interfaceInfoService.getById(invokeRequest.getId());
        if (oldInterfaceInfo == null) {
            return ResultUtils.error(ErrorCode.NOT_FOUND_ERROR, "接口不存在");
        }

//        3.判断接口状态是否开启
        if (!oldInterfaceInfo.getStatus().equals(InterfaceInfoStatus.ONLINE.getValue())) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "接口已关闭，无法调用");
        }
//         4.获取当前登录的用户信息
        User loginUser = userService.getLoginUser(request);
        String accessKey = loginUser.getAccessKey();
        String secretKey = loginUser.getSecretKey();

//        5.获取前端用户输入的JSON参数
        String userRequestParams = invokeRequest.getUserRequestParams();
//        如果用户没填参数，给个默认空JSON，防止签名报错
        if(StringUtils.isBlank(userRequestParams)){
            userRequestParams = "{}";
        }
//        6.动态拼接网关URL
        String url = gatewayHost+ oldInterfaceInfo.getUrl(); //网关地址 + 接口路径
        String method = oldInterfaceInfo.getMethod(); //接口方法
        log.info("开始进行接口在线测试, 目标URL: {}, 方法: {}", url, method);
//        动态网络调用与容错处理
        try {
//            将字符串转化为Hutool认识的HTTP Method枚举
            Method httpMethod = Method.valueOf(method.toUpperCase());
//            动态发起请求，可以适配所有Method方法
            try (HttpResponse response = cn.hutool.http.HttpRequest.of(url)
                    .method(httpMethod) //动态塞入请求方法
                    .addHeaders(AuthUtils.getHeaderMap(userRequestParams, accessKey, secretKey))//塞入鉴权信息
                    .body(userRequestParams) //塞入用户请求参数
                    .execute()) {//发起请求
//                只要走出了上面的括号，网络流就会自动安全关闭
                String result = response.body();
                int status = response.getStatus();
                log.info("接口在线测试完毕，响应状态码：{}", status);
//                将网关返回的真实内容透传给前端
                return ResultUtils.success(result);
                }
            } catch (IllegalArgumentException e) {
                log.error("不支持的HTTP方法：{}", method, e);
                return ResultUtils.error(ErrorCode.PARAMS_ERROR, "不支持的HTTP方法：" + method);
            } catch (Exception e) {
                log.error("接口在线测试网络异常", e);
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "接口调用失败，错误信息：" + e.getMessage());
            }

    }

    /**
     * 分页查询接口列表(封装了模糊查询逻辑)
     */

    @GetMapping("/page")
    @Operation(summary = "分页查询接口列表")
    public BaseResponse<Page<InterfaceInfo>> listInterfaceInfoByPage(InterfaceInfoQueryRequest interfaceInfoQueryRequest){

        if(interfaceInfoQueryRequest == null){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }


        long current = interfaceInfoQueryRequest.getCurrent();
        long size = interfaceInfoQueryRequest.getPageSize();
        String sortField = interfaceInfoQueryRequest.getSortField();
        String sortOrder = interfaceInfoQueryRequest.getSortOrder();
        String name = interfaceInfoQueryRequest.getName(); //模糊查询参数
        String description = interfaceInfoQueryRequest.getDescription(); //模糊查询参数

        //限制：size不能大于50
        if(size>50){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR,"每页条数不能超过50");
        }

        QueryWrapper<InterfaceInfo> queryWrapper = new QueryWrapper<>();
//        模糊查询：如果name不为空，则匹配数据库中包含该字符串的记录
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        queryWrapper.like(StringUtils.isNotBlank(description), "description", description);

//        排序逻辑
        queryWrapper.orderBy(StringUtils.isNotBlank(sortField),
                sortOrder.equals("ascend"), sortField);
        Page<InterfaceInfo> interfaceInfoPage = interfaceInfoService.page(new Page<>(current,size),queryWrapper);
        return ResultUtils.success(interfaceInfoPage);
    }

}










