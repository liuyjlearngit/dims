package com.cmdi.dims.batch;

import java.io.File;
import java.util.Date;

import com.cmdi.dims.domain.LockService;
import com.cmdi.dims.domain.TaskService;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.util.Assert;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DimsTaskListener implements JobExecutionListener {

    private TaskService taskService;

    private LockService lockService;

    public DimsTaskListener(TaskService taskService, LockService lockService) {
        this.taskService = taskService;
        this.lockService = lockService;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        SkipEnum skip = SkipEnum.NON_SKIP;
        try {
            String province = BatchUtil.getProvince(jobExecution);
            Assert.hasText(province, "PLEASE INPUT PROVINCE !");
            String speciality = BatchUtil.getSpeciality(jobExecution);
            Assert.hasText(speciality, "PLEASE INPUT SPECIALITY !");
            Date lockedAt = BatchUtil.getLockedAt(jobExecution);
            Assert.notNull(lockedAt, "PLEASE INPUT DATE !");
            lockService.tryLockTask(province, speciality, lockedAt);
            String taskCode = taskService.createTask(province, speciality);
            BatchUtil.setTaskCode(jobExecution, taskCode);
        } catch (Exception e) {
            log.error("[DimsTaskListener] START ERROR !" + e.getMessage(), e);
            skip = SkipEnum.ERROR_SKIP;
        } finally {
            BatchUtil.setSkip(jobExecution, skip);
        }

    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String province = BatchUtil.getProvince(jobExecution);
        String speciality = BatchUtil.getSpeciality(jobExecution);
        Date lockedAt = BatchUtil.getLockedAt(jobExecution);
        if (StringUtils.isNotEmpty(province) && StringUtils.isNotEmpty(speciality) && null != lockedAt) {
            try {
                lockService.unlockTask(province, speciality, lockedAt);
                String taskCode = BatchUtil.getTaskCode(jobExecution);
                if (StringUtils.isNotEmpty(taskCode)) {
                    taskService.finish(taskCode);
                }
                File taskDirectory = BatchUtil.getTaskFolder(taskCode);
                if (null != taskDirectory) {
                    FileUtils.forceDelete(taskDirectory);
                }
            } catch (Exception e) {
                log.error("[DimsTaskListener] FINISH ERROR !" + e.getMessage(), e);
            }

        }
        if (BatchUtil.isSkip(jobExecution)) {
            jobExecution.setExitStatus(ExitStatus.FAILED);
        }
    }
}
