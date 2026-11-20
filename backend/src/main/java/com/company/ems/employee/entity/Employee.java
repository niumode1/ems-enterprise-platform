package com.company.ems.employee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("employee")
public class Employee {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String employeeNo;
    private String name;
    private String gender;
    private LocalDate birthDate;
    private String phone;
    private String email;
    private String idCardEnc;
    private String address;
    private String emergencyContact;
    private Long avatarFileId;
    private LocalDate hireDate;
    private String status;
    private Long primaryOrgId;
    private Long primaryPositionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
