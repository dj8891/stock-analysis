package com.springboot.stock_analysis.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RouteConfig implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // This configuration forwards all unknown paths to index.html
        registry.addViewController("/{spring:[a-zA-Z0-9\\-]+}")
                .setViewName("forward:/index.html");
        registry.addViewController("/**/{spring:[a-zA-Z0-9\\-]+}")
                .setViewName("forward:/index.html");
    }
}
