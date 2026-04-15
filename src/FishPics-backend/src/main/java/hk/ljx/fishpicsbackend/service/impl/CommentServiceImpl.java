package hk.ljx.fishpicsbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.entity.Comment;
import hk.ljx.fishpicsbackend.service.CommentService;
import hk.ljx.fishpicsbackend.mapper.CommentMapper;
import org.springframework.stereotype.Service;

/**
* @author 30574
* @description 针对表【comment(评论表)】的数据库操作Service实现
* @createDate 2026-04-13 21:24:56
*/
@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment>
    implements CommentService {

}




