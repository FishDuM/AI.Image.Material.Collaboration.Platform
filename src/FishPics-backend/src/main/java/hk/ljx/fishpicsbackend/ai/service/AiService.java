package hk.ljx.fishpicsbackend.ai.service;

import hk.ljx.fishpicsbackend.ai.dto.AiDrawPictureDTO;
import hk.ljx.fishpicsbackend.task.entity.Task;

public interface AiService {

    String submitTagTask(Long pictureId);

    Task getTagResult(String taskId);

    String drawPicture(AiDrawPictureDTO drawPictureDTO);
}
