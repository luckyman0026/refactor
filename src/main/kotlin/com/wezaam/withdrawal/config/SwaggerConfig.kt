package com.wezaam.withdrawal.config

import io.swagger.annotations.Api
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import springfox.documentation.builders.ApiInfoBuilder
import springfox.documentation.builders.PathSelectors
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.service.ApiInfo
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spring.web.plugins.Docket
import springfox.documentation.swagger2.annotations.EnableSwagger2

@Configuration
@EnableSwagger2
class SwaggerConfig {

    @Bean
    fun api(apiInfo: ApiInfo): Docket {
        return Docket(DocumentationType.SWAGGER_2).apiInfo(apiInfo)
            .select()
            .apis(RequestHandlerSelectors.withClassAnnotation(Api::class.java))
            .paths(PathSelectors.any())
            .build()
    }

    @Bean
    fun apiInfo(): ApiInfo {
        return ApiInfoBuilder().title("Withdrawal service")
            .build()
    }
}

