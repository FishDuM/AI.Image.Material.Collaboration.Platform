package hk.ljx.fishpicsbackend.picture.service;
import hk.ljx.fishpicsbackend.picture.entity.Picture;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import hk.ljx.fishpicsbackend.common.dto.PageRequest;
import hk.ljx.fishpicsbackend.picture.dto.AdminPictureListDTO;
import hk.ljx.fishpicsbackend.picture.dto.CheckUploadRequest;
import hk.ljx.fishpicsbackend.picture.dto.DeleteByIdListRequest;
import hk.ljx.fishpicsbackend.picture.dto.MergeChunksRequest;
import hk.ljx.fishpicsbackend.picture.dto.PictureQueryRequest;
import hk.ljx.fishpicsbackend.picture.dto.PictureUpdateRequest;
import hk.ljx.fishpicsbackend.picture.vo.CheckUploadVO;
import hk.ljx.fishpicsbackend.picture.vo.PictureVO;
import hk.ljx.fishpicsbackend.picture.vo.UploadChunkVO;
import org.springframework.web.multipart.MultipartFile;

public interface PictureService extends IService<Picture> {

    String uploadAvatar(MultipartFile file, Long id);

    Picture uploadPicture(MultipartFile file, Long targetSpaceId);

    Picture savePictureByUrl(String url, Long targetSpaceId);

    IPage<PictureVO> getPictureList(PictureQueryRequest pictureQueryRequest);

    IPage<PictureVO> getAdminPictureList(AdminPictureListDTO dto);

    void reviewPicture(Long pictureId, Integer selected);

    String deletePicture(DeleteByIdListRequest deleteByIdList);

    void updatePicture(PictureUpdateRequest request);

    PictureVO getPictureEditMessage(Long id);

    PictureVO replacePictureFile(Long pictureId, MultipartFile file);

    PictureVO replacePictureFile(Long pictureId, MultipartFile file, boolean requireCollabLock);

    IPage<PictureVO> getRecommendPictures(PageRequest pageRequest, Long userId);

    CheckUploadVO checkUpload(CheckUploadRequest request);

    UploadChunkVO uploadChunk(MultipartFile file, String md5, Integer chunkIndex);

    PictureVO mergeChunks(MergeChunksRequest request);
}
