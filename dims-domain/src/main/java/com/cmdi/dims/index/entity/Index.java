package com.cmdi.dims.index.entity;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.Data;

@Data
@Entity
@Table(name = "dims_idx_index")
@EntityListeners(AuditingEntityListener.class)
public class Index {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long indexId;

    @Column(name = "name")
    private String name;

    @Column(name = "code")
    private String code;

    @Column(name = "orderby")
    private Integer ordinal;

    @Column(name = "entitytype_id")
    private Long entityTypeId;

    @Column(name = "specialityname")
    private String specialityName;

    @Column(name = "type")
    private Integer type;

    @Type(type = "org.hibernate.type.NumericBooleanType")
    @Column(name = "isenable")
    private Boolean enable;

    @Column(name = "procname")
    private String procName;

    @Column(name = "priority")
    private Integer priority;

    @Version
    @Column(name = "version")
    private Long version;

    @CreatedDate
    @Column(name = "createdate")
    private Date createdAt;

    @CreatedBy
    @Column(name = "creator")
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updatedate")
    private Date updatedAt;

    @LastModifiedBy
    @Column(name = "updater")
    private String updatedBy;

    @Column(name = "memo")
    private String memo;

    @Column(name = "ruledesc")
    private String ruleDesc;
}
