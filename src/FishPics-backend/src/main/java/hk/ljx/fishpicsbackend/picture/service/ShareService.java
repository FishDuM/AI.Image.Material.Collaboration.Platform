package hk.ljx.fishpicsbackend.picture.service;

import java.util.List;

import hk.ljx.fishpicsbackend.picture.vo.ShareFileVO;
import hk.ljx.fishpicsbackend.picture.vo.ShareInfoVO;

public interface ShareService {

    // maxViewCount: null/0=默认200, 上限1000
    String createShare(List<Long> pictureIds, Long userId, int expireDays, int allowDownload, Integer maxViewCount);

    ShareInfoVO getShareInfo(String shareToken);

    ShareFileVO getPreviewFile(String shareToken, Long pictureId);

    ShareFileVO getPreviewFile(String shareToken, Long pictureId, Integer size);

    ShareFileVO getDownloadFile(String shareToken, Long pictureId);

    void cancelShare(Long shareId, Long userId);
}
