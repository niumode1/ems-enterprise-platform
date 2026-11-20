package com.company.ems.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.ems.system.entity.SysApprovalPolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysApprovalPolicyMapper extends BaseMapper<SysApprovalPolicy> {

    @Select("SELECT * FROM sys_approval_policy WHERE policy_code = #{policyCode} LIMIT 1")
    SysApprovalPolicy selectByCode(String policyCode);
}
