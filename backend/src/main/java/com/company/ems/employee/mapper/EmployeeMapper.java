package com.company.ems.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.ems.employee.entity.Employee;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {
}
