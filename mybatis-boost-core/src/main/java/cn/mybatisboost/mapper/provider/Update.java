package cn.mybatisboost.mapper.provider;

import cn.mybatisboost.core.Configuration;
import cn.mybatisboost.core.ConfigurationAware;
import cn.mybatisboost.core.SqlProvider;
import cn.mybatisboost.util.*;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.reflection.MetaObject;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Update implements SqlProvider, ConfigurationAware {

    private Configuration configuration;

    @Override
    public void replace(Connection connection, MetaObject metaObject, MappedStatement mappedStatement, BoundSql boundSql) {
        Class<?> entityType = MapperUtils.getEntityTypeFromMapper
                (mappedStatement.getId().substring(0, mappedStatement.getId().lastIndexOf('.')));
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("UPDATE ").append(EntityUtils.getTableName(entityType, configuration.getNameAdaptor()));

        boolean partial = mappedStatement.getId().contains("Partial");
        boolean selective = mappedStatement.getId().contains("Selective");

        Map<?, ?> parameterMap = (Map<?, ?>) boundSql.getParameterObject();
        Object entity = parameterMap.get("param1");
        List<String> properties;
        String[] conditionalProperties;
        if (!partial) {
            properties = EntityUtils.getProperties(entity, selective);
            conditionalProperties = (String[]) parameterMap.get("param2");
        } else {
            String[] candidateProperties = (String[]) parameterMap.get("param2");
            properties = PropertyUtils.buildPropertiesWithCandidates(candidateProperties, entity, selective);
            conditionalProperties = (String[]) parameterMap.get("param3");
        }
        if (conditionalProperties.length == 0) {
            conditionalProperties = new String[]{EntityUtils.getIdProperty(entityType)};
        }
        PropertyUtils.rebuildPropertiesWithConditions(properties, entityType, conditionalProperties);

        if (!properties.isEmpty()) {
            boolean mapUnderscoreToCamelCase = (boolean)
                    metaObject.getValue("delegate.configuration.mapUnderscoreToCamelCase");
            List<String> columns = properties.stream()
                    .map(it -> SqlUtils.normalizeColumn(it, mapUnderscoreToCamelCase)).collect(Collectors.toList());
            sqlBuilder.append(" SET ");
            columns.stream().limit(columns.size() - conditionalProperties.length)
                    .forEach(c -> sqlBuilder.append(c).append(" = ?, "));
            sqlBuilder.setLength(sqlBuilder.length() - 2);
            SqlUtils.appendWhere(sqlBuilder, columns.stream().skip(columns.size() - conditionalProperties.length));
        }

        List<ParameterMapping> parameterMappings = MyBatisUtils.getParameterMappings
                ((org.apache.ibatis.session.Configuration)
                        metaObject.getValue("delegate.configuration"), properties);
        MyBatisUtils.getMetaObject(metaObject.getValue("delegate.parameterHandler"))
                .setValue("parameterObject", entity);
        metaObject.setValue("delegate.boundSql.parameterObject", entity);
        metaObject.setValue("delegate.boundSql.parameterMappings", parameterMappings);
        metaObject.setValue("delegate.boundSql.sql", sqlBuilder.toString());
    }

    @Override
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }
}
