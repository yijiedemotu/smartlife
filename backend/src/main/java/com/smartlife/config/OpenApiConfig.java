package com.smartlife.config;

import io.swagger.annotations.Api;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2WebMvc;

@Configuration
@EnableSwagger2WebMvc
public class OpenApiConfig {

    @Bean
    public Docket docket() {
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo())
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.smartlife.controller"))
                .paths(PathSelectors.any())
                .build();
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("智联生活 · 三端本地生活与社交聚合平台")
                .description("用户端：店铺浏览/LBS附近/外卖下单/秒杀券/饭搭子匹配/签到 | "
                        + "商家端(/merchant)：经营看板/订单履约/商品/券/入驻审核状态 | "
                        + "管理端(/admin)：入驻审核/店铺治理/账号治理/平台看板")
                .version("2.0.0")
                .contact(new Contact("smartlife", "", ""))
                .build();
    }
}
