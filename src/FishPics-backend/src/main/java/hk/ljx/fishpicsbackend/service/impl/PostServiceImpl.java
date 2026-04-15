package hk.ljx.fishpicsbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.entity.Post;
import hk.ljx.fishpicsbackend.service.PostService;
import hk.ljx.fishpicsbackend.mapper.PostMapper;
import org.springframework.stereotype.Service;

/**
* @author 30574
* @description 针对表【post(帖子表)】的数据库操作Service实现
* @createDate 2026-04-13 21:24:41
*/
@Service
public class PostServiceImpl extends ServiceImpl<PostMapper, Post>
    implements PostService{

}




