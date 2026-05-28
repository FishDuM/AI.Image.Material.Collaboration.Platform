package hk.ljx.fishpicsbackend.ai;

import hk.ljx.fishpicsbackend.ai.dto.AiDrawPictureDTO;
import hk.ljx.fishpicsbackend.ai.vo.AiPictureMessage;

public interface AiService {

    /**
     * 使用 ai 识别出图片的标签
     *
     * @param id 图片 id
     * @return 标签
     */
    AiPictureMessage getTagsByPicture(Long id);

    /**
     * ai 文生图
     * @param drawPictureDTO 文生图参数
     * @return 图片链接
     */
    String drawPicture(AiDrawPictureDTO drawPictureDTO);
}
