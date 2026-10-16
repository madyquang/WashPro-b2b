package com.washpro.washpro_b2b.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bật các tác vụ định kỳ (bản tin thông báo hằng ngày...). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
