package com.accycx.apiplatforminterface.controller;


import com.accycx.apiplatforminterface.model.User;
import org.springframework.web.bind.annotation.*;

/**
 * 名称API
 * 提供查询名称的接口
 */
@RestController
@RequestMapping("/name")
public class NameController {

//    1.GET方式请求，参数在URL上（比如/name/get?name=xxx）
    @GetMapping("/get")
    public String getNameByGet(String name){
        return "GET 你的名字是：" + name;
    }

//    2.POST方式请求，参数在URL上或表单里
    @PostMapping("/post")
    public String getNameByPost(@RequestParam String name){
        return "POST 你的名字是：" + name;
    }

//    3.POST方式请求，参数在请求体（JSON）里面
    @PostMapping("/user")
    public String getUserNameByPost(@RequestBody User user){
        return "POST JSON 你的名字是：" + user.getUsername();
    }
}
