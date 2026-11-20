package com.company.ems.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.ems.project.entity.Task;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {
}
