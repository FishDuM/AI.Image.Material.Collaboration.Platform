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

    // targetSpaceId 为 null 时默认上传到私人空间
    Picture uploadPicture(MultipartFile file, Long targetSpaceId);

    Picture savePictureByUrl(String url, Long targetSpaceId);

    IPage<PictureVO> getPictureList(PictureQueryRequest pictureQueryRequest);

    IPage<PictureVO> getAdminPictureList(AdminPictureListDTO dto);

    // status: 1=通过 0=拒绝
    void reviewPicture(Long pictureId, Integer status, Integer selected);

    String deletePicture(DeleteByIdListRequest deleteByIdList);

    void updatePicture(PictureUpdateRequest request);

    PictureVO getPictureEditMessage(Long id);

    // 协同编辑替换图片：传新文件到 COS，更新记录，删旧文件
    PictureVO replacePictureFile(Long pictureId, MultipartFile file);

    PictureVO replacePictureFile(Long pictureId, MultipartFile file, boolean requireCollabLock);

    IPage<PictureVO> getRecommendPictures(PageRequest pageRequest, Long userId);

    // 秒传校验
    CheckUploadVO checkUpload(CheckUploadRequest request);

    UploadChunkVO uploadChunk(MultipartFile file, String md5, Integer chunkIndex);

    PictureVO mergeChunks(MergeChunksRequest request);
}
