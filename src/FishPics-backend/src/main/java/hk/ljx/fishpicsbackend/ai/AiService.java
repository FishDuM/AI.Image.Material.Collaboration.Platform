package hk.ljx.fishpicsbackend.ai;

import hk.ljx.fishpicsbackend.ai.vo.AiPictureMessage;

public interface AiService {

    /**
     * 使用 ai 识别出图片的标签
     *
     * @param id 图片 id
     * @return 标签
     */
    AiPictureMessage getTagsByPicture(Long id) throws Exception;
}
