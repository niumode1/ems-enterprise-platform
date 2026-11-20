package com.company.ems.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.ems.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
