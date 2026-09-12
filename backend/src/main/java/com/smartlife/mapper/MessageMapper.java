package com.smartlife.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlife.entity.ChatMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface MessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 会话列表：按 (最小id,最大id) 对分组取最后一条消息，按时间倒序
     */
    @Select("SELECT m.* FROM tb_message m JOIN (" +
            " SELECT LEAST(from_id, to_id) AS a, GREATEST(from_id, to_id) AS b, MAX(id) AS mid" +
            " FROM tb_message WHERE from_id = #{me} OR to_id = #{me} GROUP BY a, b) t" +
            " ON m.id = t.mid ORDER BY m.id DESC LIMIT #{limit}")
    List<ChatMessage> selectConversations(@Param("me") Long me, @Param("limit") int limit);

    /** 各发送方发给我(me)的未读数 */
    @Select("SELECT from_id AS peerId, COUNT(*) AS cnt FROM tb_message " +
            "WHERE to_id = #{me} AND read_flag = 0 GROUP BY from_id")
    List<Map<String, Object>> selectUnreadMap(@Param("me") Long me);

    /** 将与某人的来信标记为已读 */
    @Update("UPDATE tb_message SET read_flag = 1 WHERE from_id = #{peer} AND to_id = #{me} AND read_flag = 0")
    int markRead(@Param("me") Long me, @Param("peer") Long peer);
}
