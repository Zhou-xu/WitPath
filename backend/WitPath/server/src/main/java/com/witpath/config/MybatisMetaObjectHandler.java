package com.witpath.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 公共字段自动填充
 * <p>
 * 负责注入 created_at / updated_at，保证即使调用方未显式赋值也不会出现空值。
 * </p>
 */
@Slf4j
@Component
public class MybatisMetaObjectHandler implements MetaObjectHandler {

    private static final String CREATED_AT = "createdAt";
    private static final String UPDATED_AT = "updatedAt";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        fillIfAbsent(metaObject, CREATED_AT, now);
        fillIfAbsent(metaObject, UPDATED_AT, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 更新场景始终刷新 updated_at
        if (metaObject.hasSetter(UPDATED_AT)) {
            setFieldValByName(UPDATED_AT, LocalDateTime.now(), metaObject);
        }
    }

    /**
     * 按字段名填充：仅当实体存在该属性且当前值为空时才写入。
     * <p>
     * 说明：这里没有使用 strictInsertFill，因为它要求字段显式声明
     * {@code @TableField(fill = FieldFill.INSERT)}，本项目实体未声明该注解，
     * 直接按属性名填充对全部实体统一生效。
     * </p>
     */
    private void fillIfAbsent(MetaObject metaObject, String field, LocalDateTime value) {
        if (!metaObject.hasSetter(field)) {
            return;
        }
        if (getFieldValByName(field, metaObject) == null) {
            setFieldValByName(field, value, metaObject);
        }
    }
}
