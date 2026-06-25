package hk.ljx.fishpicsbackend.picture.service;

import hk.ljx.fishpicsbackend.picture.vo.ShareFileVO;
import hk.ljx.fishpicsbackend.picture.vo.ShareInfoVO;

import java.util.List;

public interface ShareService {

    String createShare(List<Long> pictureIds, Long userId, int expireDays,
                       int allowDownload, Integer maxViewCount);

    ShareInfoVO getShareInfo(String shareToken);

    ShareFileVO getPreviewFile(String shareToken, Long pictureId);

    ShareFileVO getPreviewFile(String shareToken, Long pictureId, Integer size);

    ShareFileVO getDownloadFile(String shareToken, Long pictureId);

    void cancelShare(Long shareId, Long userId);
}
