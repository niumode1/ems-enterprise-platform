package com.company.ems.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.ems.system.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig> {

    @Select("SELECT * FROM sys_config WHERE config_key = #{key} LIMIT 1")
    SysConfig selectByKey(String key);
}
