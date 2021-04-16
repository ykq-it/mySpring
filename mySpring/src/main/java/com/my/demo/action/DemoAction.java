package com.my.demo.action;

import com.my.demo.service.DemoService;
import com.my.mvcFramework.annotation.MyAutowired;
import com.my.mvcFramework.annotation.MyController;
import com.my.mvcFramework.annotation.MyRequestMapping;
import com.my.mvcFramework.annotation.MyRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @ClassName DemoController
 * @Description TODO
 * @Author ykq
 * @Date 2020/4/29
 * @Version v1.0.0
 * http://localhost:8080/mySpring_war_exploded/demo/query?name=1
 */
@MyController
@MyRequestMapping("/demo")
public class DemoAction {

    @MyAutowired
    private DemoService demoService;

    @MyRequestMapping("/query")
    public void query(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, @MyRequestParam("name") String name) {
        String result = demoService.get(name);

        try {
            httpServletResponse.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
