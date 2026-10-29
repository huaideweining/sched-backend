package com.appliance.itwb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 会话服务启动类（仅供参考，可直接拷进你自己的工程）。
 *
 * <p><b>文件作用</b>：把本模块以普通 Spring Boot 组件的形式挂载到主工程。</p>
 *
 * <p><b>依赖</b>：spring-boot、mybatis-spring（@MapperScan 来自它，不在 mybatis-plus 里）。</p>
 *
 * <p><b>Oracle 注意点</b>：数据源方言由 MyBatis-Plus 依据 JDBC URL 自动识别为 ORACLE，
 * 分页拦截器也据此生成 ROWNUM 嵌套 SQL，无需额外配置。</p>
 *
 * <p><b>注意事项</b>：</p>
 * <ul>
 *   <li>{@code @MapperScan} 必须有：Mapper 是接口，没有实现类，Spring 不会自动注册，
 *       缺了它启动必报 "required a bean of type 'SysUserSessionMapper' that could not be found"；
 *       若拷进主工程，把扫描路径改成主工程的 mapper 包，或直接在主启动类上加；</li>
 *   <li>{@code @EnableScheduling} 只有在一个被扫描到的配置类上声明才会生效；
 *       若主工程已经加了，这里可以删掉。</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan({"com.appliance.itwb", "com.workbuddy.sched"})
@MapperScan({"com.appliance.itwb.session.mapper", "com.appliance.itwb.wb.mapper", "com.workbuddy.sched.mapper"})
public class ItwbSessionApplication {

    /**
     * 启动入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ItwbSessionApplication.class, args);
    }
}
