package com.my.mvcFramework.v1.servlet;

import com.my.mvcFramework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * @ClassName MyDispatcherServlet
 * @Description TODO
 * @Author ykq
 * @Date 2020/4/29
 * @Version v1.0.0
 */
public class MyDispatcherServlet extends HttpServlet {
    /** 声明一个配置文件的全局变量，加载配置文件时用来保存application.properties的参数 */
    private Properties contextConfig = new Properties();

    /** 扫描包时，保存class的名字 */
    private List<String> classNames = new ArrayList<>();

    /** IoC容器，即Map，存放的是扫描包下被@MyController和@MyService注解的类的实例 */
    private Map<String, Object> ioc = new HashMap<>();

    /** 保存url和被@MyRequestMapping修饰的Method的关系 */
    private Map<String, Method> handlerMapping = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            doDispatcher(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception!");
        }
    }

    /***
     * 功能描述: 按照请求，适配method
     * @author ykq
     * @date 2020/4/30 0:34
     * @param
     * @return void
     */
    private void doDispatcher(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath(); //当前页面所在的应用的名字
        // 去掉url中的项目名，并去掉多余的/
        url = url.replace(contextPath, "").replaceAll("/+", "/");

        // 判断处理去映射器中是否有改url
        if (!handlerMapping.containsKey(url)) {
            resp.getWriter().write("404 NOT FOUND!");
            return;
        }

        // 获取目标方法
        Method method = handlerMapping.get(url);

        // url带来的参数列表 TODO req.getParameterMap()get的是什么？
        Map<String, String[]> parameterMap = req.getParameterMap();

        // 获取方法的形参列表，形参列表有三类值，Request、Rresponse、自定义参数
        Class[] parametersTypes = method.getParameterTypes();

        // 保存赋值参数的位置
        Object[] paramValues = new Object[parametersTypes.length];

        // 遍历方法形参列表
        for (int i = 0; i < parametersTypes.length; i++) {
            Class parameterType = parametersTypes[i];
            // 判断参数是什么类型，分类讨论
            if (parameterType == HttpServletRequest.class) {
                paramValues[i] = req;
            } else if (parameterType == HttpServletResponse.class) {
                paramValues[i] = resp;
            } else if (parameterType == String.class) {
                // TODO 除了String类型，还有Integer等等
                // 首先判断method的形参列表中是否被@MyRequestParam修饰
                // 提取方法中加了注解的参数
                // 因为参数前可以添加多个注解，所以是二维数组，一个参数上不可以添加相同的注解，同一个注解可以加在不同的参数上!
                Annotation[][] annotations = method.getParameterAnnotations();
                for (int j = 0; j < annotations.length; j++) {
                    for (Annotation annotation: annotations[j]) {
                        // 如果当前形参的注解有@MyRequestParam，则获取形参的名称
                        if (annotation instanceof MyRequestParam) {
                            String paramName = ((MyRequestParam) annotation).value().trim();
                            if (!"".equals(paramName)) {
                                String value = Arrays.toString(parameterMap.get(paramName))
                                        .replaceAll("\\[|\\]", "")  // 将[]格式去掉
                                        .replaceAll("\\s", ",");    // 去掉空字符

                                paramValues[i] = value;
                            }
                        }
                    }
                }
            }
        }

        // 通过反射将参数引用给method
        // invoke(调用该方法的对象，将被引用的新参数的Object数组)
        String beanName = toLowFirstCase(method.getDeclaringClass().getSimpleName());
        method.invoke(ioc.get(beanName), paramValues);
    }

    /**
     * 功能描述： 调用init方法加载配置文件
     * @author ykq
     * @date 2020/4/29 11:10
     * @param
     * @return
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        // 1、加载配置文件，保存至contextConfig中
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        // 2、通过contextConfig的scanPackage，扫描相关的类，保存至中
        doScanner(contextConfig.getProperty("scanPackage"));
        
        // 3、初始化扫描得到的类，创建实例并保存至IoC容器
        doInstance();
        
        // 4、DI，实现注入
        doAutowired();

        // 5、初始化HandlerMapping
        initHandlerMapping();

        System.out.println("MySpring framework is init.");
    }

    /**
     * 功能描述: 遍历被@MyRequestMapping修饰的类和方法，保存其映射关系
     * @author ykq
     * @date 2020/4/30 0:16
     * @param
     * @return void
     */
    private void initHandlerMapping() {
        if (ioc.isEmpty()) {
            return;
        }

        // 遍历IoC容器，找到被@MyRequestMapping修饰的Controller类和其中的方法
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class clazz = entry.getValue().getClass();

            if (!clazz.isAnnotationPresent(MyController.class)) {
                continue;
            }

            // 保存Controller类上的映射关系
            String baseUrl = "";
            if (clazz.isAnnotationPresent(MyRequestMapping.class)) {
                MyRequestMapping myRequestMapping = (MyRequestMapping) clazz.getAnnotation(MyRequestMapping.class);
                baseUrl = myRequestMapping.value();
            }

            // 遍历当前Controller的所有方法，检查是否被@MyRequestMapping修饰
            for (Method method : clazz.getMethods()) {
                if (!method.isAnnotationPresent(MyRequestMapping.class)) {
                    continue;
                }

                MyRequestMapping myRequestMapping = method.getAnnotation(MyRequestMapping.class);
                String url = ("/" + baseUrl + "/" + myRequestMapping.value()).replaceAll("/+", "/");
                handlerMapping.put(url, method);
                System.out.println("Mapper: " + url + "," + method);
            }
        }
    }

    /***
     * 功能描述: 遍历ioc容器中的实例，为实例中被@MyAutowired修饰的属性赋值
     * @author ykq
     * @date 2020/4/29 23:45
     * @param
     * @return void
     */
    private void doAutowired() {
        // 先判读IoC容器为空，则返回
        if (ioc.isEmpty()) {
            return;
        }

        // 否则遍历IoC容器的实例，将其中被MyAutowired注解的属性赋值
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            // 获取实例中所有被public、protected、private修饰的属性。注意getDeclaredFields与getDeclaredField的不同
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            // 遍历属性集合，如果被@MyAutowired修饰，则为属性赋值
            for (Field field : fields) {
                if (!field.isAnnotationPresent(MyAutowired.class)) {
                    continue;
                }

                // 如果没有自定义beanName，则默认根据类型注入
                MyAutowired myAutowired = field.getAnnotation(MyAutowired.class);
                String beanName = myAutowired.value().trim();
                // TODO 类名首字母小写的判断
                if ("".equals(beanName)) {
                    beanName = field.getType().getName();
                }

                // 如果不是public，但被@MyAutowired注解的属性，则强制赋值
                field.setAccessible(true);

                try {
                    // 使用反射给属性赋值，set(要被修改的对象，修改后的新实例)
                    // TODO ioc的entry的value是什么？
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 功能描述: 根据扫描得到的类信息，创建实例，并保存
     * @author ykq
     * @date 2020/4/29 22:58
     * @param
     * @return void
     */
    private void doInstance() {
        // 判断扫描得到的classNames如果是空，则退出
        if (classNames.isEmpty()) {
            return;
        }

        // 遍历classNames，通过反射创建对象。但不是所有的类都要创建对象，只有加了注解的才会创建，MyController、MyService
        for (String className : classNames) {
            try {
                Class clazz = Class.forName(className);

                // 分类讨论Controller和Service
                // 判断指定类型的注释存在于此元素上
                if (clazz.isAnnotationPresent(MyController.class)) {
                    Object instance = clazz.newInstance();
                    // Spring的beanName默认是首字母小写的类名
                    String beanName = toLowFirstCase(clazz.getSimpleName());
                    ioc.put(beanName, instance);
                } else if (clazz.isAnnotationPresent(MyService.class)) {
                    // 如果是Service，需要考虑有多实现类的情况，此时多个实现类必须有不同的beanName，所以判断@MyService如果赋值value，则将value作为beanName，否则默认首字母小写的类名
                    MyService myService = (MyService) clazz.getAnnotation(MyService.class);
                    String beanName = myService.value();
                    if ("".equals(beanName.trim())) {
                        // 默认beanName是首字母小写类名
                        beanName = toLowFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);

                    // TODO 多实现类处理
                    // 为接口赋值实现类的实例
                    for (Class i : clazz.getInterfaces()) {
                        if (ioc.containsKey(i.getName())) {
                            throw new Exception("The " + i.getName() + " is exited!");
                        }
                        ioc.put(i.getName(), instance);
                    }
                } else {
                    // 其他注解不处理
                    continue;
                }

            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 功能描述: 将传入字符串首字母小写
     * @author ykq
     * @date 2020/4/29 23:12
     * @param
     * @return java.lang.String
     */
    private String toLowFirstCase(String simpleName) {
        char[] a = simpleName.toCharArray();
        a[0] += 32;
        return String.valueOf(a);
    }

    /**
     * 功能描述： 获取配置文件中需要扫描的包路径，保存所有的类名
     * @author ykq
     * @date 2020/4/29 13:54
     * @param
     * @return
     */
    private void doScanner(String scanPackage) {
        // 转换文件路径，将.替换为/。 注意getResource和getResources不一样
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classPath = new File(url.getFile());

        // 遍历当前目录下为文件，如果是class文件，则保存类名，如果是文件夹，则递归
        for (File file : classPath.listFiles()) {
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            }
            if (!file.getName().endsWith(".class")) {
                continue;
            }
            // 为防止不同包下，有相同名称的类，则将根目录下的全路径作为name
            String className = scanPackage + "." + file.getName().replace(".class", "");
            classNames.add(className);
        }
    }

    /**
     * 功能描述： 通过web.xml加载配置文件，保存至Properties中
     * @author ykq
     * @date 2020/4/29 13:30
     * @param
     * @return
     * 找到名叫contextConfigLocation的init-param，使用param-value对应的配置文件
     */
    private void doLoadConfig(String contextConfigLocation) {
        System.out.println("[加载配置文件]，开始");
        // 通过类路径找到spring配置文件的路径
        // 并且将其读取出来放到Properties对象中
        // 相当于将scanPackage=com.my.demo暂时保存起来
        /* class是指当前类的class对象，getClassLoader()是获取当前的类加载器，什么是类加载器？
            简单点说，就是用来加载java类的,类加载器负责把class文件加载进内存中，
            并创建一个java.lang.Class类的一个实例，也就是class对象，并且每个类的类加载器都不相同。
            getResourceAsStream(path)是用来获取资源的，而类加载器默认是从classPath下获取资源的，因为这下面有class文件，
            所以这段代码总的意思是通过类加载器在classPath目录下获取资源.并且是以流的形式。
            原文链接：https://blog.csdn.net/feeltouch/article/details/83796764 */
        InputStream is = this.getClass().getClassLoader().getResourceAsStream("application.properties");
        try {
            contextConfig.load(is);
        } catch (IOException e) {
            System.out.println("[加载配置文件]，异常");
            e.printStackTrace();
        } finally {
            if (null != is) {
                try {
                    System.out.println("[加载配置文件]关闭流");
                    is.close();
                } catch (IOException e) {
                    System.out.println("[加载配置文件]关闭流，异常");
                    e.printStackTrace();
                }
            }
        }
        System.out.println("[加载配置文件]，结束");
    }
}
