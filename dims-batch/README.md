######数据库脚本#######
######时间:20200924#####
1. 采用flyway管理数据库脚本
2. 数据库脚本默认位于resources/db/migration路径下，根据不同省份不同要求又分为三个文件夹:
    >common:常规省份的模型及核查脚本
    special：特殊省份的模型及核查脚本，本次升级主要为引入IDC专业的省份
    other：废弃的历史脚本，今后会删除。
    脚本内容更新:
        2.1 系统核查相关的业务模型L:*dims_ddl.sql, 这部分由开发人员自行设计，无特殊情况不用更新;
        2.2 元数据模型及核查存过:*business_ddl.sql, 需要根据不同省份对common/special下的脚本分别更新;
        2.3 指标核查建模：*dims_dml_meta.sql，需要结合2.1和2.2创建业务模型的原始数据，同样需要据不同省份对common/special下的脚本分别更新；
        2.4 资源统计的业务模型/存过/统计指标:*statistics_*.sql,用于资源统计，同样需要据不同省份对common/special下的脚本分别更新。
    
3. 脚本发布要慎重：
    >3.1 `dims-batch`里面的脚本是每次都会重新执行的，但`dims-web`只在第一次性的，不能重复执行，其他的是每次DB发布新脚本都要重复在主库执行的;
     3.2 请在本地测试无语法错误后再提交更新。

    