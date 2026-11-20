package com.company.ems.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.ems.chat.entity.ChatMember;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMemberMapper extends BaseMapper<ChatMember> {
}
