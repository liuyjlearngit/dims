package com.cmdi.dims.app.dto;

import java.util.Date;

import org.springframework.format.annotation.DateTimeFormat;

import lombok.Data;

@Data
public class TaskItemQueryDto {

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date collectionDate;
}
