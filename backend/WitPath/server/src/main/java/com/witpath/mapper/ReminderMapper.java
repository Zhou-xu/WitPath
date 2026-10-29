package com.witpath.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.witpath.entity.Reminder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReminderMapper extends BaseMapper<Reminder> {
}
