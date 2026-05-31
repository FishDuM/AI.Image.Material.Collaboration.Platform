package hk.ljx.fishpicsbackend.common.scheduled;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import hk.ljx.fishpicsbackend.post.entity.Post;
import hk.ljx.fishpicsbackend.mapper.PostMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class HotScoreScheduler {

    @Resource
    private PostMapper postMapper;

    @Scheduled(fixedRate = 600_000)
    @Transactional
    public void recalculatePostHotScores() {
        int count = postMapper.update(null, new UpdateWrapper<Post>()
                .setSql("hot = likes_num * 3 + collects_num * 3 + comment_num * 2 + views_num * 2"));
        log.info("帖子热度值已更新，影响 {} 条记录", count);
    }
}
