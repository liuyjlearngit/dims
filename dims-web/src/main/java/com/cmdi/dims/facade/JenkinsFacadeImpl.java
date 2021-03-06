package com.cmdi.dims.facade;

import com.cmdi.dims.task.entity.AreaCodeConfig;
import com.cmdi.dims.task.repository.AreaCodeConfigRepository;
import com.cmdi.dims.util.PinyinUtil;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Job;
import com.offbytwo.jenkins.model.JobWithDetails;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
@PropertySource({"classpath:config/province_filter.properties"})
public class JenkinsFacadeImpl implements JenkinsFacade {

    private static final String JOB_NAME = "batch-job";

    @Autowired
    private JenkinsServer jenkinsServer;

    @Value("${dims.url}")
    private String domain;

    @Value("${spring.datasource.primary.username}")
    private String batchUsername;

    @Value("${spring.datasource.primary.password}")
    private String batchPassword;

    @Value("${spring.datasource.primary.url}")
    private String batchUrl;

    @Value("${template.datasource.username}")
    private String templateUsername;

    @Value("${template.datasource.password}")
    private String templatePassword;

    @Value("classpath:jenkins/batch-job-template.xml")
    private Resource jenkinsTemplate;

    @Value("classpath:jenkins/batch-job-template-idc.xml")
    private Resource jenkinsTemplateIDC;

    @Value("${filter.list}")
    private String filterList;

    @Autowired
    private AreaCodeConfigRepository areaCodeConfigRepository;

    /**
     * 开始执行任务
     * @param province
     * @param speciality
     * @throws IOException
     */
    @Override
    public void start(String province, String speciality) throws IOException {
        Map<String, Job> jobs = jenkinsServer.getJobs();
        AreaCodeConfig areaCodeConfig = areaCodeConfigRepository.findByCode(province);
        String jobName = JOB_NAME + "-" + province + "-" + PinyinUtil.convert(areaCodeConfig.getName()) + "-" + PinyinUtil.convert(speciality);
        String tempateDatabase = "dims-" + province + "-" + PinyinUtil.convert(speciality).toLowerCase();
        String description = "省份：" + areaCodeConfig.getName() + ",专业：" + speciality + ",版本：V20191102";
        String jobConfig=IOUtils.toString(jenkinsTemplate.getInputStream(), "UTF-8");
        jobConfig = StringUtils.replace(jobConfig, "#description#", description);
        jobConfig = StringUtils.replace(jobConfig, "#province#", province);
        jobConfig = StringUtils.replace(jobConfig, "#speciality#", speciality);
        jobConfig = StringUtils.replace(jobConfig, "#domain#", domain);
        jobConfig = StringUtils.replace(jobConfig, "#template_username#", templateUsername);
        jobConfig = StringUtils.replace(jobConfig, "#template_password#", templatePassword);
        jobConfig = StringUtils.replace(jobConfig, "#template_database#", tempateDatabase);
        jobConfig = StringUtils.replace(jobConfig, "#data_username#", batchUsername);
        jobConfig = StringUtils.replace(jobConfig, "#data_password#", batchPassword);
        jobConfig = StringUtils.replace(jobConfig, "#data_url#", batchUrl);
        jobConfig = StringUtils.replace(jobConfig,"#label#",province);
        if(StringUtils.isNotEmpty(filterList)
                &&StringUtils.containsIgnoreCase(filterList,areaCodeConfig.getCode())){
            jobConfig = StringUtils.replace(jobConfig, "#flyway_path#", "special");
        }else{
            jobConfig = StringUtils.replace(jobConfig, "#flyway_path#", "common");
        }
        if (!jobs.containsKey(jobName)) {
            jenkinsServer.createJob(jobName, jobConfig, true);
        }
        JobWithDetails job = jenkinsServer.getJob(jobName);
        if (!Objects.equals(job.getDescription(), description)) {
            jenkinsServer.updateJob(jobName, jobConfig, true);
        }

        Map<String, String> parameter = new HashMap<>();
        parameter.put("province", province);
        parameter.put("speciality", speciality);
        parameter.put("domain", domain);
        parameter.put("template_username", templateUsername);
        parameter.put("template_password", templatePassword);
        parameter.put("template_database", tempateDatabase);
        parameter.put("data_username", batchUsername);
        parameter.put("data_password", batchPassword);
        parameter.put("data_url", batchUrl);
        parameter.put("label", province);
        job.build(parameter, true);
    }
}
