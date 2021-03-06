package com.wt.mggui.util

import com.wt.mggui.model.Attributes
import com.wt.mggui.model.Config
import com.wt.mggui.model.Property
import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement

/**
 * 构造器
 * Created by wt on 2017/5/20.
 */
object Generator {

    fun generate(configs: Config): String {
        val property = getProperties(configs)
        return generate(property)
    }

    private fun generate(property: Property): String {
        try {
            val cfg = Configuration(Configuration.VERSION_2_3_23)
            cfg.defaultEncoding = "UTF-8"
//            cfg.setDirectoryForTemplateLoading(File(this.javaClass.classLoader.getResource("ftl").path))
            cfg.setClassLoaderForTemplateLoading(this.javaClass.classLoader,"ftl")
            cfg.templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
            val paths = getFilePath(property)
            for ((key, value) in paths) {
                val temp = cfg.getTemplate("$key.ftl")
                val dir = File("generator/$key")
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val osp: FileOutputStream
                osp = FileOutputStream(File(dir, value))
                val out = OutputStreamWriter(osp)
                temp.process(property, out)
                osp.flush()
                osp.close()
            }
            return "${property.entityName} generate success！"
        } catch (e: Exception) {
            return e.message.orEmpty()
        }
    }

    /**
     * 获取文件路径
     */
    private fun getFilePath(property: Property): Map<String, String> {
        val paths = mutableMapOf<String,String>()
        paths.put("entity","${property.entityName}.java")
        paths.put("javaMapper","${property.entityName}Mapper.java")
        paths.put("xmlMapper","${property.entityName}Mapper.xml")
        if(property.servicePackage.isNotBlank()){
            paths.put("service","${property.entityName}Service.java")
            paths.put("serviceImpl","${property.entityName}ServiceImpl.java")
        }
        return paths
    }

    /**
     * 获取数据库表字段
     */
    private fun getProperties(configs: Config): Property {
        val property = Property()
        property.entityName = configs.entityNameProperty.value
        property.entityNameLowCase = (property.entityName[0] + 32) + property.entityName.substring(1)
        property.entityPackage = configs.entityPackageProperty.value
        property.mapperPackage = configs.mapperPackageProperty.value
        property.servicePackage = configs.servicePackageProperty.value
        property.tableName = configs.tableNameProperty.value
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance()
            val con: Connection = DriverManager.getConnection("jdbc:mysql://${configs.ipProperty.value}/${configs.dataBaseProperty.value}?useUnicode=true&characterEncoding=utf8&useSSL=false", configs.rootUserProperty.value, configs.passwordProperty.value)
            val stmt: Statement = con.createStatement()
            val sql = "SELECT COLUMN_NAME, COLUMN_COMMENT, DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_NAME = '${configs.tableNameProperty.value}' AND TABLE_SCHEMA = '${configs.dataBaseProperty.value}'"
            println(sql)
            val rs: ResultSet = stmt.executeQuery(sql)
            while (rs.next()) {
                val columnName = rs.getString("COLUMN_NAME")
                val dataType = rs.getString("DATA_TYPE").toUpperCase()
                val comments = rs.getString("COLUMN_COMMENT")
                val fieldNameUpCase = getFieldNameUpCase(columnName)
                val fieldName = (fieldNameUpCase[0] + 32) + fieldNameUpCase.substring(1)
                val type = if (dataType == "DECIMAL") {
                    property.imports.add("import java.math.BigDecimal;")
                    "BigDecimal"
                } else if (dataType == "INT" || dataType == "TINYINT") {
                    "Integer"
                } else if (dataType == "DATE" || dataType == "DATETIME" || dataType == "TIMESTAMP") {
                    property.imports.add("import java.util.Date;")
                    "Date"
                } else if (dataType == "BIGINT") {
                    "Long"
                } else if (dataType == "BIT") {
                    "Boolean"
                } else {
                    "String"
                }
                property.attrs.add(Attributes(columnName, fieldName, dataType, type, comments, fieldNameUpCase))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return property
    }

    private fun getFieldNameUpCase(columnName: String): String {
        //数据库字段驼峰式命名
        return columnName.toLowerCase().split("_").dropLastWhile(String::isEmpty).map { (it[0] - 32) + it.substring(1) }.joinToString("")
    }


}