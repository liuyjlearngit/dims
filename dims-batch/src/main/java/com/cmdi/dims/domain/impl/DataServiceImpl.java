package com.cmdi.dims.domain.impl;

import com.cmdi.dims.domain.DataService;
import com.cmdi.dims.domain.meta.MetadataLoader;
import com.cmdi.dims.domain.meta.dto.EntityType;
import com.cmdi.dims.domain.meta.dto.Index;
import com.cmdi.dims.domain.meta.dto.Metadata;
import com.cmdi.dims.domain.util.DataUtil;
import com.cmdi.dims.sdk.model.TaskItemIndexDto;
import com.csvreader.CsvReader;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DataServiceImpl implements DataService {

    private static final String Config_Path  = "config/includedTables.csv";

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public Metadata loadMetadata(String tableName) {
        return new MetadataLoader(namedParameterJdbcTemplate).loadMetadata(tableName);
    }

    @Override
    public List<Index> loadIndices(String speciality) {
        return new MetadataLoader(namedParameterJdbcTemplate).loadIndex(speciality);
    }

    @Override
    public Map<String, String> loadTables(List<String> specialities) {
        return new MetadataLoader(namedParameterJdbcTemplate)
                .loadEntityType()
                .stream()
                .filter(e -> specialities.contains(e.getSpecialityName()))
                .collect(Collectors.toMap(EntityType::getCode, EntityType::getName));
    }
    @Override
    public Map<String, String> loadTables(List<String> specialities,String primarySpecialty) {
        Map<String, String> specialityTables = loadTables(Lists.newArrayList(primarySpecialty));
        Map<String, String> includeList = getIncludedTables(primarySpecialty);
        for(String speciality:specialities){
            if(speciality == primarySpecialty){
                continue;
            }
            if(StringUtils.isNotEmpty(includeList.get(speciality))){
                String[] tableList = includeList.get(speciality).split(",");
                for(String str:tableList){
                    EntityType entityType = new MetadataLoader(namedParameterJdbcTemplate).findEntityTypeByCode(str);
                    specialityTables.put(entityType.getCode(),entityType.getName());
                }
            }
        }
        return specialityTables;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public int importData(Metadata metadata, List<Map<String, Object>> parameters) {
        int length = parameters.size();
        SqlParameterSource[] sqlParameterSources = new SqlParameterSource[length];
        for (int i = 0; i < length; i++) {
            sqlParameterSources[i] = new MapSqlParameterSource(parameters.get(i));
        }
        int[] result = namedParameterJdbcTemplate.batchUpdate(DataUtil.insertDataStatement(metadata), sqlParameterSources);
        int total = 0;
        for (int r : result) {
            total += r;
        }
        return total;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void cleanData(String table) {
        namedParameterJdbcTemplate.update(DataUtil.cleanDataStatement(table), new HashMap<>());
    }
    //TODO 核查空间数据时，已经超过7200ms了，为什么没有报错
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 7200)
    @Override
    public void calculateIndex(String taskCode, String province, Index index) {
        if (StringUtils.isEmpty(index.getProcName())) {
            return;
        }
        Connection connection = null;
        boolean oldAutoCommit = false;
        try {
            connection = jdbcTemplate.getDataSource().getConnection();
            oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            CallableStatement callableStatement = connection.prepareCall("{call " + index.getProcName() + "(?,?,?)}");
            callableStatement.setString(1, province);
            callableStatement.setString(2, taskCode);
            callableStatement.setInt(3, index.getId().intValue());
            callableStatement.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            if (null != connection) {
                try {
                    connection.rollback();
                } catch (Exception e1) {
                }
            }
            throw new RuntimeException(e);
        } finally {
            if (null != connection) {
                try {
                    connection.setAutoCommit(oldAutoCommit);
                    connection.close();
                } catch (Exception e1) {
                }
            }
        }
    }

    @Override
    public List<TaskItemIndexDto> loadCalculateIndexData(String taskCode) {
        return jdbcTemplate.query("SELECT * FROM (SELECT A.* FROM dims_tm_taskitem_index A INNER JOIN dims_tm_areacodeconfig b ON A.provincecode = b.code AND b.regiontype = 1 AND A.regiontype IN ( 1, 2, 3 ) AND A.prefecturecode IS NULL AND A.countycode IS NULL UNION ALL SELECT A.* FROM dims_tm_taskitem_index A INNER JOIN dims_tm_areacodeconfig b ON A.provincecode = b.provincecode AND A.prefecturecode = b.code AND b.regiontype = 2 AND A.regiontype IN ( 2, 3 ) AND A.countycode IS NULL UNION ALL SELECT A.* FROM dims_tm_taskitem_index A INNER JOIN dims_tm_areacodeconfig b ON A.provincecode = b.provincecode AND A.prefecturecode = b.prefecturecode AND A.countycode = b.code AND b.regiontype = 3 AND A.regiontype = 3) T WHERE taskCode=? ORDER BY regiontype, code", new Object[]{taskCode}, new BeanPropertyRowMapper<>(TaskItemIndexDto.class));
    }

    @Override
    public long countErrorData(Metadata metadata) {
        return jdbcTemplate.queryForObject(DataUtil.countErrorDataStatement(metadata), Long.class);
    }

    @Override
    public List<Map<String, Object>> exportData(Metadata metadata, int limit, int offset) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("LIMIT", limit);
        parameters.put("OFFSET", offset);
        return namedParameterJdbcTemplate.query(DataUtil.selectErrorDataStatement(metadata), parameters, new ColumnMapRowMapper());
    }

    @Override
    public List<String> getDimsColResultList(Metadata metadata) {
        Map<String, Object> parameters = new HashMap<>();
        String sql = "select dims_col_result from "+ metadata.getEntityType().getExtensionTable() +" where dims_col_result is not null group by dims_col_result ";
        return namedParameterJdbcTemplate.queryForList(sql,parameters,String.class);
    }

    @Override
    public List<Map<String, Object>> exportDataWithResult(Metadata metadata, int limit, String rtName) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("LIMIT", limit);
        parameters.put("DIMS_COL_RESULT", rtName);
        return namedParameterJdbcTemplate.query(DataUtil.selectErrorDataStatementWithResult(metadata), parameters, new ColumnMapRowMapper());

    }
    private Map<String,String> getIncludedTables(String primarySpecialty){
        Map<String,String> tableMap = new HashMap<String,String>();
        try {
            InputStream fis = new ClassPathResource(Config_Path).getInputStream();
            CsvReader csvReader = new CsvReader(fis, ';', Charset.forName("UTF-8"));
            // 读表头
            //speciality;includedspeciality;tablename
            csvReader.readHeaders();
            // 读内容
            while (csvReader.readRecord()) {
                // 读一整行
                log.info(csvReader.getRawRecord());
                if(StringUtils.equalsIgnoreCase(csvReader.get("speciality"),primarySpecialty) ){
                    tableMap.put(csvReader.get("includedspeciality"),csvReader.get("tablename"));
                }
            }
        } catch (IOException e) {
            log.info(e.getMessage());
        }
        return tableMap;
    }
}
