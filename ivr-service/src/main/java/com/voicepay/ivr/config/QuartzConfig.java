package com.voicepay.ivr.config;

import com.voicepay.ivr.scheduler.OutboundCallSchedulerJob;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    @Value("${app.scheduler.cron:0/20 * * * * ?}")
    private String cronExpression;

    @Bean
    public JobDetail outboundCallJobDetail() {
        return JobBuilder.newJob(OutboundCallSchedulerJob.class)
                .withIdentity("outboundCallSchedulerJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger outboundCallJobTrigger(JobDetail outboundCallJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(outboundCallJobDetail)
                .withIdentity("outboundCallSchedulerTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                .build();
    }
}
